/**
 * SettingsActivity.kt - Settings entry point for the Milki Launcher
 */

package com.milki.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.presentation.settings.SettingsViewModel
import com.milki.launcher.ui.screens.SettingsActions
import com.milki.launcher.ui.screens.SettingsAdvancedActions
import com.milki.launcher.ui.screens.SettingsAppearanceActions
import com.milki.launcher.ui.screens.SettingsCustomSourceActions
import com.milki.launcher.ui.screens.SettingsHomeScreenActions
import com.milki.launcher.ui.screens.SettingsLocalPrefixActions
import com.milki.launcher.ui.screens.SettingsLocalProviderActions
import com.milki.launcher.ui.screens.SettingsSearchBehaviorActions
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
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val settingsActions = remember(settingsViewModel) {
                SettingsActions(
                    searchBehavior = SettingsSearchBehaviorActions(
                        onSetMaxSearchResults = settingsViewModel::setMaxSearchResults,
                        onSetAutoFocusKeyboard = settingsViewModel::setAutoFocusKeyboard,
                        onSetShowRecentApps = settingsViewModel::setShowRecentApps,
                        onSetMaxRecentApps = settingsViewModel::setMaxRecentApps,
                        onSetCloseSearchOnLaunch = settingsViewModel::setCloseSearchOnLaunch
                    ),
                    appearance = SettingsAppearanceActions(
                        onSetSearchResultLayout = settingsViewModel::setSearchResultLayout,
                        onSetShowHomescreenHint = settingsViewModel::setShowHomescreenHint,
                        onSetShowAppIcons = settingsViewModel::setShowAppIcons
                    ),
                    homeScreen = SettingsHomeScreenActions(
                        onSetHomeTapAction = settingsViewModel::setHomeTapAction,
                        onSetSwipeUpAction = settingsViewModel::setSwipeUpAction,
                        onSetHomeButtonClearsQuery = settingsViewModel::setHomeButtonClearsQuery
                    ),
                    localProviders = SettingsLocalProviderActions(
                        onSetContactsSearchEnabled = settingsViewModel::setContactsSearchEnabled,
                        onSetFilesSearchEnabled = settingsViewModel::setFilesSearchEnabled
                    ),
                    customSources = SettingsCustomSourceActions(
                        onAddSearchSource = settingsViewModel::addSearchSource,
                        onUpdateSearchSource = settingsViewModel::updateSearchSource,
                        onDeleteSearchSource = settingsViewModel::deleteSearchSource,
                        onSetSearchSourceEnabled = settingsViewModel::setSearchSourceEnabled,
                        onAddPrefixToSource = settingsViewModel::addPrefixToSource,
                        onRemovePrefixFromSource = settingsViewModel::removePrefixFromSource
                    ),
                    localPrefixes = SettingsLocalPrefixActions(
                        onAddProviderPrefix = settingsViewModel::addProviderPrefix,
                        onRemoveProviderPrefix = settingsViewModel::removeProviderPrefix,
                        onResetProviderPrefixes = settingsViewModel::resetProviderPrefixes
                    ),
                    advanced = SettingsAdvancedActions(
                        onResetToDefaults = settingsViewModel::resetToDefaults
                    )
                )
            }

            LauncherTheme {
                SettingsScreen(
                    settings = settings,
                    onNavigateBack = { finish() },
                    actions = settingsActions
                )
            }
        }
    }
}
