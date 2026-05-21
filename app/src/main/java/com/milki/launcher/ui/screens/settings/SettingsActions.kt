package com.milki.launcher.ui.screens.settings

import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget

/**
 * SettingsActions.kt - Grouped action contracts for Settings UI
 *
 * Section-scoped contracts so each composable receives only the actions it uses.
 */

/**
 * Actions used by Home Screen settings section.
 */
data class SettingsHomeScreenActions(
    val onSetTriggerAction: (LauncherTrigger, LauncherTriggerAction) -> Unit,
    val onSetTriggerOpenAppTarget: (LauncherTrigger, LauncherTriggerTarget) -> Unit
)

/**
 * Unified actions for prefix management across all owners (providers and sources).
 */
data class SettingsPrefixActions(
    val onAddPrefix: (String, String, (String) -> Unit) -> Unit,
    val onRemovePrefix: (String, String) -> Unit,
    val onResetPrefixes: (String) -> Unit
)

/**
 * Actions used by search source management section.
 */
data class SettingsSourceActions(
    val onAddSource: (
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) -> Unit,
    val onUpdateSource: (
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String,
        onValidationResult: (String) -> Unit
    ) -> Unit,
    val onDeleteSource: (String) -> Unit,
    val onSetSourceEnabled: (String, Boolean) -> Unit,
    val onSetSourceSuggestedAction: (String, Boolean) -> Unit,
    val onSetDefaultSource: (String?) -> Unit,
    val prefixes: SettingsPrefixActions
)

/**
 * Actions used by advanced settings section.
 */
data class SettingsAdvancedActions(
    val onResetToDefaults: () -> Unit,
    val onExportBackup: () -> Unit,
    val onImportBackup: () -> Unit
)

/**
 * Root grouped action contract consumed by SettingsScreen.
 */
data class SettingsActions(
    val onOpenDefaultLauncherSettings: () -> Unit,
    val homeScreen: SettingsHomeScreenActions,
    val sources: SettingsSourceActions,
    val advanced: SettingsAdvancedActions
)
