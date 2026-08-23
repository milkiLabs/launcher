package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource

/**
 * Dedicated store for settings mutation logic that operates on a mutable
 * DataStore preferences snapshot.
 *
 * WHY THIS FILE EXISTS:
 * - Keeps mutation-heavy logic out of SettingsRepositoryImpl
 * - Centralizes all read-modify-write behavior
 * - Makes persistence rules easier to audit and test in isolation
 *
 * SCOPE:
 * 1. Search-source CRUD (add/update/delete/toggle)
 * 2. Unified prefix operations for any owner (provider or source)
 * 3. Cross-owner prefix conflict detection
 */
internal class SettingsMutationStore {

    // ========================================================================
    // SEARCH SOURCE CRUD
    // ========================================================================

    fun addSearchSource(
        preferences: MutablePreferences,
        source: SearchSource
    ): PrefixMutationResult {
        val currentSources = preferences.parseStoredSearchSources()
        val currentPrefixConfigs = parsePrefixConfigurations(
            preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS]
        )

        if (hasPrefixConflict(
                SearchSource.normalizePrefixes(source.prefixes),
                ignoredOwnerId = null,
                sources = currentSources,
                providerConfigs = currentPrefixConfigs
            )
        ) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner
        }

        preferences.writeSearchSources(currentSources + source)
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
        val currentSources = preferences.parseStoredSearchSources()
        val currentPrefixConfigs = parsePrefixConfigurations(
            preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS]
        )

        if (currentSources.none { it.id == sourceId }) {
            return PrefixMutationResult.TargetNotFound
        }

        val normalizedPrefixes = SearchSource.normalizePrefixes(prefixes)

        if (hasPrefixConflict(normalizedPrefixes, ignoredOwnerId = sourceId, sources = currentSources, providerConfigs = currentPrefixConfigs)) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner
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

        preferences.writeSearchSources(updatedSources)
        return PrefixMutationResult.Success
    }

    fun deleteSearchSource(preferences: MutablePreferences, sourceId: String) {
        val currentSources = preferences.parseStoredSearchSources()
        val updatedSources = currentSources.filterNot { it.id == sourceId }
        if (updatedSources != currentSources) {
            preferences.writeSearchSources(updatedSources)
        }
    }

    fun setSearchSourceEnabled(
        preferences: MutablePreferences,
        sourceId: String,
        enabled: Boolean
    ) {
        val currentSources = preferences.parseStoredSearchSources()
        if (currentSources.none { it.id == sourceId }) return

        preferences.writeSearchSources(
            currentSources.map { if (it.id == sourceId) it.copy(isEnabled = enabled) else it }
        )
    }

    fun setSearchSourceSuggestedAction(
        preferences: MutablePreferences,
        sourceId: String,
        showAsSuggestedAction: Boolean
    ) {
        val currentSources = preferences.parseStoredSearchSources()
        if (currentSources.none { it.id == sourceId }) return

        preferences.writeSearchSources(
            currentSources.map { if (it.id == sourceId) it.copy(showAsSuggestedAction = showAsSuggestedAction) else it }
        )
    }

    // ========================================================================
    // UNIFIED PREFIX OPERATIONS
    // ========================================================================

    /**
     * Add a prefix to any owner (provider or source).
     */
    fun addPrefix(
        preferences: MutablePreferences,
        ownerId: String,
        prefix: String
    ): PrefixMutationResult {
        val normalizedPrefixes = SearchSource.normalizePrefixes(listOf(prefix))

        if (normalizedPrefixes.isEmpty()) {
            return if (prefix.isNotBlank()) {
                PrefixMutationResult.InvalidPrefixContainsSpaces
            } else {
                PrefixMutationResult.InvalidPrefixEmpty
            }
        }
        val normalizedPrefix = normalizedPrefixes.single()

        val sources = preferences.parseStoredSearchSources()
        val providerConfigs = parsePrefixConfigurations(
            preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS]
        )

        return when {
            isProviderId(ownerId) -> addProviderPrefix(preferences, ownerId, normalizedPrefix, sources, providerConfigs)
            else -> addSourcePrefix(preferences, ownerId, normalizedPrefix, sources, providerConfigs)
        }
    }

    /**
     * Remove a prefix from any owner.
     * If this would leave the owner prefixless, reset to defaults instead.
     */
    fun removePrefix(
        preferences: MutablePreferences,
        ownerId: String,
        prefix: String
    ): PrefixMutationResult {
        val normalizedPrefixes = SearchSource.normalizePrefixes(listOf(prefix))
        if (normalizedPrefixes.isEmpty()) return PrefixMutationResult.InvalidPrefixEmpty
        val normalizedPrefix = normalizedPrefixes.single()

        val sources = preferences.parseStoredSearchSources()

        return when {
            isProviderId(ownerId) -> removeProviderPrefix(preferences, ownerId, normalizedPrefix)
            else -> removeSourcePrefix(preferences, ownerId, normalizedPrefix, sources)
        }
    }

    /**
     * Reset any owner's prefixes back to defaults.
     */
    fun resetPrefixes(preferences: MutablePreferences, ownerId: String) {
        when {
            isProviderId(ownerId) -> resetProviderPrefixes(preferences, ownerId)
            else -> resetSourcePrefixes(preferences, ownerId)
        }
    }

    /**
     * Reset all prefix customizations.
     */
    fun resetAllPrefixes(preferences: MutablePreferences) {
        preferences.remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
        val sources = preferences.parseStoredSearchSources()
        val resetSources = sources.map { it.copy(prefixes = it.defaultPrefixes) }
        if (resetSources != sources) {
            preferences.writeSearchSources(resetSources)
        }
    }

    // ========================================================================
    // PROVIDER PREFIX MUTATIONS (internal)
    // ========================================================================

    private fun addProviderPrefix(
        preferences: MutablePreferences,
        providerId: String,
        normalizedPrefix: String,
        sources: List<SearchSource>,
        providerConfigs: ProviderPrefixConfiguration
    ): PrefixMutationResult {
        if (providerId !in ProviderId.all) return PrefixMutationResult.TargetNotFound

        val currentPrefixes = getProviderPrefixes(providerId, providerConfigs)
        if (normalizedPrefix in currentPrefixes) return PrefixMutationResult.PrefixAlreadyExistsOnTarget

        if (hasPrefixConflict(listOf(normalizedPrefix), ignoredOwnerId = providerId, sources = sources, providerConfigs = providerConfigs)) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner
        }

        val updatedConfigs = providerConfigs.toMutableMap()
        updatedConfigs[providerId] = PrefixConfig(currentPrefixes + normalizedPrefix)
        preferences.writePrefixConfigurations(updatedConfigs)
        return PrefixMutationResult.Success
    }

    private fun removeProviderPrefix(
        preferences: MutablePreferences,
        providerId: String,
        normalizedPrefix: String
    ): PrefixMutationResult {
        val currentConfigs = parsePrefixConfigurations(
            preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS]
        )
        val currentPrefixes = currentConfigs[providerId]?.prefixes ?: return PrefixMutationResult.PrefixNotFoundOnTarget
        if (normalizedPrefix !in currentPrefixes) return PrefixMutationResult.PrefixNotFoundOnTarget

        val remaining = currentPrefixes - normalizedPrefix
        val updatedConfigs = currentConfigs.toMutableMap()

        if (remaining.isNotEmpty()) {
            updatedConfigs[providerId] = PrefixConfig(remaining)
        } else {
            updatedConfigs.remove(providerId)
        }

        preferences.writePrefixConfigurations(updatedConfigs)
        return PrefixMutationResult.Success
    }

    private fun resetProviderPrefixes(preferences: MutablePreferences, providerId: String) {
        val currentConfigs = parsePrefixConfigurations(
            preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS]
        )
        if (providerId !in currentConfigs) return

        val updatedConfigs = currentConfigs.toMutableMap()
        updatedConfigs.remove(providerId)
        preferences.writePrefixConfigurations(updatedConfigs)
    }

    // ========================================================================
    // SOURCE PREFIX MUTATIONS (internal)
    // ========================================================================

    private fun addSourcePrefix(
        preferences: MutablePreferences,
        sourceId: String,
        normalizedPrefix: String,
        sources: List<SearchSource>,
        providerConfigs: ProviderPrefixConfiguration
    ): PrefixMutationResult {
        val targetSource = sources.firstOrNull { it.id == sourceId }
            ?: return PrefixMutationResult.TargetNotFound

        val currentPrefixes = SearchSource.normalizePrefixes(targetSource.prefixes)
        if (normalizedPrefix in currentPrefixes) return PrefixMutationResult.PrefixAlreadyExistsOnTarget

        if (hasPrefixConflict(listOf(normalizedPrefix), ignoredOwnerId = sourceId, sources = sources, providerConfigs = providerConfigs)) {
            return PrefixMutationResult.DuplicatePrefixOnAnotherOwner
        }

        val updatedSources = sources.map { source ->
            if (source.id == sourceId) {
                source.copy(prefixes = SearchSource.normalizePrefixes(source.prefixes + normalizedPrefix))
            } else {
                source
            }
        }

        preferences.writeSearchSources(updatedSources)
        return PrefixMutationResult.Success
    }

    private fun removeSourcePrefix(
        preferences: MutablePreferences,
        sourceId: String,
        normalizedPrefix: String,
        sources: List<SearchSource>
    ): PrefixMutationResult {
        val targetSource = sources.firstOrNull { it.id == sourceId }
            ?: return PrefixMutationResult.TargetNotFound

        val currentPrefixes = SearchSource.normalizePrefixes(targetSource.prefixes)
        if (normalizedPrefix !in currentPrefixes) return PrefixMutationResult.PrefixNotFoundOnTarget

        val remaining = currentPrefixes - normalizedPrefix

        val updatedSources = sources.map { source ->
            if (source.id == sourceId) {
                source.copy(
                    prefixes = if (remaining.isEmpty()) {
                        source.defaultPrefixes.ifEmpty { listOf(source.primaryPrefix) }
                    } else {
                        remaining
                    }
                )
            } else {
                source
            }
        }

        preferences.writeSearchSources(updatedSources)
        return PrefixMutationResult.Success
    }

    private fun resetSourcePrefixes(preferences: MutablePreferences, sourceId: String) {
        val sources = preferences.parseStoredSearchSources()
        val targetSource = sources.firstOrNull { it.id == sourceId } ?: return
        if (targetSource.defaultPrefixes.isEmpty()) return

        val updatedSources = sources.map { source ->
            if (source.id == sourceId) source.copy(prefixes = source.defaultPrefixes) else source
        }

        preferences.writeSearchSources(updatedSources)
    }

    // ========================================================================
    // CONFLICT DETECTION
    // ========================================================================

    private fun hasPrefixConflict(
        prefixesToClaim: List<String>,
        ignoredOwnerId: String?,
        sources: List<SearchSource>,
        providerConfigs: ProviderPrefixConfiguration
    ): Boolean {
        val requested = prefixesToClaim.takeIf { it.isNotEmpty() }?.map(SearchSource::normalizePrefix)?.toSet()
            ?: return false

        for (providerId in ProviderId.all) {
            if (providerId == ignoredOwnerId) continue
            val providerPrefixes = providerConfigs[providerId]?.prefixes
                ?: PrefixConfig.defaults[providerId]?.prefixes.orEmpty()
            if (providerPrefixes.map(SearchSource::normalizePrefix).any(requested::contains)) {
                return true
            }
        }

        for (source in sources) {
            if (source.id == ignoredOwnerId) continue
            if (source.prefixes.map(SearchSource::normalizePrefix).any(requested::contains)) {
                return true
            }
        }

        return false
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private fun isProviderId(id: String): Boolean = id in ProviderId.all

    private fun getProviderPrefixes(
        providerId: String,
        configs: ProviderPrefixConfiguration
    ): List<String> {
        return configs[providerId]?.prefixes
            ?: PrefixConfig.defaults[providerId]?.prefixes.orEmpty()
    }
}
