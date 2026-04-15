package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource
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

    private enum class PrefixOwnerKind {
        PROVIDER,
        SOURCE
    }

    private data class PrefixOwner(
        val id: String,
        val kind: PrefixOwnerKind
    )

    fun addSearchSource(
        preferences: MutablePreferences,
        source: SearchSource
    ): PrefixMutationResult {
        val currentSources = parseSearchSources(preferences)
        val currentProviderConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])

        val normalizedSource = source.copy(
            name = source.name.trim().ifBlank { "Source" },
            urlTemplate = if (SearchSource.isValidUrlTemplate(source.urlTemplate)) {
                source.urlTemplate.trim()
            } else {
                "https://www.google.com/search?q={query}"
            },
            prefixes = normalizePrefixes(source.prefixes),
            accentColorHex = SearchSource.normalizeHexColor(source.accentColorHex)
        )

        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = normalizedSource.prefixes,
            ignoredOwner = null,
            sources = currentSources,
            providerConfigurations = currentProviderConfigurations
        )

        if (conflictingOwner != null) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner(conflictingOwner.id)
        }

        writeSearchSources(currentSources + normalizedSource, preferences)
        return PrefixMutationResult.Success
    }

    fun updateSearchSource(
        preferences: MutablePreferences,
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ): PrefixMutationResult {
        val currentSources = parseSearchSources(preferences)
        val currentProviderConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])

        if (currentSources.none { it.id == sourceId }) {
            return PrefixMutationResult.TargetNotFound
        }

        val normalizedPrefixes = normalizePrefixes(prefixes)
        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = normalizedPrefixes,
            ignoredOwner = PrefixOwner(
                id = sourceId,
                kind = PrefixOwnerKind.SOURCE
            ),
            sources = currentSources,
            providerConfigurations = currentProviderConfigurations
        )

        if (conflictingOwner != null) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner(conflictingOwner.id)
        }

        val updatedSources = currentSources.map { existing ->
            if (existing.id == sourceId) {
                existing.copy(
                    name = name.trim(),
                    urlTemplate = urlTemplate.trim(),
                    prefixes = normalizedPrefixes,
                    accentColorHex = SearchSource.normalizeHexColor(accentColorHex)
                )
            } else {
                existing
            }
        }

        writeSearchSources(updatedSources, preferences)
        return PrefixMutationResult.Success
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
        if (providerId !in ProviderId.all) {
            return
        }

        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val currentSources = parseSearchSources(preferences)
        val updatedConfigurations = currentConfigurations.toMutableMap()
        val normalizedPrefixes = normalizePrefixes(prefixes)

        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = normalizedPrefixes,
            ignoredOwner = PrefixOwner(
                id = providerId,
                kind = PrefixOwnerKind.PROVIDER
            ),
            sources = currentSources,
            providerConfigurations = currentConfigurations
        )

        if (conflictingOwner != null) {
            return
        }

        if (normalizedPrefixes.isNotEmpty()) {
            updatedConfigurations[providerId] = PrefixConfig(normalizedPrefixes)
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
    ): PrefixMutationResult {
        if (providerId !in ProviderId.all) {
            return PrefixMutationResult.TargetNotFound
        }

        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        if (normalizedPrefix.isEmpty()) {
            return PrefixMutationResult.InvalidPrefixEmpty
        }

        if (normalizedPrefix.contains(" ")) {
            return PrefixMutationResult.InvalidPrefixContainsSpaces
        }

        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val currentSources = parseSearchSources(preferences)
        val currentPrefixes = normalizePrefixes(
            currentConfigurations[providerId]?.prefixes ?: listOf(defaultPrefix)
        )

        if (normalizedPrefix in currentPrefixes) {
            return PrefixMutationResult.PrefixAlreadyExistsOnTarget
        }

        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = listOf(normalizedPrefix),
            ignoredOwner = PrefixOwner(
                id = providerId,
                kind = PrefixOwnerKind.PROVIDER
            ),
            sources = currentSources,
            providerConfigurations = currentConfigurations
        )

        if (conflictingOwner != null) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner(conflictingOwner.id)
        }

        val updatedPrefixes = (currentPrefixes + normalizedPrefix).distinct()

        val updatedConfigurations = currentConfigurations.toMutableMap()
        updatedConfigurations[providerId] = PrefixConfig(updatedPrefixes)
        writePrefixConfigurations(updatedConfigurations, preferences)
        return PrefixMutationResult.Success
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
    ): PrefixMutationResult {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)
        val currentProviderConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])

        if (normalizedPrefix.isEmpty()) {
            return PrefixMutationResult.InvalidPrefixEmpty
        }

        if (normalizedPrefix.contains(" ")) {
            return PrefixMutationResult.InvalidPrefixContainsSpaces
        }

        val currentSources = parseSearchSources(preferences)
        val targetSource = currentSources.firstOrNull { it.id == sourceId }
            ?: return PrefixMutationResult.TargetNotFound

        val normalizedTargetPrefixes = targetSource.prefixes
            .map(SearchSource.Companion::normalizePrefix)

        if (normalizedPrefix in normalizedTargetPrefixes) {
            return PrefixMutationResult.PrefixAlreadyExistsOnTarget
        }

        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = listOf(normalizedPrefix),
            ignoredOwner = PrefixOwner(
                id = sourceId,
                kind = PrefixOwnerKind.SOURCE
            ),
            sources = currentSources,
            providerConfigurations = currentProviderConfigurations
        )

        if (conflictingOwner != null) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner(conflictingOwner.id)
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
        return PrefixMutationResult.Success
    }

    fun removePrefixFromSource(
        preferences: MutablePreferences,
        sourceId: String,
        prefix: String
    ): PrefixMutationResult {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        if (normalizedPrefix.isEmpty()) {
            return PrefixMutationResult.InvalidPrefixEmpty
        }

        val currentSources = parseSearchSources(preferences)
        val targetSource = currentSources.firstOrNull { it.id == sourceId }
            ?: return PrefixMutationResult.TargetNotFound

        val normalizedTargetPrefixes = targetSource.prefixes
            .map(SearchSource.Companion::normalizePrefix)

        if (normalizedPrefix !in normalizedTargetPrefixes) {
            return PrefixMutationResult.PrefixNotFoundOnTarget
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
        return PrefixMutationResult.Success
    }

    private fun normalizePrefixes(prefixes: List<String>): List<String> {
        return prefixes
            .map(SearchSource.Companion::normalizePrefix)
            .filter { it.isNotBlank() && !it.contains(" ") }
            .distinct()
    }

    private fun findConflictingOwner(
        prefixesToClaim: List<String>,
        ignoredOwner: PrefixOwner?,
        sources: List<SearchSource>,
        providerConfigurations: ProviderPrefixConfiguration
    ): PrefixOwner? {
        if (prefixesToClaim.isEmpty()) {
            return null
        }

        val requested = prefixesToClaim.toSet()

        val effectiveProviderPrefixes = ProviderId.all.associateWith { providerId ->
            val prefixes = providerConfigurations[providerId]?.prefixes
                ?: PrefixConfig.defaults[providerId]?.prefixes.orEmpty()
            normalizePrefixes(prefixes)
        }

        for ((providerId, providerPrefixes) in effectiveProviderPrefixes) {
            val owner = PrefixOwner(id = providerId, kind = PrefixOwnerKind.PROVIDER)
            if (owner == ignoredOwner) {
                continue
            }
            if (providerPrefixes.any(requested::contains)) {
                return owner
            }
        }

        for (source in sources) {
            val owner = PrefixOwner(id = source.id, kind = PrefixOwnerKind.SOURCE)
            if (owner == ignoredOwner) {
                continue
            }
            if (normalizePrefixes(source.prefixes).any(requested::contains)) {
                return owner
            }
        }

        return null
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
