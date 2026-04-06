package com.milki.launcher.presentation.home

import android.content.Context
import android.content.pm.PackageManager
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.data.icon.ShortcutIconLoader
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
internal class HomeIconWarmupCoordinator(
    private val homeRepository: HomeRepository,
    private val appContext: Context,
    private val packageManager: PackageManager,
    private val scope: CoroutineScope
) {

    private data class VisibleHomeIcons(
        val packageNames: Set<String>,
        val shortcuts: List<HomeItem.AppShortcut>
    )

    fun start() {
        scope.launch(Dispatchers.IO) {
            homeRepository.pinnedItems
                .map(::collectVisibleHomeIcons)
                .distinctUntilChanged()
                .collectLatest { visibleIcons ->
                    AppIconMemoryCache.updateHomePriorityPackages(visibleIcons.packageNames)

                    if (visibleIcons.packageNames.isNotEmpty()) {
                        AppIconMemoryCache.preloadMissing(
                            packageNames = visibleIcons.packageNames,
                            packageManager = packageManager
                        )
                    }

                    if (visibleIcons.shortcuts.isNotEmpty()) {
                        ShortcutIconLoader.preloadMissing(
                            context = appContext,
                            shortcuts = visibleIcons.shortcuts
                        )
                    }
                }
        }
    }

    private fun collectVisibleHomeIcons(items: List<HomeItem>): VisibleHomeIcons {
        val packageNames = linkedSetOf<String>()
        val shortcuts = mutableListOf<HomeItem.AppShortcut>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.PinnedApp -> packageNames += item.packageName
                is HomeItem.AppShortcut -> {
                    packageNames += item.packageName
                    shortcuts += item
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
            shortcuts = shortcuts
        )
    }
}
