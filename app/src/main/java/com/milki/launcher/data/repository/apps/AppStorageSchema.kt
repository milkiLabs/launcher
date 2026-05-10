package com.milki.launcher.data.repository.apps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore schema for app repository persistence.
 */
internal val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_prefs"
)

internal object AppPreferenceKeys {
    /**
     * Comma-separated list of flattened ComponentName strings.
     */
    val RECENT_APPS = stringPreferencesKey("recent_apps")

    const val MAX_RECENT_APPS = 8
}
