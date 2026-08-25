package com.milki.launcher.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.milki.launcher.presentation.settings.SettingsViewModel

/**
 * Assembles the section-scoped [SettingsActions] contracts from a
 * [SettingsViewModel], keeping the wiring out of activity hosts.
 *
 * Backup entry points stay caller-supplied because they route through
 * activity-result launchers owned by the host's coordinator.
 */
@Composable
fun rememberSettingsActions(
    settingsViewModel: SettingsViewModel,
    onOpenDefaultLauncherSettings: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onShareCrashLogs: () -> Unit
): SettingsActions {
    return remember(settingsViewModel) {
        SettingsActions(
            onOpenDefaultLauncherSettings = onOpenDefaultLauncherSettings,
            onSetSearchLayout = settingsViewModel::setSearchLayout,
            homeScreen =
                SettingsHomeScreenActions(
                    onSetTriggerAction =
                        settingsViewModel::setTriggerAction,
                    onSetTriggerOpenAppTarget =
                        settingsViewModel::setTriggerOpenAppTarget
                ),
            sources =
                SettingsSourceActions(
                    onAddSource =
                        settingsViewModel::addSearchSource,
                    onUpdateSource =
                        settingsViewModel::updateSearchSource,
                    onDeleteSource =
                        settingsViewModel::deleteSearchSource,
                    onSetSourceEnabled =
                        settingsViewModel::setSearchSourceEnabled,
                    onSetSourceSuggestedAction =
                        settingsViewModel::setSearchSourceSuggestedAction,
                    onSetDefaultSource =
                        settingsViewModel::setDefaultSearchSource,
                    prefixes =
                        SettingsPrefixActions(
                            onAddPrefix =
                                settingsViewModel::addPrefix,
                            onRemovePrefix =
                                settingsViewModel::removePrefix,
                            onResetPrefixes =
                                settingsViewModel::resetPrefixes
                        )
                ),
            fileSearch =
                SettingsFileSearchActions(
                    onToggleCategory =
                        settingsViewModel::toggleFileSearchCategory,
                    onAddCustomExtension =
                        settingsViewModel::addCustomFileExtension,
                    onRemoveCustomExtension =
                        settingsViewModel::removeCustomFileExtension
                ),
            advanced =
                SettingsAdvancedActions(
                    onResetToDefaults =
                        settingsViewModel::resetToDefaults,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    onShareCrashLogs = onShareCrashLogs
                )
        )
    }
}
