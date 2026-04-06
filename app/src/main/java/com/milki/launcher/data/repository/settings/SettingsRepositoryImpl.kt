package com.milki.launcher.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.milki.launcher.domain.model.HomeTapAction
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchResultLayout
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.SourcePrefixMutationResult
import com.milki.launcher.domain.model.SwipeUpAction
import com.milki.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.IOException

/**
 * DataStore-backed implementation of [SettingsRepository].
 *
 * This repository now delegates mutation-heavy search-source and prefix update
 * logic to [SettingsMutationStore], while keeping repository responsibilities
 * focused on:
 * - exposing the settings flow
 * - generic snapshot transforms
 * - simple single-key writes
 * - mapping preferences to domain settings
 */
class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private val mutationStore = SettingsMutationStore()

    override val settings: Flow<LauncherSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::mapPreferencesToSettings)

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { preferences ->
            val currentSettings = mapPreferencesToSettings(preferences)
            val newSettings = transform(currentSettings)

            writeSettingsDiffToPreferences(
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

    override suspend fun setMaxRecentApps(value: Int) {
        writeIntSetting(SettingsPreferenceKeys.MAX_RECENT_APPS, value)
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

    override suspend fun setHomeTapAction(action: HomeTapAction) {
        writeStringSetting(SettingsPreferenceKeys.HOME_TAP_ACTION, action.name)
    }

    override suspend fun setSwipeUpAction(action: SwipeUpAction) {
        writeStringSetting(SettingsPreferenceKeys.SWIPE_UP_ACTION, action.name)
    }

    override suspend fun setContactsSearchEnabled(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED, value)
    }

    override suspend fun setFilesSearchEnabled(value: Boolean) {
        writeBooleanSetting(SettingsPreferenceKeys.FILES_SEARCH_ENABLED, value)
    }

    override suspend fun addSearchSource(source: SearchSource) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.addSearchSource(
                preferences = preferences,
                source = source
            )
        }
    }

    override suspend fun updateSearchSource(
        sourceId: String,
        name: String,
        urlTemplate: String,
        prefixes: List<String>,
        accentColorHex: String
    ) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.updateSearchSource(
                preferences = preferences,
                sourceId = sourceId,
                name = name,
                urlTemplate = urlTemplate,
                prefixes = prefixes,
                accentColorHex = accentColorHex
            )
        }
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

    override suspend fun setProviderPrefixes(providerId: String, prefixes: List<String>) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.setProviderPrefixes(
                preferences = preferences,
                providerId = providerId,
                prefixes = prefixes
            )
        }
    }

    override suspend fun addProviderPrefix(providerId: String, prefix: String, defaultPrefix: String) {
        context.settingsDataStore.edit { preferences ->
            mutationStore.addProviderPrefix(
                preferences = preferences,
                providerId = providerId,
                prefix = prefix,
                defaultPrefix = defaultPrefix
            )
        }
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
    ): SourcePrefixMutationResult {
        var result: SourcePrefixMutationResult = SourcePrefixMutationResult.SourceNotFound

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
    ): SourcePrefixMutationResult {
        var result: SourcePrefixMutationResult = SourcePrefixMutationResult.SourceNotFound

        context.settingsDataStore.edit { preferences ->
            result = mutationStore.removePrefixFromSource(
                preferences = preferences,
                sourceId = sourceId,
                prefix = prefix
            )
        }

        return result
    }

    /**
     * Maps DataStore [Preferences] into the domain-level [LauncherSettings].
     *
     * Parsing/normalization for source and prefix persistence now lives in the
     * dedicated settings mutation/store layer. The repository keeps only the
     * read-side mapping needed to expose the settings flow.
     */
    private fun mapPreferencesToSettings(preferences: Preferences): LauncherSettings {
        val defaults = LauncherSettings()
        val parsedPrefixConfigurations =
            parsePrefixConfigurations(preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS])
        val parsedSearchSources = parseSearchSources(preferences)

        return LauncherSettings(
            maxSearchResults =
                preferences[SettingsPreferenceKeys.MAX_SEARCH_RESULTS] ?: defaults.maxSearchResults,
            autoFocusKeyboard =
                preferences[SettingsPreferenceKeys.AUTO_FOCUS_KEYBOARD] ?: defaults.autoFocusKeyboard,
            showRecentApps =
                preferences[SettingsPreferenceKeys.SHOW_RECENT_APPS] ?: defaults.showRecentApps,
            maxRecentApps =
                preferences[SettingsPreferenceKeys.MAX_RECENT_APPS] ?: defaults.maxRecentApps,
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

            homeTapAction =
                preferences[SettingsPreferenceKeys.HOME_TAP_ACTION]?.let {
                    runCatching { HomeTapAction.valueOf(it) }
                        .getOrDefault(defaults.homeTapAction)
                } ?: defaults.homeTapAction,
            swipeUpAction =
                preferences[SettingsPreferenceKeys.SWIPE_UP_ACTION]?.let {
                    runCatching { SwipeUpAction.valueOf(it) }
                        .getOrDefault(defaults.swipeUpAction)
                } ?: defaults.swipeUpAction,

            contactsSearchEnabled =
                preferences[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED]
                    ?: defaults.contactsSearchEnabled,
            filesSearchEnabled =
                preferences[SettingsPreferenceKeys.FILES_SEARCH_ENABLED]
                    ?: defaults.filesSearchEnabled,

            searchSources = parsedSearchSources,
            prefixConfigurations = parsedPrefixConfigurations,
            hiddenApps = preferences[SettingsPreferenceKeys.HIDDEN_APPS] ?: defaults.hiddenApps
        )
    }

    /**
     * Writes only changed setting keys.
     *
     * This preserves the generic `updateSettings` API while avoiding needless
     * full-snapshot rewrites when only a small subset of settings changed.
     */
    private fun writeSettingsDiffToPreferences(
        currentSettings: LauncherSettings,
        newSettings: LauncherSettings,
        preferences: androidx.datastore.preferences.core.MutablePreferences
    ) {
        if (currentSettings.maxSearchResults != newSettings.maxSearchResults) {
            preferences[SettingsPreferenceKeys.MAX_SEARCH_RESULTS] = newSettings.maxSearchResults
        }
        if (currentSettings.autoFocusKeyboard != newSettings.autoFocusKeyboard) {
            preferences[SettingsPreferenceKeys.AUTO_FOCUS_KEYBOARD] = newSettings.autoFocusKeyboard
        }
        if (currentSettings.showRecentApps != newSettings.showRecentApps) {
            preferences[SettingsPreferenceKeys.SHOW_RECENT_APPS] = newSettings.showRecentApps
        }
        if (currentSettings.maxRecentApps != newSettings.maxRecentApps) {
            preferences[SettingsPreferenceKeys.MAX_RECENT_APPS] = newSettings.maxRecentApps
        }
        if (currentSettings.closeSearchOnLaunch != newSettings.closeSearchOnLaunch) {
            preferences[SettingsPreferenceKeys.CLOSE_SEARCH_ON_LAUNCH] =
                newSettings.closeSearchOnLaunch
        }

        if (currentSettings.searchResultLayout != newSettings.searchResultLayout) {
            preferences[SettingsPreferenceKeys.SEARCH_RESULT_LAYOUT] =
                newSettings.searchResultLayout.name
        }
        if (currentSettings.showHomescreenHint != newSettings.showHomescreenHint) {
            preferences[SettingsPreferenceKeys.SHOW_HOMESCREEN_HINT] =
                newSettings.showHomescreenHint
        }
        if (currentSettings.showAppIcons != newSettings.showAppIcons) {
            preferences[SettingsPreferenceKeys.SHOW_APP_ICONS] = newSettings.showAppIcons
        }

        if (currentSettings.homeTapAction != newSettings.homeTapAction) {
            preferences[SettingsPreferenceKeys.HOME_TAP_ACTION] = newSettings.homeTapAction.name
        }
        if (currentSettings.swipeUpAction != newSettings.swipeUpAction) {
            preferences[SettingsPreferenceKeys.SWIPE_UP_ACTION] = newSettings.swipeUpAction.name
        }

        if (currentSettings.contactsSearchEnabled != newSettings.contactsSearchEnabled) {
            preferences[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED] =
                newSettings.contactsSearchEnabled
        }
        if (currentSettings.filesSearchEnabled != newSettings.filesSearchEnabled) {
            preferences[SettingsPreferenceKeys.FILES_SEARCH_ENABLED] =
                newSettings.filesSearchEnabled
        }

        if (currentSettings.searchSources != newSettings.searchSources) {
            writeSearchSources(newSettings.searchSources, preferences)
        }

        if (currentSettings.prefixConfigurations != newSettings.prefixConfigurations) {
            writePrefixConfigurations(newSettings.prefixConfigurations, preferences)
        }

        if (currentSettings.hiddenApps != newSettings.hiddenApps) {
            preferences[SettingsPreferenceKeys.HIDDEN_APPS] = newSettings.hiddenApps
        }
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

    private fun writePrefixConfigurations(
        configurations: ProviderPrefixConfiguration,
        preferences: androidx.datastore.preferences.core.MutablePreferences
    ) {
        if (configurations.isEmpty()) {
            preferences.remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
            return
        }

        preferences[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] =
            settingsStorageJson.encodeToString(
                configurations
                    .filterValues { it.prefixes.isNotEmpty() }
                    .mapValues { (_, prefixConfig) -> prefixConfig.prefixes }
            )
    }

    private fun writeSearchSources(
        sources: List<SearchSource>,
        preferences: androidx.datastore.preferences.core.MutablePreferences
    ) {
        val normalized = normalizeAndValidateSearchSources(sources)
        preferences[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] =
            SearchSourcesStorageState.INITIALIZED
        preferences[SettingsPreferenceKeys.SEARCH_SOURCES] =
            settingsStorageJson.encodeToString(normalized)
    }

    private fun parsePrefixConfigurations(json: String?): ProviderPrefixConfiguration {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val serializedConfiguration: SerializedPrefixConfiguration =
                settingsStorageJson.decodeFromString(json)

            serializedConfiguration
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, prefixes) -> com.milki.launcher.domain.model.PrefixConfig(prefixes) }
        }.getOrElse {
            emptyMap()
        }
    }

    private fun parseSearchSources(preferences: Preferences): List<SearchSource> {
        val json = preferences[SettingsPreferenceKeys.SEARCH_SOURCES]
        val isInitialized =
            preferences[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] ==
                SearchSourcesStorageState.INITIALIZED

        return if (!isInitialized) {
            if (json.isNullOrBlank()) {
                SearchSource.defaultSources()
            } else {
                runCatching {
                    val decoded: SerializedSearchSources =
                        settingsStorageJson.decodeFromString(json)
                    normalizeAndValidateSearchSources(decoded)
                }.getOrElse {
                    SearchSource.defaultSources()
                }
            }
        } else {
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    val decoded: SerializedSearchSources =
                        settingsStorageJson.decodeFromString(json)
                    normalizeAndValidateSearchSources(decoded)
                }.getOrElse {
                    emptyList()
                }
            }
        }
    }

    private fun normalizeAndValidateSearchSources(
        rawSources: List<SearchSource>
    ): List<SearchSource> {
        val normalized = rawSources.mapIndexed { index, source ->
            val normalizedPrefixes = source.prefixes
                .map(SearchSource.Companion::normalizePrefix)
                .filter { it.isNotBlank() && !it.contains(" ") }
                .distinct()

            val safeName = source.name.trim().ifBlank { "Source ${index + 1}" }
            val safeTemplate = if (SearchSource.isValidUrlTemplate(source.urlTemplate)) {
                source.urlTemplate.trim()
            } else {
                "https://www.google.com/search?q={query}"
            }

            source.copy(
                name = safeName,
                urlTemplate = safeTemplate,
                prefixes = normalizedPrefixes,
                accentColorHex = SearchSource.normalizeHexColor(source.accentColorHex)
            )
        }

        val seenPrefixes = mutableSetOf<String>()
        return normalized.map { source ->
            val filteredPrefixes = source.prefixes.filter { prefix ->
                if (prefix in seenPrefixes) {
                    false
                } else {
                    seenPrefixes.add(prefix)
                    true
                }
            }
            source.copy(prefixes = filteredPrefixes)
        }
    }
}
