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

    fun setDefaultSearchEngine(engine: SearchEngine) {
        viewModelScope.launch {
            settingsRepository.setDefaultSearchEngine(engine)
        }
    }

    fun setWebSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWebSearchEnabled(value)
        }
    }

    fun setContactsSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContactsSearchEnabled(value)
        }
    }

    fun setYoutubeSearchEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setYoutubeSearchEnabled(value)
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
     * @param providerId The provider ID (e.g., ProviderId.WEB)
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
            ProviderId.WEB -> "s"
            ProviderId.CONTACTS -> "c"
            ProviderId.YOUTUBE -> "y"
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
