package com.milki.launcher.presentation.home.prune

import android.content.ComponentName
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.milki.launcher.core.file.ContentUriFailurePolicy
import com.milki.launcher.core.file.PinnedFileAvailability
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.AppRepository
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

/**
 * Keeps persisted home items aligned with app/file availability changes.
 */
class HomeAvailabilityPruner(
    private val appRepository: AppRepository,
    private val contentResolver: ContentResolver
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

    fun start(
        scope: CoroutineScope,
        readItems: suspend () -> List<HomeItem>,
        removeItemsById: suspend (Set<String>) -> Unit
    ) {
        if (started) {
            return
        }
        started = true

        observePruneRequests(
            scope = scope,
            readItems = readItems,
            removeItemsById = removeItemsById
        )
        observeInstalledAvailability(scope)
        registerFileStorageObservers()
    }

    fun stop() {
        runCatching {
            contentResolver.unregisterContentObserver(mediaStoreObserver)
        }
    }

    private fun observeInstalledAvailability(scope: CoroutineScope) {
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

    private fun observePruneRequests(
        scope: CoroutineScope,
        readItems: suspend () -> List<HomeItem>,
        removeItemsById: suspend (Set<String>) -> Unit
    ) {
        scope.launch {
            pruneRequests.collectLatest {
                val availability = latestInstalledAvailability.value ?: return@collectLatest
                pruneUnavailableItems(
                    availability = availability,
                    readItems = readItems,
                    removeItemsById = removeItemsById
                )
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
            contentResolver.registerContentObserver(uri, true, mediaStoreObserver)
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

    private suspend fun pruneUnavailableItems(
        availability: InstalledAppAvailability,
        readItems: suspend () -> List<HomeItem>,
        removeItemsById: suspend (Set<String>) -> Unit
    ) {
        val currentItems = readItems()
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

        if (unavailableItemIds.isNotEmpty()) {
            removeItemsById(unavailableItemIds.toSet())
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

                is HomeItem.ActionShortcut -> {
                    val packageName = item.packageName
                    if (packageName != null && packageName !in validPackages) {
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
                            contentResolver = contentResolver,
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
