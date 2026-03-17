package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.SourcePrefixMutationResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Dedicated store for settings mutation logic that operates on a mutable
 * DataStore preferences snapshot.
 *
 * WHY THIS FILE EXISTS:
 * - Keeps mutation-heavy logic out of SettingsRepositoryImpl
 * - Centralizes source/prefix read-modify-write behavior
 * - Makes persistence rules easier to audit and test in isolation
 *
 * SCOPE:
 * This store intentionally focuses on:
 * 1. Search-source mutations
 * 2. Provider-prefix mutations
 * 3. Source-prefix validation and transactional updates
 *
 * It does NOT expose repository concerns like Flow mapping or generic
 * LauncherSettings snapshot transforms.
 */
internal class SettingsMutationStore {

    fun addSearchSource(
        preferences: MutablePreferences,
        source: SearchSource
    ) {
        val currentSources = parseSearchSources(preferences)
        writeSearchSources(currentSources + source, preferences)
    }

    fun updateSearchSource(
        preferences: MutablePreferences,
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ) {
        val currentSources = parseSearchSources(preferences)

        if (currentSources.none { it.id == sourceId }) {
            return
        }

        val updatedSources = currentSources.map { existing ->
            if (existing.id == sourceId) {
                existing.copy(
                    name = name,
                    urlTemplate = urlTemplate,
                    prefixes = prefixes,
                    accentColorHex = accentColorHex
                )
            } else {
                existing
            }
        }

        writeSearchSources(updatedSources, preferences)
    }

    fun deleteSearchSource(
        preferences: MutablePreferences,
        sourceId: String
    ) {
        val currentSources = parseSearchSources(preferences)
        val updatedSources = currentSources.filterNot { it.id == sourceId }

        if (updatedSources == currentSources) {
            return
        }

        writeSearchSources(updatedSources, preferences)
    }

    fun setSearchSourceEnabled(
        preferences: MutablePreferences,
        sourceId: String,
        enabled: Boolean
    ) {
        val currentSources = parseSearchSources(preferences)

        if (currentSources.none { it.id == sourceId }) {
            return
        }

        val updatedSources = currentSources.map { source ->
            if (source.id == sourceId) {
                source.copy(isEnabled = enabled)
            } else {
                source
            }
        }

        writeSearchSources(updatedSources, preferences)
    }

    fun setProviderPrefixes(
        preferences: MutablePreferences,
        providerId: String,
        prefixes: List<String>
    ) {
        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val updatedConfigurations = currentConfigurations.toMutableMap()

        if (prefixes.isNotEmpty()) {
            updatedConfigurations[providerId] = PrefixConfig(prefixes)
        } else {
            updatedConfigurations.remove(providerId)
        }

        writePrefixConfigurations(updatedConfigurations, preferences)
    }

    fun addProviderPrefix(
        preferences: MutablePreferences,
        providerId: String,
        prefix: String,
        defaultPrefix: String
    ) {
        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val currentPrefixes = currentConfigurations[providerId]?.prefixes ?: listOf(defaultPrefix)

        if (prefix in currentPrefixes) {
            return
        }

        val updatedConfigurations = currentConfigurations.toMutableMap()
        updatedConfigurations[providerId] = PrefixConfig(currentPrefixes + prefix)
        writePrefixConfigurations(updatedConfigurations, preferences)
    }

    fun removeProviderPrefix(
        preferences: MutablePreferences,
        providerId: String,
        prefix: String
    ) {
        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val currentPrefixes = currentConfigurations[providerId]?.prefixes ?: return

        val updatedPrefixes = currentPrefixes - prefix
        val updatedConfigurations = currentConfigurations.toMutableMap()

        if (updatedPrefixes.isNotEmpty()) {
            updatedConfigurations[providerId] = PrefixConfig(updatedPrefixes)
        } else {
            updatedConfigurations.remove(providerId)
        }

        writePrefixConfigurations(updatedConfigurations, preferences)
    }

