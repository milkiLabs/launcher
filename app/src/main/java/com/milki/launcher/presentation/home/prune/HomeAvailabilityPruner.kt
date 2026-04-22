package com.milki.launcher.presentation.home

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.milki.launcher.core.file.ContentUriFailurePolicy
import com.milki.launcher.core.file.PinnedFileAvailability
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps persisted home items aligned with app/file availability changes.
 */
internal class HomeAvailabilityPruner(
    private val appRepository: AppRepository,
    private val appContext: Context,
    private val homeRepository: HomeRepository,
    private val modelWriter: HomeModelWriter,
    private val mutationMutex: Mutex,
    private val persistUpdatedItems: suspend (currentItems: List<HomeItem>, updatedItems: List<HomeItem>) -> Unit,
    private val scope: CoroutineScope
) {
    @Volatile
    private var started = false

    private data class InstalledAppAvailability(
        val validPackages: Set<String>,
        val validPinnedAppComponents: Set<String>
    )

    private val latestInstalledAvailability = MutableStateFlow<InstalledAppAvailability?>(null)

    private val pruneRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val mediaStoreObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            requestPrune()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            requestPrune()
        }
    }

    fun start() {
        if (started) {
            return
        }
        started = true

        observePruneRequests()
        observeInstalledAvailability()
        registerFileStorageObservers()
    }

    fun stop() {
        runCatching {
            appContext.contentResolver.unregisterContentObserver(mediaStoreObserver)
        }
    }

    private fun observeInstalledAvailability() {
        val installedAvailability = appRepository.observeInstalledApps()
            .filter { installedApps -> installedApps.isNotEmpty() }
            .map(::buildInstalledAppAvailability)

        scope.launch {
            installedAvailability.collectLatest { availability ->
                latestInstalledAvailability.value = availability
                requestPrune()
            }
        }
    }

    private fun observePruneRequests() {
        scope.launch {
            pruneRequests.collectLatest {
                val availability = latestInstalledAvailability.value ?: return@collectLatest
                pruneUnavailableItems(availability = availability)
            }
        }
    }

    private fun requestPrune() {
        pruneRequests.tryEmit(Unit)
    }

    private fun registerFileStorageObservers() {
        registerStorageObserver(MediaStore.Files.getContentUri("external"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerStorageObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
    }

    private fun registerStorageObserver(uri: Uri) {
        runCatching {
            appContext.contentResolver.registerContentObserver(uri, true, mediaStoreObserver)
        }
    }

    private fun buildInstalledAppAvailability(installedApps: List<AppInfo>): InstalledAppAvailability {
        return InstalledAppAvailability(
            validPackages = installedApps.mapTo(mutableSetOf()) { it.packageName },
            validPinnedAppComponents = installedApps.mapTo(mutableSetOf()) {
                ComponentName(it.packageName, it.activityName).flattenToString()
            }
        )
    }

    private suspend fun pruneUnavailableItems(availability: InstalledAppAvailability) {
        mutationMutex.withLock {
            val currentItems = homeRepository.readPinnedItems()
            if (currentItems.isEmpty()) {
                return
            }

            val unavailableItemIds = withContext(Dispatchers.IO) {
                collectUnavailableItemIds(
                    items = currentItems,
                    validPackages = availability.validPackages,
                    validPinnedAppComponents = availability.validPinnedAppComponents
                )
            }

            if (unavailableItemIds.isEmpty()) {
                return
            }

            when (
                val result = modelWriter.apply(
                    currentItems = currentItems,
                    command = HomeModelWriter.Command.RemoveItemsById(itemIds = unavailableItemIds.toSet())
                )
            ) {
                is HomeModelWriter.Result.Applied -> persistUpdatedItems(
                    currentItems,
                    result.items
                )

                is HomeModelWriter.Result.Rejected -> Unit
            }
        }
    }

    private fun collectUnavailableItemIds(
        items: List<HomeItem>,
        validPackages: Set<String>,
        validPinnedAppComponents: Set<String>
    ): List<String> {
        val unavailableIds = linkedSetOf<String>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.PinnedApp -> {
                    val componentName = ComponentName(item.packageName, item.activityName).flattenToString()
                    if (componentName !in validPinnedAppComponents) {
                        unavailableIds += item.id
                    }
                }

                is HomeItem.AppShortcut -> {
                    if (item.packageName !in validPackages) {
                        unavailableIds += item.id
                    }
                }

                is HomeItem.WidgetItem -> {
                    if (item.providerPackage !in validPackages) {
                        unavailableIds += item.id
                    }
                }

                is HomeItem.PinnedFile -> {
                    if (!PinnedFileAvailability.isAvailable(
                            contentResolver = appContext.contentResolver,
                            uriString = item.uri,
                            contentUriFailurePolicy = ContentUriFailurePolicy.TREAT_AS_AVAILABLE
                        )
                    ) {
                        unavailableIds += item.id
                    }
                }

                is HomeItem.FolderItem -> item.children.forEach(::visit)
                is HomeItem.PinnedContact -> Unit
            }
        }

        items.forEach(::visit)
        return unavailableIds.toList()
    }
}
