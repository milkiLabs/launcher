package com.milki.launcher.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.milki.launcher.data.repository.common.catchIoException
import com.milki.launcher.data.repository.common.mutate
import com.milki.launcher.data.repository.common.settingsDataStore
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
 * DataStore-backed implementation of all focused settings interfaces
 * ([SettingsReader], [SearchSourceRepository], [PrefixOwnerRepository],
 * [HomeTriggerRepository]).
 *
 * DI intentionally binds this single instance under each interface separately
 * (see coreModule) so consumers depend only on the focused surface they need.
 *
 * Persistence codecs live in SettingsStorage.kt; prefix/source mutation
 * rules live in SettingsMutationStore.
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
        .map(Preferences::toLauncherSettings)

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences.writeSettings(transform(preferences.toLauncherSettings()))
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
            val currentActions = preferences.parseTriggerActions()
            val currentAction =
                currentActions[trigger] ?: LauncherInteractionCatalog.defaultActionFor(trigger)
            if (currentAction == action) {
                return@edit
            }
            writeTriggerActions(currentActions + (trigger to action), preferences)
            if (action != LauncherTriggerAction.OPEN_APP && action != LauncherTriggerAction.OPEN_ACTION_SHORTCUT) {
                val updatedTargets = preferences.parseTriggerTargets() - trigger
                writeTriggerTargets(updatedTargets, preferences)
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
            val updatedActions = preferences.parseTriggerActions() + (trigger to newAction)
            val updatedTargets = preferences.parseTriggerTargets() + (trigger to target)
            writeTriggerActions(updatedActions, preferences)
            writeTriggerTargets(updatedTargets, preferences)
        }
    }

    // ========================================================================
    // SearchSourceRepository
    // ========================================================================

    override suspend fun addSearchSource(source: SearchSource): PrefixMutationResult {
        return context.settingsDataStore.mutate { preferences ->
            mutationStore.addSearchSource(preferences, source)
        }
    }

    override suspend fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ): PrefixMutationResult {
        return context.settingsDataStore.mutate { preferences ->
            mutationStore.updateSearchSource(
                preferences, sourceId, name, urlTemplate, prefixes, accentColorHex
            )
        }
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
        return context.settingsDataStore.mutate { preferences ->
            mutationStore.addPrefix(preferences, ownerId, prefix)
        }
    }

    override suspend fun removePrefix(ownerId: String, prefix: String): PrefixMutationResult {
        return context.settingsDataStore.mutate { preferences ->
            mutationStore.removePrefix(preferences, ownerId, prefix)
        }
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
}