    fun resetProviderPrefixes(
        preferences: MutablePreferences,
        providerId: String
    ) {
        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])

        if (providerId !in currentConfigurations) {
            return
        }

        val updatedConfigurations = currentConfigurations.toMutableMap()
        updatedConfigurations.remove(providerId)
        writePrefixConfigurations(updatedConfigurations, preferences)
    }

    fun resetAllPrefixConfigurations(preferences: MutablePreferences) {
        preferences.remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
    }

    fun setAllPrefixConfigurations(
        preferences: MutablePreferences,
        configurations: ProviderPrefixConfiguration
    ) {
        writePrefixConfigurations(configurations, preferences)
    }

    fun addPrefixToSource(
        preferences: MutablePreferences,
        sourceId: String,
        prefix: String
    ): SourcePrefixMutationResult {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        if (normalizedPrefix.isEmpty()) {
            return SourcePrefixMutationResult.InvalidPrefixEmpty
        }

        if (normalizedPrefix.contains(" ")) {
            return SourcePrefixMutationResult.InvalidPrefixContainsSpaces
        }

        val currentSources = parseSearchSources(preferences)
        val targetSource = currentSources.firstOrNull { it.id == sourceId }
            ?: return SourcePrefixMutationResult.SourceNotFound

        val normalizedTargetPrefixes = targetSource.prefixes
            .map(SearchSource.Companion::normalizePrefix)

        if (normalizedPrefix in normalizedTargetPrefixes) {
            return SourcePrefixMutationResult.PrefixAlreadyExistsOnTargetSource
        }

        val conflictingSource = currentSources
            .asSequence()
            .filter { it.id != sourceId }
            .firstOrNull { source ->
                source.prefixes
                    .map(SearchSource.Companion::normalizePrefix)
                    .contains(normalizedPrefix)
            }

        if (conflictingSource != null) {
            return SourcePrefixMutationResult.DuplicatePrefixOnAnotherSource(
                ownerSourceId = conflictingSource.id
            )
        }

        val updatedSources = currentSources.map { source ->
            if (source.id == sourceId) {
                source.copy(
                    prefixes = (source.prefixes + normalizedPrefix)
                        .map(SearchSource.Companion::normalizePrefix)
                        .filter { it.isNotBlank() && !it.contains(" ") }
                        .distinct()
                )
            } else {
                source
            }
        }

        writeSearchSources(updatedSources, preferences)
        return SourcePrefixMutationResult.Success
    }

    fun removePrefixFromSource(
        preferences: MutablePreferences,
        sourceId: String,
        prefix: String
    ): SourcePrefixMutationResult {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        if (normalizedPrefix.isEmpty()) {
            return SourcePrefixMutationResult.InvalidPrefixEmpty
        }

        val currentSources = parseSearchSources(preferences)
        val targetSource = currentSources.firstOrNull { it.id == sourceId }
            ?: return SourcePrefixMutationResult.SourceNotFound

        val normalizedTargetPrefixes = targetSource.prefixes
            .map(SearchSource.Companion::normalizePrefix)

        if (normalizedPrefix !in normalizedTargetPrefixes) {
            return SourcePrefixMutationResult.PrefixNotFoundOnTargetSource
        }

        val updatedSources = currentSources.map { source ->
            if (source.id == sourceId) {
                source.copy(
                    prefixes = source.prefixes.filterNot {
                        SearchSource.normalizePrefix(it) == normalizedPrefix
                    }
                )
            } else {
                source
            }
        }

        writeSearchSources(updatedSources, preferences)
        return SourcePrefixMutationResult.Success
    }

    private fun writePrefixConfigurations(
        configurations: ProviderPrefixConfiguration,
        preferences: MutablePreferences
    ) {
        if (configurations.isEmpty()) {
            preferences.remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
            return
        }

        preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] =
            serializePrefixConfigurations(configurations)
    }

    private fun writeSearchSources(
        sources: List<SearchSource>,
        preferences: MutablePreferences
    ) {
        preferences[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] =
            SearchSourcesStorageState.INITIALIZED
        preferences[SettingsPreferenceKeys.SEARCH_SOURCES] =
            serializeSearchSources(sources)
    }

    private fun parsePrefixConfigurations(json: String?): ProviderPrefixConfiguration {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val serializedConfiguration: SerializedPrefixConfiguration =
                settingsStorageJson.decodeFromString(json)

            serializedConfiguration
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, prefixes) -> PrefixConfig(prefixes) }
        }.getOrElse {
            emptyMap()
        }
    }

    private fun serializePrefixConfigurations(
        config: ProviderPrefixConfiguration
    ): String {
        if (config.isEmpty()) {
            return "{}"
        }

        val serializedConfiguration: SerializedPrefixConfiguration =
            config.mapValues { (_, prefixConfig) -> prefixConfig.prefixes }

        return settingsStorageJson.encodeToString(serializedConfiguration)
    }

    private fun parseSearchSources(preferences: MutablePreferences): List<SearchSource> {
        val json = preferences[SettingsPreferenceKeys.SEARCH_SOURCES]
        val isInitialized =
            preferences[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] ==
                SearchSourcesStorageState.INITIALIZED

        return parseSearchSources(
            json = json,
            isInitialized = isInitialized
        )
    }

    private fun parseSearchSources(
        json: String?,
        isInitialized: Boolean
    ): List<SearchSource> {
        if (!isInitialized) {
            if (json.isNullOrBlank()) {
                return SearchSource.defaultSources()
            }

            return runCatching {
                val decoded: SerializedSearchSources =
                    settingsStorageJson.decodeFromString(json)
                normalizeAndValidateSearchSources(decoded)
            }.getOrElse {
                SearchSource.defaultSources()
            }
        }

        if (json.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val decoded: SerializedSearchSources =
                settingsStorageJson.decodeFromString(json)
            normalizeAndValidateSearchSources(decoded)
        }.getOrElse {
            emptyList()
        }
    }

    private fun serializeSearchSources(sources: List<SearchSource>): String {
        val normalized = normalizeAndValidateSearchSources(sources)
        return settingsStorageJson.encodeToString(normalized)
    }

    private fun normalizeAndValidateSearchSources(
        rawSources: List<SearchSource>
    ): List<SearchSource> {
        val normalized = rawSources.mapIndexed { index, source ->
            val normalizedPrefixes = source.prefixes
                .map(SearchSource.Companion::normalizePrefix)
                .filter { it.isNotBlank() && !it.contains(" ") }
                .distinct()

            val safeName = source.name.trim().ifBlank { "Source ${index + 1}" }
            val safeTemplate = if (SearchSource.isValidUrlTemplate(source.urlTemplate)) {
                source.urlTemplate.trim()
            } else {
                "https://www.google.com/search?q={query}"
            }

            source.copy(
                name = safeName,
                urlTemplate = safeTemplate,
                prefixes = normalizedPrefixes,
                accentColorHex = SearchSource.normalizeHexColor(source.accentColorHex)
            )
        }

        val seenPrefixes = mutableSetOf<String>()
        return normalized.map { source ->
            val filteredPrefixes = source.prefixes.filter { prefix ->
                if (prefix in seenPrefixes) {
                    false
                } else {
                    seenPrefixes.add(prefix)
                    true
                }
            }
            source.copy(prefixes = filteredPrefixes)
        }
    }
}
