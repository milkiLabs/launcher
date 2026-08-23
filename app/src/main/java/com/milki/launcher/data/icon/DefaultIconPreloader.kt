package com.milki.launcher.data.icon

import android.content.Context
import com.milki.launcher.domain.icon.IconPreloader
import com.milki.launcher.domain.model.HomeItem

/**
 * Default [IconPreloader] backed by the process-wide icon cache singletons.
 *
 * Keeps the Android context and PackageManager access out of the warmup
 * coordinator, which only sees the port.
 */
class DefaultIconPreloader(
    private val appContext: Context
) : IconPreloader {

    override fun preloadMissingAppIcons(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return

        AppIconMemoryCache.preloadMissing(
            packageNames = packageNames,
            packageManager = appContext.packageManager
        )
    }

    override fun preloadMissingShortcutIcons(shortcuts: List<HomeItem.AppShortcut>) {
        if (shortcuts.isEmpty()) return

        ShortcutIconLoader.preloadMissing(
            context = appContext,
            shortcuts = shortcuts
        )
    }
}
