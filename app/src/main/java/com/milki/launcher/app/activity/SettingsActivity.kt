/**
 * SettingsActivity.kt - Settings entry point for the Milki Launcher
 */

package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.coroutines.resume
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.core.launcher.isAppDefaultLauncher
import com.milki.launcher.core.launcher.launchHomeRoleRequestIfNeeded
import com.milki.launcher.core.launcher.openDefaultLauncherSettingsFallback
import com.milki.launcher.core.launcher.syncLauncherIconVisibility
import com.milki.launcher.presentation.settings.SettingsViewModel
import com.milki.launcher.ui.screens.settings.SettingsActions
import com.milki.launcher.ui.screens.settings.SettingsAdvancedActions
import com.milki.launcher.ui.screens.settings.SettingsCustomSourceActions
import com.milki.launcher.ui.screens.settings.SettingsHomeScreenActions
import com.milki.launcher.ui.screens.settings.SettingsLocalPrefixActions
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

    private var isDefaultLauncher by mutableStateOf(false)
    private var pendingWidgetPermissionResult: ((Boolean) -> Unit)? = null

    private val requestWidgetBindPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pendingWidgetPermissionResult?.invoke(result.resultCode == RESULT_OK)
            pendingWidgetPermissionResult = null
        }

    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                settingsViewModel.exportBackup(uri)
            }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                settingsViewModel.importBackup(uri) { bindIntent ->
                    awaitActivityResult(
                        launcher = requestWidgetBindPermissionLauncher,
                        intent = bindIntent
                    )
                }
            }
        }

    private val requestHomeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted =
                result.resultCode == RESULT_OK ||
                        isAppDefaultLauncher(this)
            if (!granted) {
                openDefaultLauncherSettingsFallback()
            }
            refreshLauncherDefaultState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshLauncherDefaultState()

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val backupStatusMessage by settingsViewModel.backupStatusMessage.collectAsStateWithLifecycle()
            val importReport by settingsViewModel.lastImportReport.collectAsStateWithLifecycle()
            val settingsActions = remember(settingsViewModel) {
                SettingsActions(
                    onOpenDefaultLauncherSettings = ::openDefaultLauncherSettings,
                    homeScreen = SettingsHomeScreenActions(
                        onSetTriggerAction = settingsViewModel::setTriggerAction
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
                        onSetContactsSearchEnabled = settingsViewModel::setContactsSearchEnabled,
                        onSetFilesSearchEnabled = settingsViewModel::setFilesSearchEnabled,
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
                    showSetDefaultLauncherOption = !isDefaultLauncher,
                    backupStatusMessage = backupStatusMessage,
                    importReport = importReport,
                    onDismissImportReport = settingsViewModel::clearLastImportReport,
                    actions = settingsActions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLauncherDefaultState()
    }

    private fun refreshLauncherDefaultState() {
        isDefaultLauncher = isAppDefaultLauncher(this)
        syncLauncherIconVisibility(this)
    }

    private fun openDefaultLauncherSettings() {
        if (launchHomeRoleRequestIfNeeded()) {
            return
        }

        openDefaultLauncherSettingsFallback()
    }

    private fun launchHomeRoleRequestIfNeeded(): Boolean {
        return launchHomeRoleRequestIfNeeded(requestHomeRoleLauncher)
    }

    private suspend fun awaitActivityResult(
        launcher: ActivityResultLauncher<Intent>,
        intent: Intent
    ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        pendingWidgetPermissionResult = { granted ->
            if (continuation.isActive) {
                continuation.resume(granted)
            }
        }

        runCatching {
            launcher.launch(intent)
        }.onFailure {
            pendingWidgetPermissionResult = null
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }

        continuation.invokeOnCancellation {
            pendingWidgetPermissionResult = null
        }
    }

}
