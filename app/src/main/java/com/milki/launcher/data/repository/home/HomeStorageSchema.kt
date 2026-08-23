package com.milki.launcher.data.repository.home

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore schema primitives for home-layout persistence.
 *
 * The [com.milki.launcher.data.repository.common.homeDataStore] instance itself
 * is declared in the central DataStore registry (LauncherDataStores.kt); this
 * file keeps the schema keys.
 */

/**
 * Preference keys used by home-layout persistence.
 */
internal object HomePreferenceKeys {
    /**
     * Newline-separated JSON rows where each row encodes one HomeItem.
     */
    val PINNED_ITEMS = stringPreferencesKey("pinned_items_ordered")
}
