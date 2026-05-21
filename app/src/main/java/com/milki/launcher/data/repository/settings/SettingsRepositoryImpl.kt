package com.milki.launcher.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.milki.launcher.data.repository.common.catchIoException
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.PrefixMutationResult
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.repository.HomeTriggerRepository
import com.milki.launcher.domain.repository.PrefixOwnerRepository
import com.milki.launcher.domain.repository.SearchSourceRepository
import com.milki.launcher.domain.repository.SettingsReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of all focused settings interfaces.
 *
 * Persistence mapping, diff writing, and mutation rules
 * live in focused collaborators.
 */
class SettingsRepositoryImpl(
    private val context: Context
) : SettingsReader,
    SearchSourceRepository,
    PrefixOwnerRepository,
    HomeTriggerRepository {

    private val mutationStore = SettingsMutationStore()

    // ========================================================================
    // SettingsReader
    // ========================================================================

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

    // ========================================================================
    // HomeTriggerRepository
    // ========================================================================

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

    // ========================================================================
    // SearchSourceRepository
    // ========================================================================

    override suspend fun addSearchSource(source: SearchSource): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.Success
        context.settingsDataStore.edit { preferences ->
            result = mutationStore.addSearchSource(preferences, source)
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
                preferences, sourceId, name, urlTemplate, prefixes, accentColorHex
            )
        }
        return result
    }

    override suspend fun deleteSearchSource(sourceId: String) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.deleteSearchSource(preferences, sourceId)
        }
    }

    override suspend fun setSearchSourceEnabled(sourceId: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.setSearchSourceEnabled(preferences, sourceId, enabled)
        }
    }

    override suspend fun setSearchSourceSuggestedAction(sourceId: String, showAsSuggestedAction: Boolean) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.setSearchSourceSuggestedAction(preferences, sourceId, showAsSuggestedAction)
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

    // ========================================================================
    // PrefixOwnerRepository
    // ========================================================================

    override suspend fun addPrefix(ownerId: String, prefix: String): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound
        context.settingsDataStore.edit { preferences ->
            result = mutationStore.addPrefix(preferences, ownerId, prefix)
        }
        return result
    }

    override suspend fun removePrefix(ownerId: String, prefix: String): PrefixMutationResult {
        var result: PrefixMutationResult = PrefixMutationResult.TargetNotFound
        context.settingsDataStore.edit { preferences ->
            result = mutationStore.removePrefix(preferences, ownerId, prefix)
        }
        return result
    }

    override suspend fun resetPrefixes(ownerId: String) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.resetPrefixes(preferences, ownerId)
        }
    }

    override suspend fun resetAllPrefixes() {
        context.settingsDataStore.edit { preferences ->
            mutationStore.resetAllPrefixes(preferences)
        }
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private suspend fun writeBooleanSetting(
        key: Preferences.Key<Boolean>,
        value: Boolean
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
