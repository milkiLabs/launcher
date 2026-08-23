package com.milki.launcher.presentation.home

import android.content.pm.PackageManager
import com.milki.launcher.data.icon.ActionShortcutPackageResolver
import com.milki.launcher.domain.icon.IconPreloader
import com.milki.launcher.domain.icon.IconPriorityStore
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps icon cache warm for packages visible on the home surface.
 */
class HomeIconWarmupCoordinator(
    private val homeRepository: HomeRepository,
    private val priorityStore: IconPriorityStore,
    private val iconPreloader: IconPreloader,
    private val packageManager: PackageManager
) {
    @Volatile
    private var started = false

    private data class VisibleHomeIcons(
        val packageNames: Set<String>,
        val shortcuts: List<HomeItem.AppShortcut>,
        val unresolvedActionShortcuts: List<HomeItem.ActionShortcut>
    )

    fun start(scope: CoroutineScope) {
        if (started) {
            return
        }
        started = true

        scope.launch(Dispatchers.IO) {
            homeRepository.pinnedItems
                .map(::collectVisibleHomeIcons)
                .distinctUntilChanged()
                .collectLatest { visibleIcons ->
                    priorityStore.updateHomePriorityPackages(visibleIcons.packageNames)

                    if (visibleIcons.packageNames.isNotEmpty()) {
                        iconPreloader.preloadMissingAppIcons(visibleIcons.packageNames)
                    }

                    if (visibleIcons.shortcuts.isNotEmpty()) {
                        iconPreloader.preloadMissingShortcutIcons(visibleIcons.shortcuts)
                    }

                    // Pre-resolve action shortcut handler packages so the first
                    // composition of ActionShortcutIcon hits the cache instead of
                    // flashing the generic link placeholder.
                    visibleIcons.unresolvedActionShortcuts.forEach { shortcut ->
                        ActionShortcutPackageResolver.getOrLoad(packageManager, shortcut.destinationUri)
                    }
                }
        }
    }

    private fun collectVisibleHomeIcons(items: List<HomeItem>): VisibleHomeIcons {
        val packageNames = linkedSetOf<String>()
        val shortcuts = mutableListOf<HomeItem.AppShortcut>()
        val unresolvedActionShortcuts = mutableListOf<HomeItem.ActionShortcut>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.PinnedApp -> packageNames += item.packageName
                is HomeItem.AppShortcut -> {
                    packageNames += item.packageName
                    shortcuts += item
                }
                is HomeItem.ActionShortcut -> {
                    val packageName = item.packageName
                    if (packageName != null) {
                        packageNames += packageName
                    } else {
                        unresolvedActionShortcuts += item
                    }
                }
                is HomeItem.WidgetItem -> packageNames += item.providerPackage
                is HomeItem.FolderItem -> item.children.forEach(::visit)
                is HomeItem.PinnedContact,
                is HomeItem.PinnedFile -> Unit
            }
        }

        items.forEach(::visit)
        return VisibleHomeIcons(
            packageNames = packageNames,
            shortcuts = shortcuts,
            unresolvedActionShortcuts = unresolvedActionShortcuts.distinctBy { it.destinationUri }
        )
    }
}
