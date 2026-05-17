package com.milki.launcher.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.milki.launcher.data.repository.catchIoException
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchResultLayout
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of [SettingsRepository]. Persistence mapping,
 * diff writing, and search-source mutation rules live in focused collaborators.
 */
class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private val mutationStore = SettingsMutationStore()

    override val settings: Flow<LauncherSettings> = context.settingsDataStore.data
        .catchIoException()
        .map(SettingsPreferenceReader::mapPreferencesToSettings)

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { preferences ->
            val currentSettings = SettingsPreferenceReader.mapPreferencesToSettings(preferences)
            val newSettings = transform(currentSettings)

            SettingsPreferenceWriter.writeSettingsDiffToPreferences(
                currentSettings = currentSettings,
                newSettings = newSettings,
                preferences = preferences
            )
        }
    }

    override suspend fun setMaxSearchResults(value: Int) {
        writeIntSetting(SettingsPreferenceKeys.MAX_SEARCH_RESULTS, value)
    }

    override suspend fun setAutoFocusKeyboard(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.AUTO_FOCUS_KEYBOARD, value)
    }

    override suspend fun setShowRecentApps(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.SHOW_RECENT_APPS, value)
    }

    override suspend fun setCloseSearchOnLaunch(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.CLOSE_SEARCH_ON_LAUNCH, value)
    }

    override suspend fun setSearchResultLayout(layout: SearchResultLayout) {
        writeStringSetting(SettingsPreferenceKeys.SEARCH_RESULT_LAYOUT, layout.name)
    }

    override suspend fun setShowHomescreenHint(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.SHOW_HOMESCREEN_HINT, value)
    }

    override suspend fun setShowAppIcons(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.SHOW_APP_ICONS, value)
    }

    override suspend fun setTriggerAction(
        trigger: LauncherTrigger,
        action: LauncherTriggerAction
    ) {
        context.settingsDataStore.edit { preferences ->
            val currentActions = SettingsPreferenceReader.parseTriggerActions(preferences)
            val currentAction =
                currentActions[trigger] ?: LauncherInteractionCatalog.defaultActionFor(trigger)
            if (currentAction == action) {
                return@edit
            }
            val updatedActions = currentActions + (trigger to action)
            SettingsPreferenceWriter.writeTriggerActions(updatedActions, preferences)
            if (action != LauncherTriggerAction.OPEN_APP && action != LauncherTriggerAction.OPEN_ACTION_SHORTCUT) {
                val updatedTargets = SettingsPreferenceReader.parseTriggerTargets(preferences) - trigger
                SettingsPreferenceWriter.writeTriggerTargets(updatedTargets, preferences)
            }
        }
    }

    override suspend fun setTriggerOpenAppTarget(
        trigger: LauncherTrigger,
        target: LauncherTriggerTarget
    ) {
        context.settingsDataStore.edit { preferences ->
            val newAction = if (target is LauncherTriggerTarget.ActionShortcut) {
                LauncherTriggerAction.OPEN_ACTION_SHORTCUT
            } else {
                LauncherTriggerAction.OPEN_APP
            }
            val updatedActions = SettingsPreferenceReader.parseTriggerActions(preferences) + (trigger to newAction)
            val updatedTargets = SettingsPreferenceReader.parseTriggerTargets(preferences) + (trigger to target)
            SettingsPreferenceWriter.writeTriggerActions(updatedActions, preferences)
            SettingsPreferenceWriter.writeTriggerTargets(updatedTargets, preferences)
        }
    }

    override suspend fun setContactsSearchEnabled(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED, value)
    }

    override suspend fun setFilesSearchEnabled(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.FILES_SEARCH_ENABLED, value)
    }

    override suspend fun addSearchSource(source: SearchSource): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.Success

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.addSearchSource(
                preferences = preferences,
                source = source
            )
        }

        return result
    }

    override suspend fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.updateSearchSource(
                preferences = preferences,
                sourceId = sourceId,
                name = name,
                urlTemplate = urlTemplate,
                prefixes = prefixes,
                accentColorHex = accentColorHex
            )
        }

        return result
    }

    override suspend fun deleteSearchSource(sourceId: String) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.deleteSearchSource(
                preferences = preferences,
                sourceId = sourceId
            )
        }
    }

    override suspend fun setSearchSourceEnabled(sourceId: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.setSearchSourceEnabled(
                preferences = preferences,
                sourceId = sourceId,
                enabled = enabled
            )
        }
    }

    override suspend fun setSearchSourceSuggestedAction(sourceId: String, showAsSuggestedAction: Boolean) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.setSearchSourceSuggestedAction(
                preferences = preferences,
                sourceId = sourceId,
                showAsSuggestedAction = showAsSuggestedAction
            )
        }
    }

    override suspend fun setDefaultSearchSourceId(sourceId: String?) {
        context.settingsDataStore.edit { preferences ->
            if (sourceId == null) {
                preferences.remove(SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID)
            } else {
                preferences[SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID] = sourceId
            }
        }
    }

    override suspend fun setProviderPrefixes(
        providerId: String,
        prefixes: List<String>
    ): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.setProviderPrefixes(
                preferences = preferences,
                providerId = providerId,
                prefixes = prefixes
            )
        }

        return result
    }

    override suspend fun addProviderPrefix(
        providerId: String,
        prefix: String,
        defaultPrefix: String
    ): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.addProviderPrefix(
                preferences = preferences,
                providerId = providerId,
                prefix = prefix,
                defaultPrefix = defaultPrefix
            )
        }

        return result
    }

    override suspend fun removeProviderPrefix(providerId: String, prefix: String) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.removeProviderPrefix(
                preferences = preferences,
                providerId = providerId,
                prefix = prefix
            )
        }
    }

    override suspend fun resetProviderPrefixes(providerId: String) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.resetProviderPrefixes(
                preferences = preferences,
                providerId = providerId
            )
        }
    }

    override suspend fun resetAllPrefixConfigurations() {
        context.settingsDataStore.edit { preferences ->
            mutationStore.resetAllPrefixConfigurations(preferences)
        }
    }

    override suspend fun toggleHiddenApp(packageName: String) {
        context.settingsDataStore.edit { preferences ->
            val currentHiddenApps =
                (preferences[SettingsPreferenceKeys.HIDDEN_APPS] ?: emptySet()).toMutableSet()

            if (packageName in currentHiddenApps) {
                currentHiddenApps.remove(packageName)
            } else {
                currentHiddenApps.add(packageName)
            }

            preferences[SettingsPreferenceKeys.HIDDEN_APPS] = currentHiddenApps
        }
    }

    override suspend fun setAllPrefixConfigurations(configurations: ProviderPrefixConfiguration) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.setAllPrefixConfigurations(
                preferences = preferences,
                configurations = configurations
            )
        }
    }

    override suspend fun addPrefixToSource(
        sourceId: String,
        prefix: String
    ): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.addPrefixToSource(
                preferences = preferences,
                sourceId = sourceId,
                prefix = prefix
            )
        }

        return result
    }

    override suspend fun removePrefixFromSource(
        sourceId: String,
        prefix: String
    ): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.removePrefixFromSource(
                preferences = preferences,
                sourceId = sourceId,
                prefix = prefix
            )
        }

        return result
    }

    private suspend fun writeBooleanSetting(
        key: Preferences.Key<Boolean>,
        value: Boolean
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private suspend fun writeIntSetting(
        key: Preferences.Key<Int>,
        value: Int
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private suspend fun writeStringSetting(
        key: Preferences.Key<String>,
        value: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
