/**
 * SettingsActivity.kt - Settings entry point for the Milki Launcher
 *
 * This Activity hosts the Settings screen. It's registered separately in the
 * AndroidManifest with its own intent filter so it can be:
 * 1. Launched from the app drawer as "Launcher Settings"
 * 2. Launched from the launcher itself via a settings action
 *
 * ARCHITECTURE:
 * Like MainActivity, this follows the "dumb View" pattern:
 * - State flows from SettingsViewModel via LauncherSettings
 * - User actions are dispatched to SettingsViewModel methods
 * - Activity just renders the Compose UI
 *
 * LIFECYCLE:
 * - Standard Activity lifecycle (not singleTask like MainActivity)
 * - finish() is called when user navigates back
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

/**
 * SettingsActivity - Hosts the launcher settings screen.
 *
 * This is a separate Activity (not part of MainActivity) because:
 * 1. It needs its own launcher entry point in the app drawer
 * 2. It shouldn't interfere with the home screen behavior
 * 3. It can be navigated to independently from any context
 */
class SettingsActivity : ComponentActivity() {

    /**
     * SettingsViewModel instance obtained from the AppContainer.
     * Created lazily to ensure AppContainer is initialized first.
     */
    private val settingsViewModel: SettingsViewModel by lazy {
        (application as LauncherApplication).container.settingsViewModelFactory
            .create(SettingsViewModel::class.java)
    }

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
