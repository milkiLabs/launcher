package com.milki.launcher.data.icon

import android.content.Context
import com.milki.launcher.domain.icon.IconPreloader
import com.milki.launcher.domain.model.HomeItem

/**
 * Default [IconPreloader] backed by the DI-managed icon memory cache.
 *
 * Keeps the Android context and PackageManager access out of the warmup
 * coordinator, which only sees the port.
 */
class DefaultIconPreloader(
    private val appContext: Context,
    private val appIconMemoryCache: AppIconMemoryCache
) : IconPreloader {

    override suspend fun preloadMissingAppIcons(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return

        appIconMemoryCache.preloadMissing(
            packageNames = packageNames,
            packageManager = appContext.packageManager
        )
    }

    override suspend fun preloadMissingShortcutIcons(shortcuts: List<HomeItem.AppShortcut>) {
        if (shortcuts.isEmpty()) return

        ShortcutIconLoader.preloadMissing(
            context = appContext,
            shortcuts = shortcuts
        )
    }
}
