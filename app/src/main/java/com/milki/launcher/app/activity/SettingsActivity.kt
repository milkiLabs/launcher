/**
 * SettingsActivity.kt - Settings entry point for the Milki Launcher
 */

package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milki.launcher.core.launcher.isAppDefaultLauncher
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

    private val requestHomeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val roleManager = homeRoleManagerOrNull()
            val granted =
                result.resultCode == RESULT_OK ||
                    (roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true)
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
        val roleManager = homeRoleManagerOrNull() ?: return false
        val canRequestHomeRole =
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        if (!canRequestHomeRole) {
            return false
        }

        return runCatching {
            requestHomeRoleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            )
            true
        }.getOrDefault(false)
    }

    private fun homeRoleManagerOrNull(): RoleManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return getSystemService(RoleManager::class.java)
    }

    private fun openDefaultLauncherSettingsFallback() {
        if (tryStartActivity(Intent(Settings.ACTION_HOME_SETTINGS))) {
            return
        }

        if (tryStartActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
            return
        }

        tryStartActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        )
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
