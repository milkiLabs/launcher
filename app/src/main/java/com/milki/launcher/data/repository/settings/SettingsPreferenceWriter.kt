package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchSource
import kotlinx.serialization.encodeToString

internal object SettingsPreferenceWriter {
    fun writeSettingsDiffToPreferences(
        currentSettings: LauncherSettings,
        newSettings: LauncherSettings,
        preferences: MutablePreferences
    ) {
        writeSearchSettings(currentSettings, newSettings, preferences)
        writeInteractionSettings(currentSettings, newSettings, preferences)

        if (currentSettings.defaultSearchSourceId != newSettings.defaultSearchSourceId) {
            val newId = newSettings.defaultSearchSourceId
            if (newId == null) {
                preferences.remove(SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID)
            } else {
                preferences[SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID] = newId
            }
        }
    }

    private fun writeSearchSettings(
        current: LauncherSettings,
        updated: LauncherSettings,
        preferences: MutablePreferences
    ) {
        if (current.contactsSearchEnabled != updated.contactsSearchEnabled) {
            preferences[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED] = updated.contactsSearchEnabled
        }
        if (current.filesSearchEnabled != updated.filesSearchEnabled) {
            preferences[SettingsPreferenceKeys.FILES_SEARCH_ENABLED] = updated.filesSearchEnabled
        }
        if (current.searchSources != updated.searchSources) {
            preferences.writeSearchSources(updated.searchSources)
        }
        if (current.prefixConfigurations != updated.prefixConfigurations) {
            preferences.writePrefixConfigurations(updated.prefixConfigurations)
        }
    }

    private fun writeInteractionSettings(
        current: LauncherSettings,
        updated: LauncherSettings,
        preferences: MutablePreferences
    ) {
        if (current.triggerActions != updated.triggerActions) {
            writeTriggerActions(updated.triggerActions, preferences)
        }
        if (current.triggerTargets != updated.triggerTargets) {
            writeTriggerTargets(updated.triggerTargets, preferences)
        }
    }

    fun writeTriggerActions(
        triggerActions: Map<LauncherTrigger, LauncherTriggerAction>,
        preferences: MutablePreferences
    ) {
        val normalized = mergeWithDefaultTriggerActions(triggerActions)
        val serialized: SerializedTriggerActions = normalized
            .mapKeys { (trigger, _) -> trigger.name }
            .mapValues { (_, action) -> action.name }

        preferences[SettingsPreferenceKeys.TRIGGER_ACTIONS] =
            settingsStorageJson.encodeToString(serialized)
    }

    fun writeTriggerTargets(
        triggerTargets: Map<LauncherTrigger, LauncherTriggerTarget>,
        preferences: MutablePreferences
    ) {
        if (triggerTargets.isEmpty()) {
            preferences.remove(SettingsPreferenceKeys.TRIGGER_TARGETS)
            return
        }

        val serialized: SerializedTriggerTargets = triggerTargets
            .mapKeys { (trigger, _) -> trigger.name }

        preferences[SettingsPreferenceKeys.TRIGGER_TARGETS] =
            settingsStorageJson.encodeToString(serialized)
    }

    private fun mergeWithDefaultTriggerActions(
        overrides: Map<LauncherTrigger, LauncherTriggerAction>
    ): Map<LauncherTrigger, LauncherTriggerAction> {
        return LauncherInteractionCatalog.configurableTriggers.associateWith { trigger ->
            overrides[trigger] ?: LauncherInteractionCatalog.defaultActionFor(trigger)
        }
    }
}
