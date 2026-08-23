package com.milki.launcher.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.actionForTrigger
import com.milki.launcher.domain.model.targetForTrigger
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory

/**
 * Section-level settings UI for the Home Screen page.
 *
 * These composables own only rendering and section-scoped events. Cross-section
 * modal state and navigation live in SettingsNav/SettingsPages.
 */
@Composable
internal fun HomeScreenSection(
    settings: LauncherSettings,
    actions: SettingsHomeScreenActions,
    onSelectOpenAppAction: (LauncherTrigger, LauncherTriggerAction) -> Unit
) {
    SettingsCategory(title = stringResource(R.string.settings_group_home_title))

    LauncherTrigger.entries.forEach { trigger ->
        val action = settings.actionForTrigger(trigger)
        val target = settings.targetForTrigger(trigger)
        DropdownSettingItem(
            title = trigger.displayName,
            subtitle = if (action.requiresTargetPicker) {
                target?.displayName ?: stringResource(R.string.home_trigger_target_unset)
            } else {
                null
            },
            selectedValue = action.displayName,
            options = LauncherTriggerAction.entries
                .map { availableAction -> availableAction.displayName to availableAction },
            onOptionSelected = { selectedAction ->
                if (selectedAction.requiresTargetPicker) {
                    onSelectOpenAppAction(trigger, selectedAction)
                } else {
                    actions.onSetTriggerAction(trigger, selectedAction)
                }
            }
        )
    }
}

private val LauncherTriggerAction.requiresTargetPicker: Boolean
    get() = this == LauncherTriggerAction.OPEN_APP ||
        this == LauncherTriggerAction.OPEN_ACTION_SHORTCUT
