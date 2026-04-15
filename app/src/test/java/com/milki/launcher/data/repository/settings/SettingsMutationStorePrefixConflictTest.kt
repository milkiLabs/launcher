package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchSource
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMutationStorePrefixConflictTest {

    private val store = SettingsMutationStore()

    @Test
    fun add_provider_prefix_rejects_conflict_with_custom_source_prefix() {
        val preferences = mutablePreferencesOf()

        val result = store.addProviderPrefix(
            preferences = preferences,
            providerId = ProviderId.CONTACTS,
            prefix = "d",
            defaultPrefix = "c"
        )

        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = "source_duckduckgo"),
            result
        )
        assertTrue(storedPrefixConfigurations(preferences).isEmpty())
    }

    @Test
    fun add_prefix_to_source_rejects_conflict_with_local_provider_default() {
        val preferences = mutablePreferencesOf()

        val result = store.addPrefixToSource(
            preferences = preferences,
            sourceId = "source_duckduckgo",
            prefix = "c"
        )

        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = ProviderId.CONTACTS),
            result
        )
    }

    @Test
    fun add_provider_prefix_rejects_conflict_between_local_providers() {
        val preferences = mutablePreferencesOf()

        val firstResult = store.addProviderPrefix(
            preferences = preferences,
            providerId = ProviderId.CONTACTS,
            prefix = "contactsx",
            defaultPrefix = "c"
        )
        val secondResult = store.addProviderPrefix(
            preferences = preferences,
            providerId = ProviderId.FILES,
            prefix = "contactsx",
            defaultPrefix = "f"
        )

        assertEquals(PrefixMutationResult.Success, firstResult)
        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = ProviderId.CONTACTS),
            secondResult
        )
    }

    @Test
    fun update_search_source_rejects_conflict_with_local_provider_prefix() {
        val preferences = mutablePreferencesOf()

        val result = store.updateSearchSource(
            preferences = preferences,
            sourceId = "source_duckduckgo",
            name = "DuckDuckGo",
            urlTemplate = "https://duckduckgo.com/?q={query}",
            prefixes = listOf("f"),
            accentColorHex = "#DE5833"
        )

        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = ProviderId.FILES),
            result
        )
    }

    @Test
    fun add_search_source_rejects_conflict_with_existing_source_prefix() {
        val preferences = mutablePreferencesOf()

        val result = store.addSearchSource(
            preferences = preferences,
            source = SearchSource(
                id = "source_reddit",
                name = "Reddit",
                urlTemplate = "https://www.reddit.com/search/?q={query}",
                prefixes = listOf("ig"),
                isEnabled = true,
                accentColorHex = "#FF4500"
            )
        )

        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = "source_instagram"),
            result
        )
    }

    @Test
    fun add_provider_prefix_allows_non_conflicting_prefix() {
        val preferences = mutablePreferencesOf()

        val result = store.addProviderPrefix(
            preferences = preferences,
            providerId = ProviderId.CONTACTS,
            prefix = "contactsx",
            defaultPrefix = "c"
        )

        assertEquals(PrefixMutationResult.Success, result)
        assertEquals(
            listOf("c", "contactsx"),
            storedPrefixConfigurations(preferences)[ProviderId.CONTACTS]
        )
    }

    @Test
    fun set_provider_prefixes_rejects_conflict_with_custom_source_prefix() {
        val preferences = mutablePreferencesOf()

        val result = store.setProviderPrefixes(
            preferences = preferences,
            providerId = ProviderId.FILES,
            prefixes = listOf("d")
        )

        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = "source_duckduckgo"),
            result
        )
        assertTrue(storedPrefixConfigurations(preferences).isEmpty())
    }

    @Test
    fun set_provider_prefixes_returns_target_not_found_for_unknown_provider() {
        val preferences = mutablePreferencesOf()

        val result = store.setProviderPrefixes(
            preferences = preferences,
            providerId = "unknown_provider",
            prefixes = listOf("u")
        )

        assertEquals(PrefixMutationResult.TargetNotFound, result)
    }

    private fun storedPrefixConfigurations(preferences: MutablePreferences): Map<String, List<String>> {
        val json = preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] ?: return emptyMap()
        return settingsStorageJson.decodeFromString(json)
    }
}
