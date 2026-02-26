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
        updateSetting { it.copy(maxSearchResults = value.coerceIn(3, 20)) }
    }

    fun setAutoFocusKeyboard(value: Boolean) {
        updateSetting { it.copy(autoFocusKeyboard = value) }
    }

    fun setShowRecentApps(value: Boolean) {
        updateSetting { it.copy(showRecentApps = value) }
    }

    fun setMaxRecentApps(value: Int) {
        updateSetting { it.copy(maxRecentApps = value.coerceIn(1, 10)) }
    }

    fun setCloseSearchOnLaunch(value: Boolean) {
        updateSetting { it.copy(closeSearchOnLaunch = value) }
    }

    // ========================================================================
    // APPEARANCE
    // ========================================================================

    fun setSearchResultLayout(layout: SearchResultLayout) {
        updateSetting { it.copy(searchResultLayout = layout) }
    }

    fun setShowHomescreenHint(value: Boolean) {
        updateSetting { it.copy(showHomescreenHint = value) }
    }

    fun setShowAppIcons(value: Boolean) {
        updateSetting { it.copy(showAppIcons = value) }
    }

    // ========================================================================
    // HOME SCREEN
    // ========================================================================

    fun setHomeTapAction(action: HomeTapAction) {
        updateSetting { it.copy(homeTapAction = action) }
    }

    fun setSwipeUpAction(action: SwipeUpAction) {
        updateSetting { it.copy(swipeUpAction = action) }
    }

    fun setHomeButtonClearsQuery(value: Boolean) {
        updateSetting { it.copy(homeButtonClearsQuery = value) }
    }

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    fun setDefaultSearchEngine(engine: SearchEngine) {
        updateSetting { it.copy(defaultSearchEngine = engine) }
    }

    fun setWebSearchEnabled(value: Boolean) {
        updateSetting { it.copy(webSearchEnabled = value) }
    }

    fun setContactsSearchEnabled(value: Boolean) {
        updateSetting { it.copy(contactsSearchEnabled = value) }
    }

    fun setYoutubeSearchEnabled(value: Boolean) {
        updateSetting { it.copy(youtubeSearchEnabled = value) }
    }

    fun setFilesSearchEnabled(value: Boolean) {
        updateSetting { it.copy(filesSearchEnabled = value) }
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
        updateSetting { settings ->
            val newConfigurations = settings.prefixConfigurations.toMutableMap()
            if (prefixes.isNotEmpty()) {
                newConfigurations[providerId] = PrefixConfig(prefixes)
            } else {
                // If no prefixes, remove the configuration (will use default)
                newConfigurations.remove(providerId)
            }
            settings.copy(prefixConfigurations = newConfigurations)
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
        updateSetting { settings ->
            val currentPrefixes = settings.prefixConfigurations[providerId]?.prefixes
                ?: listOf(getDefaultPrefix(providerId))

            // Don't add duplicate prefixes
            if (prefix in currentPrefixes) {
                return@updateSetting settings
            }

            val newConfigurations = settings.prefixConfigurations.toMutableMap()
            newConfigurations[providerId] = PrefixConfig(currentPrefixes + prefix)
            settings.copy(prefixConfigurations = newConfigurations)
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
        updateSetting { settings ->
            val currentPrefixes = settings.prefixConfigurations[providerId]?.prefixes
                ?: return@updateSetting settings

            val newPrefixes = currentPrefixes - prefix

            val newConfigurations = settings.prefixConfigurations.toMutableMap()
            if (newPrefixes.isNotEmpty()) {
                newConfigurations[providerId] = PrefixConfig(newPrefixes)
            } else {
                // Remove configuration to fall back to default
                newConfigurations.remove(providerId)
            }
            settings.copy(prefixConfigurations = newConfigurations)
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
        updateSetting { settings ->
            val newConfigurations = settings.prefixConfigurations.toMutableMap()
            newConfigurations.remove(providerId)
            settings.copy(prefixConfigurations = newConfigurations)
        }
    }

    /**
     * Reset all prefix configurations to defaults.
     *
     * This removes all custom prefix configurations.
     */
    fun resetAllPrefixConfigurations() {
        updateSetting { it.copy(prefixConfigurations = emptyMap()) }
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
        updateSetting { settings ->
            val newHiddenApps = settings.hiddenApps.toMutableSet()
            if (packageName in newHiddenApps) {
                newHiddenApps.remove(packageName)
            } else {
                newHiddenApps.add(packageName)
            }
            settings.copy(hiddenApps = newHiddenApps)
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

    // ========================================================================
    // HELPER
    // ========================================================================

    private fun updateSetting(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }
}
