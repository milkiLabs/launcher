package com.milki.launcher.presentation.home

import android.content.pm.PackageManager
import com.milki.launcher.data.icon.AppIconMemoryCache
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
    private val packageManager: PackageManager,
    private val scope: CoroutineScope
) {

    fun start() {
        scope.launch(Dispatchers.IO) {
            homeRepository.pinnedItems
                .map(::collectHomeVisibleIconPackages)
                .distinctUntilChanged()
                .collectLatest { packageNames ->
                    AppIconMemoryCache.updateHomePriorityPackages(packageNames)
                    if (packageNames.isEmpty()) return@collectLatest

                    AppIconMemoryCache.preloadMissing(
                        packageNames = packageNames,
                        packageManager = packageManager
                    )
                }
        }
    }

    private fun collectHomeVisibleIconPackages(items: List<HomeItem>): Set<String> {
        if (items.isEmpty()) return emptySet()

        val packageNames = linkedSetOf<String>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.PinnedApp -> packageNames += item.packageName
                is HomeItem.AppShortcut -> packageNames += item.packageName
                is HomeItem.WidgetItem -> packageNames += item.providerPackage
                is HomeItem.FolderItem -> item.children.forEach(::visit)
                is HomeItem.PinnedContact,
                is HomeItem.PinnedFile -> Unit
            }
        }

        items.forEach(::visit)
        return packageNames
    }
}
