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
            prefix = "g",
            defaultPrefix = "c"
        )

        assertEquals(
            PrefixMutationResult.DuplicatePrefixOnAnotherOwner(ownerId = "source_google"),
            result
        )
        assertTrue(storedPrefixConfigurations(preferences).isEmpty())
    }

    @Test
    fun add_prefix_to_source_rejects_conflict_with_local_provider_default() {
        val preferences = mutablePreferencesOf()

        val result = store.addPrefixToSource(
            preferences = preferences,
            sourceId = "source_google",
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
            sourceId = "source_google",
            name = "Google",
            urlTemplate = "https://www.google.com/search?q={query}",
            prefixes = listOf("f"),
            accentColorHex = "#4285F4"
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

    private fun storedPrefixConfigurations(preferences: MutablePreferences): Map<String, List<String>> {
        val json = preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] ?: return emptyMap()
        return settingsStorageJson.decodeFromString(json)
    }
}
