package com.milki.launcher.data.icon

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import com.milki.launcher.domain.model.HomeItem

/**
 * Shared shortcut-icon loading path used by home cells, folder previews, and
 * warmup flows so they all resolve the same artwork consistently.
 */
object ShortcutIconLoader {

    fun cacheKey(shortcut: HomeItem.AppShortcut): String {
        return "${shortcut.packageName}/${shortcut.shortcutId}"
    }

    fun getCached(shortcut: HomeItem.AppShortcut): Drawable? {
        return ShortcutIconMemoryCache.get(cacheKey(shortcut))
    }

    fun getOrLoad(
        context: Context,
        shortcut: HomeItem.AppShortcut
    ): Drawable? {
        val key = cacheKey(shortcut)
        return ShortcutIconMemoryCache.getOrLoad(key) {
            load(context, shortcut)
        }
    }

    fun preloadMissing(
        context: Context,
        shortcuts: Collection<HomeItem.AppShortcut>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }

        shortcuts
            .distinctBy(::cacheKey)
            .forEach { shortcut ->
                val key = cacheKey(shortcut)
                if (ShortcutIconMemoryCache.get(key) == null) {
                    load(context, shortcut)?.let { drawable ->
                        ShortcutIconMemoryCache.preload(key, drawable)
                    }
                }
            }
    }

    private fun load(
        context: Context,
        shortcut: HomeItem.AppShortcut
    ): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return null
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(shortcut.packageName)
            .setShortcutIds(listOf(shortcut.shortcutId))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
            )

        val shortcutInfo = runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle())
        }.getOrNull()?.firstOrNull() ?: return null

        return launcherApps.getShortcutIconDrawable(
            shortcutInfo,
            context.resources.displayMetrics.densityDpi
        )
    }
}
