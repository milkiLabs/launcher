package com.milki.launcher.ui.screens.settings

import com.milki.launcher.domain.model.HomeTapAction
import com.milki.launcher.domain.model.SwipeUpAction

/**
 * SettingsActions.kt - Grouped action contracts for Settings UI
 *
 * WHY THIS FILE EXISTS:
 * The original SettingsScreen accepted a very long list of callback parameters.
 * That approach made the screen signature noisy and increased coupling between
 * every section and every callback, even when a section only needed a subset.
 *
 * This file introduces section-scoped contracts so each composable receives only
 * the actions it actually uses.
 */

/**
 * Actions used by Search Behavior settings section.
 */
data class SettingsSearchBehaviorActions(
    val onSetMaxRecentApps: (Int) -> Unit
)

/**
 * Actions used by Home Screen settings section.
 */
data class SettingsHomeScreenActions(
    val onSetHomeTapAction: (HomeTapAction) -> Unit,
    val onSetSwipeUpAction: (SwipeUpAction) -> Unit,
    val onSetHomeButtonClearsSearchDialogQuery: (Boolean) -> Unit,
    val onSetHomeButtonClearsDrawerQuery: (Boolean) -> Unit,
    val onSetHomeButtonClearsWidgetPickerQuery: (Boolean) -> Unit
)

/**
 * Actions used by local-provider on/off switches.
 */
data class SettingsLocalProviderActions(
    val onSetContactsSearchEnabled: (Boolean) -> Unit,
    val onSetFilesSearchEnabled: (Boolean) -> Unit
)

/**
 * Actions used by dynamic/custom source management section.
 */
data class SettingsCustomSourceActions(
    val onAddSearchSource: (
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ) -> Unit,
    val onUpdateSearchSource: (
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ) -> Unit,
    val onDeleteSearchSource: (String) -> Unit,
    val onSetSearchSourceEnabled: (String, Boolean) -> Unit,
    val onAddPrefixToSource: (String, String, (String) -> Unit) -> Unit,
    val onRemovePrefixFromSource: (String, String) -> Unit
)

/**
 * Actions used by local-provider prefix editing section.
 */
data class SettingsLocalPrefixActions(
    val onAddProviderPrefix: (String, String) -> Unit,
    val onRemoveProviderPrefix: (String, String) -> Unit,
    val onResetProviderPrefixes: (String) -> Unit
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
    val searchBehavior: SettingsSearchBehaviorActions,
    val homeScreen: SettingsHomeScreenActions,
    val localProviders: SettingsLocalProviderActions,
    val customSources: SettingsCustomSourceActions,
    val localPrefixes: SettingsLocalPrefixActions,
    val advanced: SettingsAdvancedActions
)
