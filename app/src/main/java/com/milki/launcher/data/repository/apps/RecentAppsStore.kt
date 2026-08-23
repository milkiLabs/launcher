package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.ComponentName
import androidx.datastore.preferences.core.edit
import com.milki.launcher.data.repository.common.RecentListStorage
import com.milki.launcher.data.repository.common.launcherDataStore
import com.milki.launcher.domain.model.AppInfo

internal class RecentAppsStore(application: Application) : RecentListStorage<String>(
    dataStore = application.launcherDataStore,
    key = AppPreferenceKeys.RECENT_APPS,
    maxSize = AppPreferenceKeys.MAX_RECENT_APPS,
    encoder = { component -> component },
    decoder = { raw -> raw },
) {
    suspend fun pruneUnavailable(installedApps: List<AppInfo>) {
        val validComponents = installedApps
            .mapTo(mutableSetOf()) { app ->
                ComponentName(app.packageName, app.activityName).flattenToString()
            }

        dataStore.edit { preferences ->
            val raw = preferences[key] ?: return@edit
            val currentComponents = readItems(raw)

            if (currentComponents.isEmpty()) {
                preferences.remove(key)
                return@edit
            }

            val filtered = currentComponents
                .filterTo(linkedSetOf()) { it in validComponents }
            val normalizedRaw = writeItems(filtered.toList())

            if (normalizedRaw == raw) {
                return@edit
            }

            if (filtered.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = normalizedRaw
            }
        }
    }
}
