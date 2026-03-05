/**
 * SettingsViewModel.kt - ViewModel for the settings screen
 *
 * Manages the settings UI state and handles user interactions.
 * Follows the same UDF pattern as SearchViewModel.
 *
 * RESPONSIBILITIES:
 * - Observe settings from SettingsRepository
 * - Apply user-requested setting changes
 * - NOT responsible for rendering UI (that's the Composables)
 */

package com.milki.launcher.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 *
 * @param settingsRepository Repository for reading/writing settings
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        const val PREFIX_ADD_SUCCESS = ""
        const val PREFIX_ERROR_EMPTY = "Prefix cannot be empty"
        const val PREFIX_ERROR_SPACES = "Prefix cannot contain spaces"
        const val PREFIX_ERROR_DUPLICATE = "Prefix is already used by another source"
    }

    /**
     * Current settings state, observed from the repository.
     * Starts with default settings until DataStore emits.
     */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LauncherSettings()
        )

    // ========================================================================
    // SEARCH BEHAVIOR
    // ========================================================================

    fun setMaxSearchResults(value: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxSearchResults(value.coerceIn(3, 20))
        }
    }

    fun setAutoFocusKeyboard(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoFocusKeyboard(value)
        }
    }

    fun setShowRecentApps(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowRecentApps(value)
        }
    }

    fun setMaxRecentApps(value: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxRecentApps(value.coerceIn(1, 10))
        }
    }

    fun setCloseSearchOnLaunch(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCloseSearchOnLaunch(value)
        }
    }

    // ========================================================================
    // APPEARANCE
    // ========================================================================

    fun setSearchResultLayout(layout: SearchResultLayout) {
        viewModelScope.launch {
            settingsRepository.setSearchResultLayout(layout)
        }
    }

    fun setShowHomescreenHint(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHomescreenHint(value)
        }
    }

    fun setShowAppIcons(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowAppIcons(value)
        }
    }

    // ========================================================================
    // HOME SCREEN
    // ========================================================================

    fun setHomeTapAction(action: HomeTapAction) {
        viewModelScope.launch {
            settingsRepository.setHomeTapAction(action)
        }
    }

    fun setSwipeUpAction(action: SwipeUpAction) {
        viewModelScope.launch {
            settingsRepository.setSwipeUpAction(action)
        }
    }

    fun setHomeButtonClearsQuery(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHomeButtonClearsQuery(value)
        }
    }

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    fun setContactsSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContactsSearchEnabled(value)
        }
    }

    // ========================================================================
    // DYNAMIC SEARCH SOURCES
    // ========================================================================

    /**
     * Adds a new custom source.
     */
    fun addSearchSource(
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                val normalizedPrefixes = prefixes
                    .map(SearchSource.Companion::normalizePrefix)
                    .filter { it.isNotBlank() && !it.contains(" ") }
                    .distinct()

                val newSource = SearchSource.create(
                    name = name.trim(),
                    urlTemplate = urlTemplate.trim(),
                    prefixes = normalizedPrefixes,
                    accentColorHex = accentColorHex
                )

                current.copy(searchSources = current.searchSources + newSource)
            }
        }
    }

    /**
     * Updates an existing source.
     */
    fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                val normalizedPrefixes = prefixes
                    .map(SearchSource.Companion::normalizePrefix)
                    .filter { it.isNotBlank() && !it.contains(" ") }
                    .distinct()

                current.copy(
                    searchSources = current.searchSources.map { source ->
                        if (source.id == sourceId) {
                            source.copy(
                                name = name.trim(),
                                urlTemplate = urlTemplate.trim(),
                                prefixes = normalizedPrefixes,
                                accentColorHex = SearchSource.normalizeHexColor(accentColorHex)
                            )
                        } else {
                            source
                        }
                    }
                )
            }
        }
    }

    /**
     * Deletes one source by ID.
     */
    fun deleteSearchSource(sourceId: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(searchSources = current.searchSources.filterNot { it.id == sourceId })
            }
        }
    }

    /**
     * Toggles source enabled/disabled state.
     */
    fun setSearchSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(searchSources = current.searchSources.map { source ->
                    if (source.id == sourceId) {
                        source.copy(isEnabled = enabled)
                    } else {
                        source
                    }
                })
            }
        }
    }

    /**
     * Adds a prefix to a source with global uniqueness validation.
     *
     * @return empty string on success, otherwise human-readable error.
     */
    fun addPrefixToSource(sourceId: String, prefix: String, onValidationResult: (String) -> Unit) {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        when {
            normalizedPrefix.isEmpty() -> {
                onValidationResult(PREFIX_ERROR_EMPTY)
                return
            }
            normalizedPrefix.contains(" ") -> {
                onValidationResult(PREFIX_ERROR_SPACES)
                return
            }
        }

        val allOtherPrefixes = settings.value.searchSources
            .filter { it.id != sourceId }
            .flatMap { it.prefixes }
            .map(SearchSource.Companion::normalizePrefix)
            .toSet()

        if (normalizedPrefix in allOtherPrefixes) {
            onValidationResult(PREFIX_ERROR_DUPLICATE)
            return
        }

        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(
                    searchSources = current.searchSources.map { source ->
                        if (source.id == sourceId) {
                            val updatedPrefixes = (source.prefixes + normalizedPrefix)
                                .map(SearchSource.Companion::normalizePrefix)
                                .distinct()
                            source.copy(prefixes = updatedPrefixes)
                        } else {
                            source
                        }
                    }
                )
            }
            onValidationResult(PREFIX_ADD_SUCCESS)
        }
    }

    /**
     * Removes one prefix from a source.
     */
    fun removePrefixFromSource(sourceId: String, prefix: String) {
        val normalizedPrefix = SearchSource.normalizePrefix(prefix)

        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(
                    searchSources = current.searchSources.map { source ->
                        if (source.id == sourceId) {
                            source.copy(prefixes = source.prefixes.filterNot {
                                SearchSource.normalizePrefix(it) == normalizedPrefix
                            })
                        } else {
                            source
                        }
                    }
                )
            }
        }
    }

    fun setFilesSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFilesSearchEnabled(value)
        }
    }

    // ========================================================================
    // PREFIX CONFIGURATION
    // ========================================================================

    /**
     * Set the prefixes for a specific provider.
     *
     * This replaces all existing prefixes for the provider with the new list.
     * The first prefix in the list is considered the "primary" prefix for display.
     *
    * @param providerId The provider ID (contacts/files)
     * @param prefixes List of prefixes to set for this provider
     */
    fun setProviderPrefixes(providerId: String, prefixes: List<String>) {
        viewModelScope.launch {
            settingsRepository.setProviderPrefixes(providerId, prefixes)
        }
    }

    /**
     * Add a prefix to a provider's existing prefixes.
     *
     * If the provider has no existing configuration, this creates one with
     * the default prefix plus the new prefix.
     *
     * @param providerId The provider ID
     * @param prefix The prefix to add
     */
    fun addProviderPrefix(providerId: String, prefix: String) {
        viewModelScope.launch {
            settingsRepository.addProviderPrefix(
                providerId = providerId,
                prefix = prefix,
                defaultPrefix = getDefaultPrefix(providerId)
            )
        }
    }

    /**
     * Remove a prefix from a provider's prefixes.
     *
     * If this is the last prefix, the provider will fall back to its default prefix.
     *
     * @param providerId The provider ID
     * @param prefix The prefix to remove
     */
    fun removeProviderPrefix(providerId: String, prefix: String) {
        viewModelScope.launch {
            settingsRepository.removeProviderPrefix(providerId, prefix)
        }
    }

    /**
     * Reset a provider's prefixes to the default.
     *
     * This removes any custom configuration for the provider.
     *
     * @param providerId The provider ID to reset
     */
    fun resetProviderPrefixes(providerId: String) {
        viewModelScope.launch {
            settingsRepository.resetProviderPrefixes(providerId)
        }
    }

    /**
     * Reset all prefix configurations to defaults.
     *
     * This removes all custom prefix configurations.
     */
    fun resetAllPrefixConfigurations() {
        viewModelScope.launch {
            settingsRepository.resetAllPrefixConfigurations()
        }
    }

    /**
     * Get the default prefix for a provider.
     *
     * This is used when there's no custom configuration for the provider.
     *
     * @param providerId The provider ID
     * @return The default prefix for this provider
     */
    private fun getDefaultPrefix(providerId: String): String {
        return when (providerId) {
            ProviderId.CONTACTS -> "c"
            ProviderId.FILES -> "f"
            else -> ""
        }
    }

    // ========================================================================
    // HIDDEN APPS
    // ========================================================================

    fun toggleHiddenApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.toggleHiddenApp(packageName)
        }
    }

    // ========================================================================
    // RESET
    // ========================================================================

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.updateSettings { LauncherSettings() }
        }
    }

}
