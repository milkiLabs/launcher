package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource

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
            prefixes = SearchSource.normalizePrefixes(source.prefixes),
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

        val normalizedPrefixes = SearchSource.normalizePrefixes(prefixes)
        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = normalizedPrefixes,
            ignoredOwner = sourceOwner(sourceId),
            sources = currentSources,
            providerConfigurations = currentProviderConfigurations
        )

        return when {
            currentSources.none { it.id == sourceId } -> PrefixMutationResult.TargetNotFound
            conflictingOwner != null -> duplicatePrefixResult(conflictingOwner)
            else -> {
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
                PrefixMutationResult.Success
            }
        }
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

    fun setSearchSourceSuggestedAction(
        preferences: MutablePreferences,
        sourceId: String,
        showAsSuggestedAction: Boolean
    ) {
        val currentSources = parseSearchSources(preferences)

        if (currentSources.none { it.id == sourceId }) {
            return
        }

        val updatedSources = currentSources.map { source ->
            if (source.id == sourceId) {
                source.copy(showAsSuggestedAction = showAsSuggestedAction)
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
    ): PrefixMutationResult {
        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val currentSources = parseSearchSources(preferences)
        val updatedConfigurations = currentConfigurations.toMutableMap()
        val normalizedPrefixes = SearchSource.normalizePrefixes(prefixes)

        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = normalizedPrefixes,
            ignoredOwner = providerOwner(providerId),
            sources = currentSources,
            providerConfigurations = currentConfigurations
        )

        return when {
            providerId !in ProviderId.all -> PrefixMutationResult.TargetNotFound
            conflictingOwner != null -> duplicatePrefixResult(conflictingOwner)
            else -> {
                if (normalizedPrefixes.isNotEmpty()) {
                    updatedConfigurations[providerId] = PrefixConfig(normalizedPrefixes)
                } else {
                    updatedConfigurations.remove(providerId)
                }

                writePrefixConfigurations(updatedConfigurations, preferences)
                PrefixMutationResult.Success
            }
        }
    }

    fun addProviderPrefix(
        preferences: MutablePreferences,
        providerId: String,
        prefix: String,
        defaultPrefix: String
    ): PrefixMutationResult {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)
        val currentConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val currentSources = parseSearchSources(preferences)
        val currentPrefixes = SearchSource.normalizePrefixes(
            currentConfigurations[providerId]?.prefixes ?: listOf(defaultPrefix)
        )

        val conflictingOwner = findConflictingOwner(
            prefixesToClaim = listOf(normalizedPrefix),
            ignoredOwner = providerOwner(providerId),
            sources = currentSources,
            providerConfigurations = currentConfigurations
        )

        return when {
            providerId !in ProviderId.all -> PrefixMutationResult.TargetNotFound
            normalizedPrefix.isEmpty() -> PrefixMutationResult.InvalidPrefixEmpty
            normalizedPrefix.contains(" ") -> PrefixMutationResult.InvalidPrefixContainsSpaces
            normalizedPrefix in currentPrefixes -> PrefixMutationResult.PrefixAlreadyExistsOnTarget
            conflictingOwner != null -> duplicatePrefixResult(conflictingOwner)
            else -> {
                val updatedPrefixes = (currentPrefixes + normalizedPrefix).distinct()
                val updatedConfigurations = currentConfigurations.toMutableMap()
                updatedConfigurations[providerId] = PrefixConfig(updatedPrefixes)
                writePrefixConfigurations(updatedConfigurations, preferences)
                PrefixMutationResult.Success
            }
        }
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

        val currentSources = parseSearchSources(preferences)
        val targetSource = currentSources.firstOrNull { it.id == sourceId }

        val normalizedTargetPrefixes = targetSource?.prefixes
            .orEmpty()
            .map(SearchSource.Companion::normalizePrefix)

        val conflictingOwner = targetSource?.let {
            findConflictingOwner(
                prefixesToClaim = listOf(normalizedPrefix),
                ignoredOwner = sourceOwner(sourceId),
                sources = currentSources,
                providerConfigurations = currentProviderConfigurations
            )
        }

        return when {
            normalizedPrefix.isEmpty() -> PrefixMutationResult.InvalidPrefixEmpty
            normalizedPrefix.contains(" ") -> PrefixMutationResult.InvalidPrefixContainsSpaces
            targetSource == null -> PrefixMutationResult.TargetNotFound
            normalizedPrefix in normalizedTargetPrefixes -> PrefixMutationResult.PrefixAlreadyExistsOnTarget
            conflictingOwner != null -> duplicatePrefixResult(conflictingOwner)
            else -> {
                val updatedSources = currentSources.map { source ->
                    if (source.id == sourceId) {
                        source.copy(
                            prefixes = SearchSource.normalizePrefixes(
                                source.prefixes + normalizedPrefix
                            )
                        )
                    } else {
                        source
                    }
                }

                writeSearchSources(updatedSources, preferences)
                PrefixMutationResult.Success
            }
        }
    }

    fun removePrefixFromSource(
        preferences: MutablePreferences,
        sourceId: String,
        prefix: String
    ): PrefixMutationResult {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)
        val currentSources = parseSearchSources(preferences)
        val targetSource = currentSources.firstOrNull { it.id == sourceId }

        val normalizedTargetPrefixes = targetSource?.prefixes
            .orEmpty()
            .map(SearchSource.Companion::normalizePrefix)

        return when {
            normalizedPrefix.isEmpty() -> PrefixMutationResult.InvalidPrefixEmpty
            targetSource == null -> PrefixMutationResult.TargetNotFound
            normalizedPrefix !in normalizedTargetPrefixes -> PrefixMutationResult.PrefixNotFoundOnTarget
            else -> {
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
                PrefixMutationResult.Success
            }
        }
    }

    private fun findConflictingOwner(
        prefixesToClaim: List<String>,
        ignoredOwner: PrefixOwner?,
        sources: List<SearchSource>,
        providerConfigurations: ProviderPrefixConfiguration
    ): PrefixOwner? {
        val requested = prefixesToClaim.takeIf { it.isNotEmpty() }?.toSet()
        val providerConflict = requested?.let { requestedPrefixes ->
            ProviderId.all.firstNotNullOfOrNull { providerId ->
                val owner = providerOwner(providerId)
                val providerPrefixes = providerConfigurations[providerId]?.prefixes
                    ?: PrefixConfig.defaults[providerId]?.prefixes.orEmpty()
                owner.takeUnless { it == ignoredOwner }
                    ?.takeIf {
                        SearchSource.normalizePrefixes(providerPrefixes).any(requestedPrefixes::contains)
                    }
            }
        }
        val sourceConflict = requested?.let { requestedPrefixes ->
            sources.firstNotNullOfOrNull { source ->
                val owner = sourceOwner(source.id)
                owner.takeUnless { it == ignoredOwner }
                    ?.takeIf {
                        SearchSource.normalizePrefixes(source.prefixes).any(requestedPrefixes::contains)
                    }
            }
        }

        return providerConflict ?: sourceConflict
    }

    private fun providerOwner(providerId: String): PrefixOwner {
        return PrefixOwner(id = providerId, kind = PrefixOwnerKind.PROVIDER)
    }

    private fun sourceOwner(sourceId: String): PrefixOwner {
        return PrefixOwner(id = sourceId, kind = PrefixOwnerKind.SOURCE)
    }

    private fun duplicatePrefixResult(conflictingOwner: PrefixOwner): PrefixMutationResult {
        return PrefixMutationResult.DuplicatePrefixOnAnotherOwner(conflictingOwner.id)
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
}
