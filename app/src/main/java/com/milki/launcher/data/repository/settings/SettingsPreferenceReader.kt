package com.milki.launcher.data.repository.settings

import androidx.datastore.preferences.core.Preferences
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.SearchResultLayout
import com.milki.launcher.domain.model.SearchSource
import kotlinx.serialization.decodeFromString

internal object SettingsPreferenceReader {
    fun mapPreferencesToSettings(preferences: Preferences): LauncherSettings {
        val defaults = LauncherSettings()
        val parsedPrefixConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])

        return LauncherSettings(
            maxSearchResults =
                preferences[SettingsPreferenceKeys.MAX_SEARCH_RESULTS] ?: defaults.maxSearchResults,
            autoFocusKeyboard =
                preferences[SettingsPreferenceKeys.AUTO_FOCUS_KEYBOARD] ?: defaults.autoFocusKeyboard,
            showRecentApps =
                preferences[SettingsPreferenceKeys.SHOW_RECENT_APPS] ?: defaults.showRecentApps,
            closeSearchOnLaunch =
                preferences[SettingsPreferenceKeys.CLOSE_SEARCH_ON_LAUNCH]
                    ?: defaults.closeSearchOnLaunch,
            searchResultLayout =
                preferences[SettingsPreferenceKeys.SEARCH_RESULT_LAYOUT]?.let {
                    runCatching { SearchResultLayout.valueOf(it) }
                        .getOrDefault(defaults.searchResultLayout)
                } ?: defaults.searchResultLayout,
            showHomescreenHint =
                preferences[SettingsPreferenceKeys.SHOW_HOMESCREEN_HINT]
                    ?: defaults.showHomescreenHint,
            showAppIcons =
                preferences[SettingsPreferenceKeys.SHOW_APP_ICONS] ?: defaults.showAppIcons,
            triggerActions = parseTriggerActions(preferences),
            triggerTargets = parseTriggerTargets(preferences),
            contactsSearchEnabled =
                preferences[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED]
                    ?: defaults.contactsSearchEnabled,
            filesSearchEnabled =
                preferences[SettingsPreferenceKeys.FILES_SEARCH_ENABLED]
                    ?: defaults.filesSearchEnabled,
            searchSources = parseSearchSources(preferences),
            prefixConfigurations = parsedPrefixConfigurations,
            hiddenApps = preferences[SettingsPreferenceKeys.HIDDEN_APPS] ?: defaults.hiddenApps,
            defaultSearchSourceId = preferences[SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID]
        )
    }

    private fun parseSearchSources(preferences: Preferences): List<SearchSource> {
        val isInitialized =
            preferences[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] ==
                SearchSourcesStorageState.INITIALIZED

        return parseSearchSources(
            json = preferences[SettingsPreferenceKeys.SEARCH_SOURCES],
            isInitialized = isInitialized
        )
    }

    fun parseTriggerActions(preferences: Preferences): Map<LauncherTrigger, LauncherTriggerAction> {
        val storedJson = preferences[SettingsPreferenceKeys.TRIGGER_ACTIONS]
        if (!storedJson.isNullOrBlank()) {
            val parsed = runCatching {
                val decoded: SerializedTriggerActions = settingsStorageJson.decodeFromString(storedJson)
                decoded.mapNotNull { (triggerName, actionName) ->
                    val trigger = runCatching { LauncherTrigger.valueOf(triggerName) }.getOrNull()
                    val action = runCatching { LauncherTriggerAction.valueOf(actionName) }.getOrNull()
                    if (trigger == null || action == null) null else trigger to action
                }.toMap()
            }.getOrDefault(emptyMap())

            return mergeWithDefaultTriggerActions(parsed)
        }

        return mergeWithDefaultTriggerActions(parseLegacyTriggerActions(preferences))
    }

    fun parseTriggerTargets(preferences: Preferences): Map<LauncherTrigger, LauncherTriggerTarget> {
        val storedJson = preferences[SettingsPreferenceKeys.TRIGGER_TARGETS]
        if (storedJson.isNullOrBlank()) return emptyMap()

        return runCatching {
            val decoded: SerializedTriggerTargets = settingsStorageJson.decodeFromString(storedJson)
            decoded.mapNotNull { (triggerName, target) ->
                val trigger = runCatching { LauncherTrigger.valueOf(triggerName) }.getOrNull()
                if (trigger == null) null else trigger to target
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun parseLegacyTriggerActions(preferences: Preferences): Map<LauncherTrigger, LauncherTriggerAction> {
        val legacyMappings = mutableMapOf<LauncherTrigger, LauncherTriggerAction>()
        preferences[SettingsPreferenceKeys.HOME_TAP_ACTION]?.let { storedActionName ->
            runCatching { LauncherTriggerAction.valueOf(storedActionName) }
                .getOrNull()
                ?.let { legacyMappings[LauncherTrigger.HOME_TAP] = it }
        }
        preferences[SettingsPreferenceKeys.SWIPE_UP_ACTION]?.let { storedActionName ->
            runCatching { LauncherTriggerAction.valueOf(storedActionName) }
                .getOrNull()
                ?.let { legacyMappings[LauncherTrigger.HOME_SWIPE_UP] = it }
        }
        return legacyMappings
    }

    private fun mergeWithDefaultTriggerActions(
        overrides: Map<LauncherTrigger, LauncherTriggerAction>
    ): Map<LauncherTrigger, LauncherTriggerAction> {
        return LauncherInteractionCatalog.configurableTriggers.associateWith { trigger ->
            overrides[trigger] ?: LauncherInteractionCatalog.defaultActionFor(trigger)
        }
    }
}
