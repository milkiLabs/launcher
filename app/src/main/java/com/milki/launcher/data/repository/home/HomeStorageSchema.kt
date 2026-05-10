package com.milki.launcher.data.repository.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.HomeItem
import kotlinx.serialization.json.Json

/**
 * DataStore schema primitives for home-layout persistence.
 *
 * Keeping storage keys and DataStore wiring in one file avoids scattering
 * schema details across mutation and repository classes.
 */
internal val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "home_items"
)

/**
 * Preference keys used by home-layout persistence.
 */
internal object HomePreferenceKeys {
    /**
     * Newline-separated JSON rows where each row encodes one HomeItem.
     */
    val PINNED_ITEMS = stringPreferencesKey("pinned_items_ordered")
}

/**
 * Shared Json instance for HomeItem serialization.
 *
 * This intentionally reuses HomeItem.json so polymorphic behavior stays
 * identical across all home-model write paths.
 */
internal val homeStorageJson: Json = HomeItem.json
