/**
 * SettingsActivity.kt - Settings entry point for the Milki Launcher
 */

package com.milki.launcher.app.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.presentation.settings.SettingsViewModel
import com.milki.launcher.ui.screens.settings.SettingsActions
import com.milki.launcher.ui.screens.settings.SettingsAdvancedActions
import com.milki.launcher.ui.screens.settings.SettingsCustomSourceActions
import com.milki.launcher.ui.screens.settings.SettingsHomeScreenActions
import com.milki.launcher.ui.screens.settings.SettingsLocalPrefixActions
import com.milki.launcher.ui.screens.settings.SettingsLocalProviderActions
import com.milki.launcher.ui.screens.settings.SettingsSearchBehaviorActions
import com.milki.launcher.ui.screens.settings.SettingsScreen
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

    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                settingsViewModel.exportBackup(uri)
            }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                settingsViewModel.importBackup(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val backupStatusMessage by settingsViewModel.backupStatusMessage.collectAsStateWithLifecycle()
            val importReport by settingsViewModel.lastImportReport.collectAsStateWithLifecycle()
            val settingsActions = remember(settingsViewModel) {
                SettingsActions(
                    searchBehavior = SettingsSearchBehaviorActions(
                        onSetMaxRecentApps = settingsViewModel::setMaxRecentApps
                    ),
                    homeScreen = SettingsHomeScreenActions(
                        onSetHomeTapAction = settingsViewModel::setHomeTapAction,
                        onSetSwipeUpAction = settingsViewModel::setSwipeUpAction,
                        onSetHomeButtonClearsDrawerQuery = settingsViewModel::setHomeButtonClearsDrawerQuery,
                        onSetHomeButtonClearsWidgetPickerQuery = settingsViewModel::setHomeButtonClearsWidgetPickerQuery
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
                        onResetToDefaults = settingsViewModel::resetToDefaults,
                        onExportBackup = {
                            val suggestedName = "launcher-backup-${System.currentTimeMillis()}.json"
                            exportBackupLauncher.launch(suggestedName)
                        },
                        onImportBackup = {
                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                )
            }

            LauncherTheme {
                SettingsScreen(
                    settings = settings,
                    onNavigateBack = { finish() },
                    backupStatusMessage = backupStatusMessage,
                    importReport = importReport,
                    onDismissImportReport = settingsViewModel::clearLastImportReport,
                    actions = settingsActions
                )
            }
        }
    }
}
