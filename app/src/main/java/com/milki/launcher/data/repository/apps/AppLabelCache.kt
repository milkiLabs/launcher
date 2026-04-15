package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.Context
import com.milki.launcher.domain.model.AppInfo

/**
 * SharedPreferences-backed cache for app display labels.
 *
 * WHY THIS EXISTS:
 * On cold start, resolveInfo.loadLabel(PackageManager) does IPC per app.
 * With a cold PM cache this costs ~24ms per app (~673ms for 28 apps).
 * By caching labels to SharedPreferences, subsequent cold starts read
 * labels in ~5ms total, cutting TTID from ~694ms to ~80ms.
 *
 * STALENESS POLICY:
 * The cache is only used for the very first load after process creation.
 * All subsequent loads (triggered by PackageChangeMonitor broadcasts)
 * resolve labels fresh from PackageManager (which is fast because the
 * PM process is already warm). The cache is then updated with the fresh
 * values so the next cold start benefits too.
 */
internal class AppLabelCache(application: Application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Reads all cached labels. Returns a map of "package/activity" → label.
     */
    fun readAll(): Map<String, String> {
        return buildMap {
            for ((key, value) in prefs.all) {
                if (value is String) put(key, value)
            }
        }
    }

    /**
     * Persists the full label set, replacing any previous cache.
     */
    fun writeAll(apps: List<AppInfo>) {
        prefs.edit().apply {
            clear()
            apps.forEach { app ->
                putString(cacheKey(app.packageName, app.activityName), app.name)
            }
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "app_label_cache"

        fun cacheKey(packageName: String, activityName: String): String {
            return "$packageName/$activityName"
        }
    }
}
