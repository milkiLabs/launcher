/**
 * SettingsNav.kt - Navigation 3 graph for the settings screen.
 *
 * Settings are grouped into focused pages reached from a single index page:
 *  - Home Screen (trigger actions and target pickers)
 *  - Search (layout, sources, prefixes, and file types)
 *  - Advanced (backup, import, and reset)
 *
 * All routes are serializable NavKeys, allowing the settings back stack to
 * survive configuration changes and process recreation.
 */

package com.milki.launcher.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.backup.LauncherImportResult
import com.milki.launcher.presentation.settings.ImportFileAccessPrompt
import com.milki.launcher.domain.model.targetForTrigger
import kotlinx.serialization.Serializable

/**
 * Typed, saveable routes for the settings navigation graph.
 *
 * [AppPicker] retains the trigger and action that opened it so a selection can
 * be persisted against the correct launcher trigger after state restoration.
 */
@Serializable
sealed interface SettingsRoute : NavKey {
    @Serializable
    data object Index : SettingsRoute

    @Serializable
    data object HomeScreen : SettingsRoute

    @Serializable
    data object Search : SettingsRoute

    @Serializable
    data object Advanced : SettingsRoute

    @Serializable
    data object About : SettingsRoute

    @Serializable
    data class AppPicker(
        val trigger: LauncherTrigger,
        val action: LauncherTriggerAction
    ) : SettingsRoute
}

/**
 * Saveable Navigation 3 host for settings.
 *
 * Back pops nested settings destinations. Back at [SettingsRoute.Index]
 * explicitly exits settings and returns to the application's Home root.
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
    importFileAccessPrompt: ImportFileAccessPrompt?,
    onGrantImportFileAccess: () -> Unit,
    onSkipImportFileAccess: () -> Unit,
    actions: SettingsActions,
    onExitSettings: () -> Unit
) {
    val backStack = rememberNavBackStack(SettingsRoute.Index)

    val current by rememberUpdatedState(
        SettingsNavParams(
            settings = settings,
            installedApps = installedApps,
            actionShortcuts = actionShortcuts,
            showSetDefaultLauncherOption = showSetDefaultLauncherOption,
            backupStatusMessage = backupStatusMessage,
            actions = actions,
            onExitSettings = onExitSettings
        )
    )

    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            current.onExitSettings()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = navigateBack,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        entryProvider = { key ->
            when (key) {
                SettingsRoute.Index -> NavEntry(key) {
                    SettingsIndexScreen(
                        showSetDefaultLauncherOption =
                            current.showSetDefaultLauncherOption,
                        onOpenDefaultLauncherSettings =
                            current.actions.onOpenDefaultLauncherSettings,
                        onOpenHomeScreen = {
                            backStack.add(SettingsRoute.HomeScreen)
                        },
                        onOpenSearch = {
                            backStack.add(SettingsRoute.Search)
                        },
                        onOpenAdvanced = {
                            backStack.add(SettingsRoute.Advanced)
                        },
                        onOpenAbout = {
                            backStack.add(SettingsRoute.About)
                        }
                    )
                }

                SettingsRoute.HomeScreen -> NavEntry(key) {
                    HomeScreenSettingsScreen(
                        settings = current.settings,
                        actions = current.actions.homeScreen,
                        onSelectOpenAppAction = { trigger, action ->
                            backStack.add(
                                SettingsRoute.AppPicker(trigger, action)
                            )
                        },
                        onBack = navigateBack
                    )
                }

                SettingsRoute.Search -> NavEntry(key) {
                    SearchSettingsScreen(
                        settings = current.settings,
                        actions = current.actions,
                        onBack = navigateBack
                    )
                }

                SettingsRoute.Advanced -> NavEntry(key) {
                    AdvancedSettingsScreen(
                        backupStatusMessage = current.backupStatusMessage,
                        onRequestReset =
                            current.actions.advanced.onResetToDefaults,
                        onRequestExport =
                            current.actions.advanced.onExportBackup,
                        onRequestImport =
                            current.actions.advanced.onImportBackup,
                        onShareCrashLogs =
                            current.actions.advanced.onShareCrashLogs,
                        onBack = navigateBack
                    )
                }

                SettingsRoute.About -> NavEntry(key) {
                    AboutSettingsScreen(onBack = navigateBack)
                }

                is SettingsRoute.AppPicker -> NavEntry(key) {
                    val onTargetSelected: (LauncherTriggerTarget) -> Unit =
                        { target ->
                            current.actions.homeScreen
                                .onSetTriggerOpenAppTarget(key.trigger, target)
                            backStack.removeLastOrNull()
                        }

                    if (
                        key.action ==
                        LauncherTriggerAction.OPEN_ACTION_SHORTCUT
                    ) {
                        TriggerActionShortcutPickerScreen(
                            trigger = key.trigger,
                            actionShortcuts = current.actionShortcuts,
                            currentTarget =
                                current.settings.targetForTrigger(key.trigger),
                            onBack = navigateBack,
                            onTargetSelected = onTargetSelected
                        )
                    } else {
                        TriggerAppPickerScreen(
                            trigger = key.trigger,
                            installedApps = current.installedApps,
                            currentTarget =
                                current.settings.targetForTrigger(key.trigger),
                            onBack = navigateBack,
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

    importFileAccessPrompt?.let { prompt ->
        ImportFileAccessDialog(
            pinnedFileCount = prompt.pinnedFileCount,
            pinnedContactCount = prompt.pinnedContactCount,
            onGrant = onGrantImportFileAccess,
            onDismiss = onSkipImportFileAccess
        )
    }
}

/**
 * Immutable snapshot of [SettingsNavHost]'s parameters.
 *
 * NavEntry content lambdas registered with [NavDisplay] may execute long
 * after the recomposition that created them; reading through this hoisted
 * snapshot keeps those captures current without one rememberUpdatedState
 * declaration per parameter.
 */
@Immutable
private data class SettingsNavParams(
    val settings: LauncherSettings,
    val installedApps: List<AppInfo>,
    val actionShortcuts: List<HomeItem.ActionShortcut>,
    val showSetDefaultLauncherOption: Boolean,
    val backupStatusMessage: String?,
    val actions: SettingsActions,
    val onExitSettings: () -> Unit
)
