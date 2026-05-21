package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMutationStorePrefixConflictTest {

    private val store = SettingsMutationStore()

    @Test
    fun add_prefix_to_provider_succeeds_when_no_conflict() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.addPrefix(prefs, ProviderId.CONTACTS, "cx")

        assertEquals(PrefixMutationResult.Success, result)
    }

    @Test
    fun add_prefix_rejects_conflict_with_provider_default() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.addPrefix(prefs, ProviderId.CONTACTS, "f")

        assertEquals(PrefixMutationResult.DuplicatePrefixOnAnotherOwner("unknown"), result)
    }

    @Test
    fun add_prefix_rejects_conflict_with_source_prefix() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.addPrefix(prefs, ProviderId.CONTACTS, "k")

        assertEquals(PrefixMutationResult.DuplicatePrefixOnAnotherOwner("unknown"), result)
    }

    @Test
    fun add_prefix_rejects_unknown_provider() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.addPrefix(prefs, "unknown_provider", "x")

        assertEquals(PrefixMutationResult.TargetNotFound, result)
    }

    @Test
    fun add_prefix_rejects_empty_prefix() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.addPrefix(prefs, ProviderId.CONTACTS, "  ")

        assertEquals(PrefixMutationResult.InvalidPrefixEmpty, result)
    }

    @Test
    fun add_prefix_rejects_prefix_with_spaces() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.addPrefix(prefs, ProviderId.CONTACTS, "ab c")

        assertEquals(PrefixMutationResult.InvalidPrefixContainsSpaces, result)
    }

    @Test
    fun add_prefix_rejects_duplicate_on_same_provider() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        store.addPrefix(prefs, ProviderId.CONTACTS, "cx")
        val result = store.addPrefix(prefs, ProviderId.CONTACTS, "cx")

        assertEquals(PrefixMutationResult.PrefixAlreadyExistsOnTarget, result)
    }

    @Test
    fun remove_prefix_from_provider_succeeds() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        store.addPrefix(prefs, ProviderId.CONTACTS, "cx")
        val result = store.removePrefix(prefs, ProviderId.CONTACTS, "cx")

        assertEquals(PrefixMutationResult.Success, result)
    }

    @Test
    fun remove_prefix_returns_not_found_when_missing() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        val result = store.removePrefix(prefs, ProviderId.CONTACTS, "nonexistent")

        assertEquals(PrefixMutationResult.PrefixNotFoundOnTarget, result)
    }

    @Test
    fun reset_prefixes_clears_provider_customization() {
        val prefs = mutablePreferencesOf()
        seedDefaultSources(prefs)

        store.addPrefix(prefs, ProviderId.CONTACTS, "cx")
        store.resetPrefixes(prefs, ProviderId.CONTACTS)

        val prefs2 = mutablePreferencesOf()
        seedDefaultSources(prefs2)
        val result = store.addPrefix(prefs2, ProviderId.CONTACTS, "cx")

        assertEquals(PrefixMutationResult.Success, result)
    }

    private fun seedDefaultSources(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        val sources = listOf(
            SearchSource(
                id = "source_duckduckgo",
                name = "DuckDuckGo",
                urlTemplate = "https://duckduckgo.com/?q={query}",
                prefixes = listOf("k"),
                isEnabled = true,
                accentColorHex = "#DE5833"
            )
        )
        prefs[SettingsPreferenceKeys.SEARCH_SOURCES] =
            kotlinx.serialization.json.Json.encodeToString(sources)
        prefs[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] = SearchSourcesStorageState.INITIALIZED
    }
}
