package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource

/**
 * Shared write helpers for persisting settings to DataStore preferences.
 *
 * WHY THIS EXISTS:
 * Both SettingsMutationStore and SettingsPreferenceWriter needed identical
 * writePrefixConfigurations/writeSearchSources functions. This file is the
 * single source of truth for those persistence operations.
 */

internal fun MutablePreferences.writePrefixConfigurations(configurations: ProviderPrefixConfiguration) {
    if (configurations.isEmpty()) {
        remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
        return
    }
    this[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] =
        serializePrefixConfigurations(configurations)
}

internal fun MutablePreferences.writeSearchSources(sources: List<SearchSource>) {
    this[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] =
        SearchSourcesStorageState.INITIALIZED
    this[SettingsPreferenceKeys.SEARCH_SOURCES] =
        serializeSearchSources(sources)
}
