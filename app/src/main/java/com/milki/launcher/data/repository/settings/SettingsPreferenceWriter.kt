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
        writeScalarSettings(currentSettings, newSettings, preferences)
        writeSearchSettings(currentSettings, newSettings, preferences)
        writeInteractionSettings(currentSettings, newSettings, preferences)

        if (currentSettings.hiddenApps != newSettings.hiddenApps) {
            preferences[SettingsPreferenceKeys.HIDDEN_APPS] = newSettings.hiddenApps
        }

        if (currentSettings.defaultSearchSourceId != newSettings.defaultSearchSourceId) {
            val newId = newSettings.defaultSearchSourceId
            if (newId == null) {
                preferences.remove(SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID)
            } else {
                preferences[SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID] = newId
            }
        }
    }

    private fun writeScalarSettings(
        current: LauncherSettings,
        updated: LauncherSettings,
        preferences: MutablePreferences
    ) {
        if (current.maxSearchResults != updated.maxSearchResults) {
            preferences[SettingsPreferenceKeys.MAX_SEARCH_RESULTS] = updated.maxSearchResults
        }
        if (current.autoFocusKeyboard != updated.autoFocusKeyboard) {
            preferences[SettingsPreferenceKeys.AUTO_FOCUS_KEYBOARD] = updated.autoFocusKeyboard
        }
        if (current.showRecentApps != updated.showRecentApps) {
            preferences[SettingsPreferenceKeys.SHOW_RECENT_APPS] = updated.showRecentApps
        }
        if (current.closeSearchOnLaunch != updated.closeSearchOnLaunch) {
            preferences[SettingsPreferenceKeys.CLOSE_SEARCH_ON_LAUNCH] = updated.closeSearchOnLaunch
        }
        if (current.searchResultLayout != updated.searchResultLayout) {
            preferences[SettingsPreferenceKeys.SEARCH_RESULT_LAYOUT] = updated.searchResultLayout.name
        }
        if (current.showHomescreenHint != updated.showHomescreenHint) {
            preferences[SettingsPreferenceKeys.SHOW_HOMESCREEN_HINT] = updated.showHomescreenHint
        }
        if (current.showAppIcons != updated.showAppIcons) {
            preferences[SettingsPreferenceKeys.SHOW_APP_ICONS] = updated.showAppIcons
        }
        if (current.contactsSearchEnabled != updated.contactsSearchEnabled) {
            preferences[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED] = updated.contactsSearchEnabled
        }
        if (current.filesSearchEnabled != updated.filesSearchEnabled) {
            preferences[SettingsPreferenceKeys.FILES_SEARCH_ENABLED] = updated.filesSearchEnabled
        }
    }

    private fun writeSearchSettings(
        current: LauncherSettings,
        updated: LauncherSettings,
        preferences: MutablePreferences
    ) {
        if (current.searchSources != updated.searchSources) {
            writeSearchSources(updated.searchSources, preferences)
        }
        if (current.prefixConfigurations != updated.prefixConfigurations) {
            writePrefixConfigurations(updated.prefixConfigurations, preferences)
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

    private fun writePrefixConfigurations(
        configurations: ProviderPrefixConfiguration,
        preferences: MutablePreferences
    ) {
        if (configurations.isEmpty()) {
            preferences.remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
            return
        }

        preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] =
            serializePrefixConfigurations(configurations)
    }

    private fun writeSearchSources(
        sources: List<SearchSource>,
        preferences: MutablePreferences
    ) {
        preferences[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] =
            SearchSourcesStorageState.INITIALIZED
        preferences[SettingsPreferenceKeys.SEARCH_SOURCES] =
            serializeSearchSources(sources)
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
