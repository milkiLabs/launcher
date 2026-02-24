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
