package com.milki.launcher.data.repository.apps

import androidx.datastore.preferences.core.stringPreferencesKey
import com.milki.launcher.domain.homegraph.HomeGridDefaults

/**
 * DataStore schema for app repository persistence.
 *
 * The [com.milki.launcher.data.repository.common.launcherDataStore] instance
 * itself is declared in the central DataStore registry (LauncherDataStores.kt).
 */
internal object AppPreferenceKeys {
    /**
     * Comma-separated list of flattened ComponentName strings.
     */
    val RECENT_APPS = stringPreferencesKey("recent_apps")

    const val MAX_RECENT_APPS = HomeGridDefaults.COLUMNS * 2
}
