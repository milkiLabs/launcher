package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.ComponentName
import androidx.datastore.preferences.core.edit
import com.milki.launcher.core.util.parseCsv
import com.milki.launcher.core.util.toCsv
import com.milki.launcher.data.repository.RecentListStorage
import com.milki.launcher.domain.model.AppInfo

internal class RecentAppsStore(application: Application) : RecentListStorage<String>(
    dataStore = application.launcherDataStore,
    key = AppPreferenceKeys.RECENT_APPS,
    maxSize = AppPreferenceKeys.MAX_RECENT_APPS,
) {
    override fun encode(item: String): String = item
    override fun decode(raw: String): String? = raw

    suspend fun pruneUnavailable(installedApps: List<AppInfo>) {
        val validComponents = installedApps
            .mapTo(mutableSetOf()) { app ->
                ComponentName(app.packageName, app.activityName).flattenToString()
            }

        dataStore.edit { preferences ->
            val currentRaw = preferences[key] ?: return@edit
            val currentComponents = parseCsv(currentRaw)

            if (currentComponents.isEmpty()) {
                preferences.remove(key)
                return@edit
            }

            val filtered = linkedSetOf<String>()
            currentComponents.forEach { component ->
                if (component in validComponents) {
                    filtered += component
                }
            }

            val normalizedRaw = filtered
                .take(maxSize)
                .toCsv()

            if (normalizedRaw == currentRaw) {
                return@edit
            }

            if (normalizedRaw.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = normalizedRaw
            }
        }
    }
}
