/**
 * SettingsActivity.kt - Settings entry point for the Milki Launcher
 */

package com.milki.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.milki.launcher.presentation.settings.SettingsViewModel
import com.milki.launcher.ui.screens.SettingsScreen
import com.milki.launcher.ui.theme.LauncherTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsActivity : ComponentActivity() {

    /**
     * SettingsViewModel instance provided by Koin.
     *
     * Koin's viewModel() delegate handles:
     * - Creating the ViewModel with all its dependencies (SettingsRepository)
     * - Scoping the ViewModel to this Activity's lifecycle
     * - Surviving configuration changes (screen rotation)
     * - Clearing when the Activity is destroyed
     *
     * BEFORE (manual DI):
     * private val settingsViewModel: SettingsViewModel by lazy {
     *     (application as LauncherApplication).container.settingsViewModelFactory
     *         .create(SettingsViewModel::class.java)
     * }
     *
     * AFTER (Koin):
     * private val settingsViewModel: SettingsViewModel by viewModel()
     */
    private val settingsViewModel: SettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by settingsViewModel.settings.collectAsState()

            LauncherTheme {
                SettingsScreen(
                    settings = settings,
                    onNavigateBack = { finish() },
                    onSetMaxSearchResults = settingsViewModel::setMaxSearchResults,
                    onSetAutoFocusKeyboard = settingsViewModel::setAutoFocusKeyboard,
                    onSetShowRecentApps = settingsViewModel::setShowRecentApps,
                    onSetMaxRecentApps = settingsViewModel::setMaxRecentApps,
                    onSetCloseSearchOnLaunch = settingsViewModel::setCloseSearchOnLaunch,
                    onSetSearchResultLayout = settingsViewModel::setSearchResultLayout,
                    onSetShowHomescreenHint = settingsViewModel::setShowHomescreenHint,
                    onSetShowAppIcons = settingsViewModel::setShowAppIcons,
                    onSetHomeTapAction = settingsViewModel::setHomeTapAction,
                    onSetSwipeUpAction = settingsViewModel::setSwipeUpAction,
                    onSetHomeButtonClearsQuery = settingsViewModel::setHomeButtonClearsQuery,
                    onSetDefaultSearchEngine = settingsViewModel::setDefaultSearchEngine,
                    onSetWebSearchEnabled = settingsViewModel::setWebSearchEnabled,
                    onSetContactsSearchEnabled = settingsViewModel::setContactsSearchEnabled,
                    onSetYoutubeSearchEnabled = settingsViewModel::setYoutubeSearchEnabled,
                    onSetFilesSearchEnabled = settingsViewModel::setFilesSearchEnabled,
                    onResetToDefaults = settingsViewModel::resetToDefaults
                )
            }
        }
    }
}
