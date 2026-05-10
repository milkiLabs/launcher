package com.milki.launcher.data.contextmenu

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import com.milki.launcher.domain.model.HomeItem

/**
 * Process-wide, pre-populated cache for context menu data (shortcuts + widget availability).
 *
 * WHY THIS EXISTS:
 * Context menus need two pieces of data per app:
 * 1. Quick shortcuts published via ShortcutManager (e.g. "New chat", "Compose")
 * 2. Whether the app has any widgets
 *
 * Both require IPC calls (LauncherApps.getShortcuts, AppWidgetManager.installedProviders).
 * Previously, each composable loaded this data on-demand via LaunchedEffect, causing
 * menu items to visibly "jump in" after the popup was already shown.
 *
 * This cache follows the same pattern as AppIconMemoryCache:
 * - Pre-populated at startup and on package changes
 * - Synchronous reads from the UI thread
 * - Thread-safe concurrent access
 *
 * LIFECYCLE:
 * - Populated by AppRepositoryImpl alongside installed app refreshes
 * - Reads are lock-free (volatile snapshot references)
 * - Stale data is acceptable for the brief moment between a package change
 *   and the next refresh — the menu will just show the previous state
 */
object AppContextDataCache {

    /**
     * Snapshot of shortcuts keyed by package name.
     * Replaced atomically on refresh; reads see a consistent snapshot.
     */
    @Volatile
    private var shortcutsSnapshot: Map<String, List<HomeItem.AppShortcut>> = emptyMap()

    /**
     * Set of package names that have at least one widget provider.
     */
    @Volatile
    private var widgetPackagesSnapshot: Set<String> = emptySet()

    /**
     * Maximum number of shortcuts to retain per app.
     */
    private const val MAX_SHORTCUTS_PER_APP = 4

    // ── Synchronous reads ──────────────────────────────────────────────

    /**
     * Returns cached shortcuts for [packageName], or empty list if none.
     * This is a synchronous read — no coroutines, no suspension.
     */
    fun getShortcuts(packageName: String): List<HomeItem.AppShortcut> {
        return shortcutsSnapshot[packageName].orEmpty()
    }

    /**
     * Returns whether [packageName] has any widget providers.
     * This is a synchronous read — no coroutines, no suspension.
     */
    fun hasWidgets(packageName: String): Boolean {
        return packageName in widgetPackagesSnapshot
    }

    // ── Background population ──────────────────────────────────────────

    /**
     * Refreshes the entire cache for all installed packages.
     *
     * MUST be called from a background thread (IO dispatcher).
     * Called by AppRepositoryImpl whenever the installed apps list changes.
     */
    fun refreshAll(context: Context) {
        val newShortcuts = loadAllShortcuts(context)
        val newWidgetPackages = loadWidgetPackages(context)

        // Atomic snapshot replacement — readers see old or new, never partial.
        shortcutsSnapshot = newShortcuts
        widgetPackagesSnapshot = newWidgetPackages
    }

    // ── Internal loading ───────────────────────────────────────────────

    private fun loadAllShortcuts(context: Context): Map<String, List<HomeItem.AppShortcut>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return emptyMap()
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return emptyMap()

        // Get all packages that have shortcuts
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )

        val allShortcuts = runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
        }.getOrElse { emptyList() }

        return allShortcuts
            .asSequence()
            .filter { it.isEnabled }
            .groupBy { it.`package` }
            .mapValues { (_, shortcuts) ->
                shortcuts
                    .sortedWith(
                        compareBy({ !it.isDynamic }, { !it.isDeclaredInManifest }, { it.rank })
                    )
                    .map(HomeItem.AppShortcut::fromShortcutInfo)
                    .distinctBy { it.shortcutId }
                    .take(MAX_SHORTCUTS_PER_APP)
            }
    }

    private fun loadWidgetPackages(context: Context): Set<String> {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        return appWidgetManager.installedProviders
            .mapTo(mutableSetOf()) { it.provider.packageName }
    }
}
