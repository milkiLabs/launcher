package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.ComponentName
import androidx.datastore.preferences.core.edit
import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Encapsulates recent-app persistence.
 */
internal class RecentAppsStore(
    private val application: Application
) {

    private val dataStore = application.launcherDataStore

    fun observeRecentComponentNames(): Flow<List<String>> {
        return dataStore.data
            .map { preferences ->
                val raw = preferences[AppPreferenceKeys.RECENT_APPS] ?: return@map emptyList()
                raw
                    .split(",")
                    .filter { value -> value.isNotEmpty() }
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
}
