package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.core.intent.createLauncherActivityIntent
import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Encapsulates recent-app persistence and mapping logic.
 */
internal class RecentAppsStore(
    private val application: Application
) {

    private val dataStore = application.launcherDataStore

    fun observeRecentApps(): Flow<List<AppInfo>> {
        return dataStore.data
            .map { preferences ->
                val raw = preferences[AppPreferenceKeys.RECENT_APPS] ?: return@map emptyList()
                val recentComponentNames = raw
                    .split(",")
                    .filter { value -> value.isNotEmpty() }

                recentComponentNames.mapNotNull { flattenedComponentName ->
                    toAppInfo(flattenedComponentName)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun saveRecentApp(componentName: String) {
        dataStore.edit { preferences ->
            val currentRaw = preferences[AppPreferenceKeys.RECENT_APPS] ?: ""
            val components = currentRaw
                .split(",")
                .filter { value -> value.isNotEmpty() }
                .toMutableList()

            components.remove(componentName)
            components.add(0, componentName)

            preferences[AppPreferenceKeys.RECENT_APPS] = components
                .take(AppPreferenceKeys.MAX_RECENT_APPS)
                .joinToString(separator = ",")
        }
    }

    suspend fun pruneUnavailable(installedApps: List<AppInfo>) {
        val validComponents = installedApps
            .mapTo(mutableSetOf()) { app ->
                ComponentName(app.packageName, app.activityName).flattenToString()
            }

        dataStore.edit { preferences ->
            val currentRaw = preferences[AppPreferenceKeys.RECENT_APPS] ?: return@edit
            val currentComponents = currentRaw
                .split(",")
                .filter { value -> value.isNotEmpty() }

            if (currentComponents.isEmpty()) {
                preferences.remove(AppPreferenceKeys.RECENT_APPS)
                return@edit
            }

            val filtered = linkedSetOf<String>()
            currentComponents.forEach { component ->
                if (component in validComponents) {
                    filtered += component
                }
            }

            val normalizedRaw = filtered
                .take(AppPreferenceKeys.MAX_RECENT_APPS)
                .joinToString(separator = ",")

            if (normalizedRaw == currentRaw) {
                return@edit
            }

            if (normalizedRaw.isEmpty()) {
                preferences.remove(AppPreferenceKeys.RECENT_APPS)
            } else {
                preferences[AppPreferenceKeys.RECENT_APPS] = normalizedRaw
            }
        }
    }

    private fun toAppInfo(flattenedComponentName: String): AppInfo? {
        val componentName = ComponentName.unflattenFromString(flattenedComponentName)
            ?: return null

        val packageManager = application.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfoCompat(componentName.packageName)

            AppIconMemoryCache.preload(
                packageName = componentName.packageName,
                icon = packageManager.getApplicationIcon(componentName.packageName)
            )

            AppInfo(
                name = packageManager.getApplicationLabel(applicationInfo).toString(),
                packageName = componentName.packageName,
                activityName = componentName.className,
                launchIntent = createLauncherActivityIntent(componentName)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
