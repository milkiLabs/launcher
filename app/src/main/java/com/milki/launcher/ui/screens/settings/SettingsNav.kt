/**
 * SettingsNav.kt - Navigation 3 graph for the settings screen.
 *
 * Settings are grouped into focused pages reached from a single index page:
 *  - Home Screen (trigger actions and target pickers)
 *  - Search (layout, sources, prefixes, and file types)
 *  - Advanced (backup, import, and reset)
 *
 * Each page is a NavDisplay entry so the system back button and predictive
 * back gesture pop the previous page naturally.
 */

package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.domain.model.targetForTrigger

/**
 * Routes for the settings navigation graph.
 *
 * [AppPicker] carries the trigger/action pair that launched the target
 * picker so the picker can resolve the current target and persist the
 * selection back to the same trigger.
 */
sealed interface SettingsRoute {
    data object Index : SettingsRoute
    data object HomeScreen : SettingsRoute
    data object Search : SettingsRoute
    data object Advanced : SettingsRoute
    data class AppPicker(
        val trigger: LauncherTrigger,
        val action: LauncherTriggerAction
    ) : SettingsRoute
}

/**
 * Root navigation host for the settings screen.
 *
 * @param settings the latest settings snapshot (fresh state is read via
 *   [rememberUpdatedState] so entries cached by the back stack never render
 *   stale data).
 */
@Composable
fun SettingsNavHost(
    settings: LauncherSettings,
    installedApps: List<AppInfo>,
    actionShortcuts: List<HomeItem.ActionShortcut>,
    showSetDefaultLauncherOption: Boolean,
    backupStatusMessage: String?,
    importReport: LauncherImportResult?,
    onDismissImportReport: () -> Unit,
    actions: SettingsActions
) {
    val backStack = remember { mutableStateListOf<Any>(SettingsRoute.Index) }

    val currentSettings by rememberUpdatedState(settings)
    val currentInstalledApps by rememberUpdatedState(installedApps)
    val currentActionShortcuts by rememberUpdatedState(actionShortcuts)
    val currentShowSetDefaultLauncherOption by rememberUpdatedState(showSetDefaultLauncherOption)
    val currentBackupStatusMessage by rememberUpdatedState(backupStatusMessage)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        entryProvider = { key ->
            when (key) {
                is SettingsRoute.Index -> NavEntry(key) {
                    SettingsIndexScreen(
                        showSetDefaultLauncherOption = currentShowSetDefaultLauncherOption,
                        onOpenDefaultLauncherSettings = actions.onOpenDefaultLauncherSettings,
                        onOpenHomeScreen = { backStack.add(SettingsRoute.HomeScreen) },
                        onOpenSearch = { backStack.add(SettingsRoute.Search) },
                        onOpenAdvanced = { backStack.add(SettingsRoute.Advanced) }
                    )
                }

                is SettingsRoute.HomeScreen -> NavEntry(key) {
                    HomeScreenSettingsScreen(
                        settings = currentSettings,
                        actions = actions.homeScreen,
                        onSelectOpenAppAction = { trigger, action ->
                            backStack.add(SettingsRoute.AppPicker(trigger, action))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                is SettingsRoute.Search -> NavEntry(key) {
                    SearchSettingsScreen(
                        settings = currentSettings,
                        actions = actions,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                is SettingsRoute.Advanced -> NavEntry(key) {
                    AdvancedSettingsScreen(
                        backupStatusMessage = currentBackupStatusMessage,
                        onRequestReset = actions.advanced.onResetToDefaults,
                        onRequestExport = actions.advanced.onExportBackup,
                        onRequestImport = actions.advanced.onImportBackup,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                is SettingsRoute.AppPicker -> NavEntry(key) {
                    val onTargetSelected: (LauncherTriggerTarget) -> Unit =
                        { target ->
                            actions.homeScreen.onSetTriggerOpenAppTarget(key.trigger, target)
                            backStack.removeLastOrNull()
                        }
                    if (key.action == LauncherTriggerAction.OPEN_ACTION_SHORTCUT) {
                        TriggerActionShortcutPickerScreen(
                            trigger = key.trigger,
                            actionShortcuts = currentActionShortcuts,
                            currentTarget = currentSettings.targetForTrigger(key.trigger),
                            onBack = { backStack.removeLastOrNull() },
                            onTargetSelected = onTargetSelected
                        )
                    } else {
                        TriggerAppPickerScreen(
                            trigger = key.trigger,
                            installedApps = currentInstalledApps,
                            currentTarget = currentSettings.targetForTrigger(key.trigger),
                            onBack = { backStack.removeLastOrNull() },
                            onTargetSelected = onTargetSelected
                        )
                    }
                }

                else -> error("Unknown settings route: $key")
            }
        }
    )

    if (importReport != null) {
        ImportReportDialog(
            importReport = importReport,
            onDismiss = onDismissImportReport
        )
    }
}
