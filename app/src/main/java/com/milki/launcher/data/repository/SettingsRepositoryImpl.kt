/**
 * SettingsRepositoryImpl.kt - DataStore-backed implementation of SettingsRepository
 *
 * Persists all launcher settings using Jetpack DataStore Preferences.
 * Each setting is stored as a separate key-value pair for granular updates.
 *
 * WHY DATASTORE INSTEAD OF SHAREDPREFERENCES?
 * - Type-safe key definitions
 * - Coroutine/Flow support (non-blocking)
 * - No UI thread blocking (unlike SharedPreferences.commit())
 * - Atomic read-modify-write operations
 *
 * SERIALIZATION:
 * - Primitives (Int, Boolean, String): Stored directly
 * - Enums: Stored as String (name property)
 * - Set<String>: Stored as StringSet
 */

package com.milki.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Serialized representation used for persisting provider prefixes.
 *
 * IMPORTANT COMPATIBILITY NOTE:
 * The persisted JSON format intentionally remains:
 * {
 *   "web": ["s", "ج"],
 *   "files": ["f", "م"]
 * }
 *
 * We keep this as Map<String, List<String>> to preserve backward compatibility
 * with already stored values while still using kotlinx.serialization for all
 * encoding/decoding work.
 */
private typealias SerializedPrefixConfiguration = Map<String, List<String>>

/**
 * Serialized representation for dynamic external search sources.
 *
 * We persist the list directly as JSON using kotlinx.serialization.
 */
private typealias SerializedSearchSources = List<SearchSource>

/**
 * DataStore instance for settings, scoped to the application context.
 * The name "launcher_settings" determines the file name on disk.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

/**
 * DataStore-backed implementation of SettingsRepository.
 *
 * Reads and writes all launcher settings from/to DataStore Preferences.
 * All operations are non-blocking and suspend-based.
 *
 * @param context Application context for DataStore access
 */
class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    /**
     * Shared Json instance for settings serialization/deserialization.
     *
     * CONFIGURATION CHOICES:
     * - ignoreUnknownKeys = true:
     *   Allows forward compatibility if a future version adds extra fields.
     * - encodeDefaults = true:
     *   Keeps output deterministic when defaultable values are introduced later.
     *
     * We keep this instance private to the repository because it is tightly
     * coupled to the repository's storage schema and migration behavior.
     */
    private val settingsJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========================================================================
    // PREFERENCE KEYS
    // ========================================================================

    private object Keys {
        // Search Behavior
        val MAX_SEARCH_RESULTS = intPreferencesKey("max_search_results")
        val AUTO_FOCUS_KEYBOARD = booleanPreferencesKey("auto_focus_keyboard")
        val SHOW_RECENT_APPS = booleanPreferencesKey("show_recent_apps")
        val MAX_RECENT_APPS = intPreferencesKey("max_recent_apps")
        val CLOSE_SEARCH_ON_LAUNCH = booleanPreferencesKey("close_search_on_launch")

        // Appearance
        val SEARCH_RESULT_LAYOUT = stringPreferencesKey("search_result_layout")
        val SHOW_HOMESCREEN_HINT = booleanPreferencesKey("show_homescreen_hint")
        val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")

        // Home Screen
        val HOME_TAP_ACTION = stringPreferencesKey("home_tap_action")
        val SWIPE_UP_ACTION = stringPreferencesKey("swipe_up_action")
        val HOME_BUTTON_CLEARS_QUERY = booleanPreferencesKey("home_button_clears_query")

        // Search Providers
        val DEFAULT_SEARCH_ENGINE = stringPreferencesKey("default_search_engine")
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val CONTACTS_SEARCH_ENABLED = booleanPreferencesKey("contacts_search_enabled")
        val YOUTUBE_SEARCH_ENABLED = booleanPreferencesKey("youtube_search_enabled")
        val FILES_SEARCH_ENABLED = booleanPreferencesKey("files_search_enabled")

        // Hidden Apps
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")

        // Prefix Configuration - stored as JSON string
        // Format: {"web":["s","ج"],"files":["f","م"],...}
        val PREFIX_CONFIGURATIONS = stringPreferencesKey("prefix_configurations")

        // Dynamic source configuration - stored as JSON array
        val SEARCH_SOURCES = stringPreferencesKey("search_sources")
    }

    // ========================================================================
    // SETTINGS FLOW
    // ========================================================================

    override val settings: Flow<LauncherSettings> = context.settingsDataStore.data
        .catch { exception ->
            // If DataStore encounters an IOException, emit default settings
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapPreferencesToSettings(preferences)
        }

    // ========================================================================
    // UPDATE SETTINGS
    // ========================================================================

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { preferences ->
            val currentSettings = mapPreferencesToSettings(preferences)
            val newSettings = transform(currentSettings)

            // PERFORMANCE OPTIMIZATION:
            // Instead of always writing every key (full snapshot write), we now
            // write only keys whose values actually changed. This reduces write
            // overhead during frequent settings edits.
            writeSettingsDiffToPreferences(
                currentSettings = currentSettings,
                newSettings = newSettings,
                preferences = preferences
            )
        }
    }

    /**
     * Targeted key-only update for max search results.
     */
    override suspend fun setMaxSearchResults(value: Int) {
        writeIntSetting(Keys.MAX_SEARCH_RESULTS, value)
    }

    /**
     * Targeted key-only update for auto-focus keyboard setting.
     */
    override suspend fun setAutoFocusKeyboard(value: Boolean) {
        writeBooleanSetting(Keys.AUTO_FOCUS_KEYBOARD, value)
    }

    /**
     * Targeted key-only update for show recent apps setting.
     */
    override suspend fun setShowRecentApps(value: Boolean) {
        writeBooleanSetting(Keys.SHOW_RECENT_APPS, value)
    }

    /**
     * Targeted key-only update for max recent apps setting.
     */
    override suspend fun setMaxRecentApps(value: Int) {
        writeIntSetting(Keys.MAX_RECENT_APPS, value)
    }

    /**
     * Targeted key-only update for close search on launch setting.
     */
    override suspend fun setCloseSearchOnLaunch(value: Boolean) {
        writeBooleanSetting(Keys.CLOSE_SEARCH_ON_LAUNCH, value)
    }

    /**
     * Targeted key-only update for search result layout enum.
     */
    override suspend fun setSearchResultLayout(layout: SearchResultLayout) {
        writeStringSetting(Keys.SEARCH_RESULT_LAYOUT, layout.name)
    }

    /**
     * Targeted key-only update for homescreen hint visibility setting.
     */
    override suspend fun setShowHomescreenHint(value: Boolean) {
        writeBooleanSetting(Keys.SHOW_HOMESCREEN_HINT, value)
    }

    /**
     * Targeted key-only update for app icons visibility setting.
     */
    override suspend fun setShowAppIcons(value: Boolean) {
        writeBooleanSetting(Keys.SHOW_APP_ICONS, value)
    }

    /**
     * Targeted key-only update for home tap action enum.
     */
    override suspend fun setHomeTapAction(action: HomeTapAction) {
        writeStringSetting(Keys.HOME_TAP_ACTION, action.name)
    }

    /**
     * Targeted key-only update for swipe-up action enum.
     */
    override suspend fun setSwipeUpAction(action: SwipeUpAction) {
        writeStringSetting(Keys.SWIPE_UP_ACTION, action.name)
    }

    /**
     * Targeted key-only update for home button clears query setting.
     */
    override suspend fun setHomeButtonClearsQuery(value: Boolean) {
        writeBooleanSetting(Keys.HOME_BUTTON_CLEARS_QUERY, value)
    }

    /**
     * Targeted key-only update for default search engine enum.
     */
    override suspend fun setDefaultSearchEngine(engine: SearchEngine) {
        writeStringSetting(Keys.DEFAULT_SEARCH_ENGINE, engine.name)
    }

    /**
     * Targeted key-only update for web provider enabled setting.
     */
    override suspend fun setWebSearchEnabled(value: Boolean) {
        writeBooleanSetting(Keys.WEB_SEARCH_ENABLED, value)
    }

    /**
     * Targeted key-only update for contacts provider enabled setting.
     */
    override suspend fun setContactsSearchEnabled(value: Boolean) {
        writeBooleanSetting(Keys.CONTACTS_SEARCH_ENABLED, value)
    }

    /**
     * Targeted key-only update for YouTube provider enabled setting.
     */
    override suspend fun setYoutubeSearchEnabled(value: Boolean) {
        writeBooleanSetting(Keys.YOUTUBE_SEARCH_ENABLED, value)
    }

    /**
     * Targeted key-only update for files provider enabled setting.
     */
    override suspend fun setFilesSearchEnabled(value: Boolean) {
        writeBooleanSetting(Keys.FILES_SEARCH_ENABLED, value)
    }

    // ========================================================================
    // TARGETED HOT-PATH UPDATES
    // ========================================================================

    /**
     * Targeted provider-prefix update.
     *
     * This method updates only the single prefix configuration key in DataStore.
     * It intentionally avoids full LauncherSettings read/write remapping.
     */
    override suspend fun setProviderPrefixes(providerId: String, prefixes: List<String>) {
        context.settingsDataStore.edit { preferences ->
            val currentConfigurations = parsePrefixConfigurations(preferences[Keys.PREFIX_CONFIGURATIONS])
            val updatedConfigurations = currentConfigurations.toMutableMap()

            if (prefixes.isNotEmpty()) {
                updatedConfigurations[providerId] = PrefixConfig(prefixes)
            } else {
                updatedConfigurations.remove(providerId)
            }

            writePrefixConfigurations(updatedConfigurations, preferences)
        }
    }

    /**
     * Add one prefix with duplicate prevention and fallback-default behavior.
     */
    override suspend fun addProviderPrefix(providerId: String, prefix: String, defaultPrefix: String) {
        context.settingsDataStore.edit { preferences ->
            val currentConfigurations = parsePrefixConfigurations(preferences[Keys.PREFIX_CONFIGURATIONS])
            val currentPrefixes = currentConfigurations[providerId]?.prefixes ?: listOf(defaultPrefix)

            if (prefix in currentPrefixes) {
                return@edit
            }

            val updatedConfigurations = currentConfigurations.toMutableMap()
            updatedConfigurations[providerId] = PrefixConfig(currentPrefixes + prefix)
            writePrefixConfigurations(updatedConfigurations, preferences)
        }
    }

    /**
     * Remove one prefix from one provider entry.
     */
    override suspend fun removeProviderPrefix(providerId: String, prefix: String) {
        context.settingsDataStore.edit { preferences ->
            val currentConfigurations = parsePrefixConfigurations(preferences[Keys.PREFIX_CONFIGURATIONS])
            val currentPrefixes = currentConfigurations[providerId]?.prefixes ?: return@edit

            val updatedPrefixes = currentPrefixes - prefix
            val updatedConfigurations = currentConfigurations.toMutableMap()

            if (updatedPrefixes.isNotEmpty()) {
                updatedConfigurations[providerId] = PrefixConfig(updatedPrefixes)
            } else {
                updatedConfigurations.remove(providerId)
            }

            writePrefixConfigurations(updatedConfigurations, preferences)
        }
    }

    /**
     * Remove custom prefix config for a single provider.
     */
    override suspend fun resetProviderPrefixes(providerId: String) {
        context.settingsDataStore.edit { preferences ->
            val currentConfigurations = parsePrefixConfigurations(preferences[Keys.PREFIX_CONFIGURATIONS])
            if (providerId !in currentConfigurations) {
                return@edit
            }

            val updatedConfigurations = currentConfigurations.toMutableMap()
            updatedConfigurations.remove(providerId)
            writePrefixConfigurations(updatedConfigurations, preferences)
        }
    }

    /**
     * Remove all custom prefix config values.
     */
    override suspend fun resetAllPrefixConfigurations() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.PREFIX_CONFIGURATIONS)
        }
    }

    /**
     * Replace full prefix configuration map in one targeted write.
     */
    override suspend fun setAllPrefixConfigurations(configurations: ProviderPrefixConfiguration) {
        context.settingsDataStore.edit { preferences ->
            writePrefixConfigurations(configurations, preferences)
        }
    }

    /**
     * Toggle hidden-app package inside the hidden apps set.
     */
    override suspend fun toggleHiddenApp(packageName: String) {
        context.settingsDataStore.edit { preferences ->
            val currentHiddenApps = (preferences[Keys.HIDDEN_APPS] ?: emptySet()).toMutableSet()

            if (packageName in currentHiddenApps) {
                currentHiddenApps.remove(packageName)
            } else {
                currentHiddenApps.add(packageName)
            }

            preferences[Keys.HIDDEN_APPS] = currentHiddenApps
        }
    }

    // ========================================================================
    // MAPPING HELPERS
    // ========================================================================

    /**
     * Map DataStore Preferences to LauncherSettings data class.
     */
    private fun mapPreferencesToSettings(preferences: Preferences): LauncherSettings {
        val defaults = LauncherSettings()
        val parsedPrefixConfigurations = parsePrefixConfigurations(preferences[Keys.PREFIX_CONFIGURATIONS])
        val parsedSearchSources = parseSearchSources(
            json = preferences[Keys.SEARCH_SOURCES],
            legacyPrefixConfigurations = parsedPrefixConfigurations,
            defaultSearchEngine = preferences[Keys.DEFAULT_SEARCH_ENGINE]?.let {
                runCatching { SearchEngine.valueOf(it) }.getOrDefault(defaults.defaultSearchEngine)
            } ?: defaults.defaultSearchEngine,
            webSearchEnabled = preferences[Keys.WEB_SEARCH_ENABLED] ?: defaults.webSearchEnabled,
            youtubeSearchEnabled = preferences[Keys.YOUTUBE_SEARCH_ENABLED] ?: defaults.youtubeSearchEnabled
        )

        return LauncherSettings(
            // Search Behavior
            maxSearchResults = preferences[Keys.MAX_SEARCH_RESULTS] ?: defaults.maxSearchResults,
            autoFocusKeyboard = preferences[Keys.AUTO_FOCUS_KEYBOARD] ?: defaults.autoFocusKeyboard,
            showRecentApps = preferences[Keys.SHOW_RECENT_APPS] ?: defaults.showRecentApps,
            maxRecentApps = preferences[Keys.MAX_RECENT_APPS] ?: defaults.maxRecentApps,
            closeSearchOnLaunch = preferences[Keys.CLOSE_SEARCH_ON_LAUNCH] ?: defaults.closeSearchOnLaunch,

            // Appearance
            searchResultLayout = preferences[Keys.SEARCH_RESULT_LAYOUT]?.let {
                runCatching { SearchResultLayout.valueOf(it) }.getOrDefault(defaults.searchResultLayout)
            } ?: defaults.searchResultLayout,
            showHomescreenHint = preferences[Keys.SHOW_HOMESCREEN_HINT] ?: defaults.showHomescreenHint,
            showAppIcons = preferences[Keys.SHOW_APP_ICONS] ?: defaults.showAppIcons,

            // Home Screen
            homeTapAction = preferences[Keys.HOME_TAP_ACTION]?.let {
                runCatching { HomeTapAction.valueOf(it) }.getOrDefault(defaults.homeTapAction)
            } ?: defaults.homeTapAction,
            swipeUpAction = preferences[Keys.SWIPE_UP_ACTION]?.let {
                runCatching { SwipeUpAction.valueOf(it) }.getOrDefault(defaults.swipeUpAction)
            } ?: defaults.swipeUpAction,
            homeButtonClearsQuery = preferences[Keys.HOME_BUTTON_CLEARS_QUERY]
                ?: defaults.homeButtonClearsQuery,

            // Search Providers
            defaultSearchEngine = preferences[Keys.DEFAULT_SEARCH_ENGINE]?.let {
                runCatching { SearchEngine.valueOf(it) }.getOrDefault(defaults.defaultSearchEngine)
            } ?: defaults.defaultSearchEngine,
            webSearchEnabled = preferences[Keys.WEB_SEARCH_ENABLED] ?: defaults.webSearchEnabled,
            contactsSearchEnabled = preferences[Keys.CONTACTS_SEARCH_ENABLED]
                ?: defaults.contactsSearchEnabled,
            youtubeSearchEnabled = preferences[Keys.YOUTUBE_SEARCH_ENABLED]
                ?: defaults.youtubeSearchEnabled,
            filesSearchEnabled = preferences[Keys.FILES_SEARCH_ENABLED] ?: defaults.filesSearchEnabled,

            // Dynamic external search sources
            searchSources = parsedSearchSources,

            // Prefix Configuration
            prefixConfigurations = parsedPrefixConfigurations,

            // Hidden Apps
            hiddenApps = preferences[Keys.HIDDEN_APPS] ?: defaults.hiddenApps
        )
    }

    /**
     * Writes only changed setting keys.
     *
     * WHY THIS EXISTS:
     * `updateSettings` is intentionally generic and keeps existing call sites intact,
     * but generic transforms often changed one or two fields while rewriting every
     * key. This helper reduces write churn by comparing old/new values first.
     */
    private fun writeSettingsDiffToPreferences(
        currentSettings: LauncherSettings,
        newSettings: LauncherSettings,
        preferences: MutablePreferences
    ) {
        if (currentSettings.maxSearchResults != newSettings.maxSearchResults) {
            preferences[Keys.MAX_SEARCH_RESULTS] = newSettings.maxSearchResults
        }
        if (currentSettings.autoFocusKeyboard != newSettings.autoFocusKeyboard) {
            preferences[Keys.AUTO_FOCUS_KEYBOARD] = newSettings.autoFocusKeyboard
        }
        if (currentSettings.showRecentApps != newSettings.showRecentApps) {
            preferences[Keys.SHOW_RECENT_APPS] = newSettings.showRecentApps
        }
        if (currentSettings.maxRecentApps != newSettings.maxRecentApps) {
            preferences[Keys.MAX_RECENT_APPS] = newSettings.maxRecentApps
        }
        if (currentSettings.closeSearchOnLaunch != newSettings.closeSearchOnLaunch) {
            preferences[Keys.CLOSE_SEARCH_ON_LAUNCH] = newSettings.closeSearchOnLaunch
        }

        if (currentSettings.searchResultLayout != newSettings.searchResultLayout) {
            preferences[Keys.SEARCH_RESULT_LAYOUT] = newSettings.searchResultLayout.name
        }
        if (currentSettings.showHomescreenHint != newSettings.showHomescreenHint) {
            preferences[Keys.SHOW_HOMESCREEN_HINT] = newSettings.showHomescreenHint
        }
        if (currentSettings.showAppIcons != newSettings.showAppIcons) {
            preferences[Keys.SHOW_APP_ICONS] = newSettings.showAppIcons
        }

        if (currentSettings.homeTapAction != newSettings.homeTapAction) {
            preferences[Keys.HOME_TAP_ACTION] = newSettings.homeTapAction.name
        }
        if (currentSettings.swipeUpAction != newSettings.swipeUpAction) {
            preferences[Keys.SWIPE_UP_ACTION] = newSettings.swipeUpAction.name
        }
        if (currentSettings.homeButtonClearsQuery != newSettings.homeButtonClearsQuery) {
            preferences[Keys.HOME_BUTTON_CLEARS_QUERY] = newSettings.homeButtonClearsQuery
        }

        if (currentSettings.defaultSearchEngine != newSettings.defaultSearchEngine) {
            preferences[Keys.DEFAULT_SEARCH_ENGINE] = newSettings.defaultSearchEngine.name
        }
        if (currentSettings.webSearchEnabled != newSettings.webSearchEnabled) {
            preferences[Keys.WEB_SEARCH_ENABLED] = newSettings.webSearchEnabled
        }
        if (currentSettings.contactsSearchEnabled != newSettings.contactsSearchEnabled) {
            preferences[Keys.CONTACTS_SEARCH_ENABLED] = newSettings.contactsSearchEnabled
        }
        if (currentSettings.youtubeSearchEnabled != newSettings.youtubeSearchEnabled) {
            preferences[Keys.YOUTUBE_SEARCH_ENABLED] = newSettings.youtubeSearchEnabled
        }
        if (currentSettings.filesSearchEnabled != newSettings.filesSearchEnabled) {
            preferences[Keys.FILES_SEARCH_ENABLED] = newSettings.filesSearchEnabled
        }

        if (currentSettings.searchSources != newSettings.searchSources) {
            writeSearchSources(newSettings.searchSources, preferences)
        }

        if (currentSettings.prefixConfigurations != newSettings.prefixConfigurations) {
            writePrefixConfigurations(newSettings.prefixConfigurations, preferences)
        }

        if (currentSettings.hiddenApps != newSettings.hiddenApps) {
            preferences[Keys.HIDDEN_APPS] = newSettings.hiddenApps
        }
    }

    /**
     * Writes prefix configuration key with empty-state compaction.
     *
     * Compaction behavior:
     * - Empty map -> remove key completely
     * - Non-empty map -> write serialized JSON string
     */
    private fun writePrefixConfigurations(
        configurations: ProviderPrefixConfiguration,
        preferences: MutablePreferences
    ) {
        if (configurations.isEmpty()) {
            preferences.remove(Keys.PREFIX_CONFIGURATIONS)
            return
        }

        preferences[Keys.PREFIX_CONFIGURATIONS] = serializePrefixConfigurations(configurations)
    }

    /**
     * Shared helper for writing one Boolean preference key.
     *
     * This helper keeps targeted-write methods concise and easy to audit.
     */
    private suspend fun writeBooleanSetting(
        key: Preferences.Key<Boolean>,
        value: Boolean
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    /**
     * Shared helper for writing one Int preference key.
     */
    private suspend fun writeIntSetting(
        key: Preferences.Key<Int>,
        value: Int
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    /**
     * Shared helper for writing one String preference key.
     */
    private suspend fun writeStringSetting(
        key: Preferences.Key<String>,
        value: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    /**
     * Writes the dynamic search source list into DataStore.
     *
     * Empty list compaction:
     * - Empty list removes the key entirely and lets mapPreferencesToSettings fallback
     *   to default source list, so the app always has usable source data.
     */
    private fun writeSearchSources(
        sources: List<SearchSource>,
        preferences: MutablePreferences
    ) {
        if (sources.isEmpty()) {
            preferences.remove(Keys.SEARCH_SOURCES)
            return
        }

        preferences[Keys.SEARCH_SOURCES] = serializeSearchSources(sources)
    }

    // ========================================================================
    // PREFIX CONFIGURATION SERIALIZATION
    // ========================================================================

    /**
     * Parse a JSON string into a ProviderPrefixConfiguration map.
     *
     * JSON FORMAT:
     * ```json
     * {
     *   "web": ["s", "ج"],
     *   "files": ["f", "م"],
     *   "contacts": ["c"],
     *   "youtube": ["y", "yt"]
     * }
     * ```
     *
    * This format was chosen because:
    * - It's human-readable and easy to debug
    * - It handles the list of prefixes naturally
    * - It's compact and easy to evolve
    * - It is now parsed by kotlinx.serialization for consistency with home item persistence
     *
     * @param json The JSON string to parse, or null
     * @return Map of provider ID to PrefixConfig, or empty map if parsing fails
     */
    private fun parsePrefixConfigurations(json: String?): ProviderPrefixConfiguration {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val serializedConfiguration: SerializedPrefixConfiguration =
                settingsJson.decodeFromString(json)

            // Maintain existing behavior: only keep providers with at least one prefix.
            // This prevents storing no-op entries like "web": [] in the active model.
            serializedConfiguration
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, prefixes) -> PrefixConfig(prefixes) }
        }.getOrElse {
            // If parsing fails for any reason, return empty map.
            // Returning empty map is safe because default provider prefixes are applied.
            emptyMap()
        }
    }

    /**
     * Serialize a ProviderPrefixConfiguration map to a JSON string.
     *
     * @param config The configuration map to serialize
     * @return JSON string representation
     */
    private fun serializePrefixConfigurations(config: ProviderPrefixConfiguration): String {
        if (config.isEmpty()) {
            return "{}"
        }

        // Convert domain model to serialized schema while preserving the existing
        // on-disk JSON structure (providerId -> array of prefixes).
        val serializedConfiguration: SerializedPrefixConfiguration =
            config.mapValues { (_, prefixConfig) -> prefixConfig.prefixes }

        return settingsJson.encodeToString(serializedConfiguration)
    }

    // ========================================================================
    // SEARCH SOURCES SERIALIZATION + LEGACY MIGRATION
    // ========================================================================

    /**
     * Parses persisted search sources, with legacy migration fallback.
     *
     * Migration behavior when no `search_sources` key exists yet:
     * - Start from SearchSource.defaultSources()
     * - Carry over legacy web/youtube enable flags
     * - Carry over legacy web and youtube prefixes from prefixConfigurations
     * - Apply legacy default search engine to choose default plain-query source
     */
    private fun parseSearchSources(
        json: String?,
        legacyPrefixConfigurations: ProviderPrefixConfiguration,
        defaultSearchEngine: SearchEngine,
        webSearchEnabled: Boolean,
        youtubeSearchEnabled: Boolean
    ): List<SearchSource> {
        if (json.isNullOrBlank()) {
            return migrateLegacySources(
                legacyPrefixConfigurations = legacyPrefixConfigurations,
                defaultSearchEngine = defaultSearchEngine,
                webSearchEnabled = webSearchEnabled,
                youtubeSearchEnabled = youtubeSearchEnabled
            )
        }

        return runCatching {
            val decoded: SerializedSearchSources = settingsJson.decodeFromString(json)
            normalizeAndValidateSearchSources(decoded)
        }.getOrElse {
            migrateLegacySources(
                legacyPrefixConfigurations = legacyPrefixConfigurations,
                defaultSearchEngine = defaultSearchEngine,
                webSearchEnabled = webSearchEnabled,
                youtubeSearchEnabled = youtubeSearchEnabled
            )
        }
    }

    /**
     * Serializes search sources to JSON.
     */
    private fun serializeSearchSources(sources: List<SearchSource>): String {
        val normalized = normalizeAndValidateSearchSources(sources)
        return settingsJson.encodeToString(normalized)
    }

    /**
     * Normalizes/validates a source list before runtime use and persistence.
     *
     * Rules enforced here:
     * - Prefixes are normalized to lowercase
     * - Prefix list values are deduplicated
     * - Invalid/blank names are replaced with fallback names
     * - Invalid templates are replaced with a safe Google template
     * - Invalid colors are normalized to #RRGGBB fallback
     * - Exactly one default plain-query source is maintained when possible
     */
    private fun normalizeAndValidateSearchSources(rawSources: List<SearchSource>): List<SearchSource> {
        if (rawSources.isEmpty()) {
            return SearchSource.defaultSources()
        }

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

        // Enforce global prefix uniqueness (first source wins).
        val seenPrefixes = mutableSetOf<String>()
        val globallyUnique = normalized.map { source ->
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

        // Ensure one default plain-query action source.
        val hasDefault = globallyUnique.any { it.isDefaultForPlainQueryAction && it.isEnabled }
        if (hasDefault) {
            return globallyUnique
        }

        val firstEnabledIndex = globallyUnique.indexOfFirst { it.isEnabled }
        if (firstEnabledIndex == -1) {
            return globallyUnique
        }

        return globallyUnique.mapIndexed { index, source ->
            source.copy(isDefaultForPlainQueryAction = index == firstEnabledIndex)
        }
    }

    /**
     * Builds initial search source list using legacy settings.
     */
    private fun migrateLegacySources(
        legacyPrefixConfigurations: ProviderPrefixConfiguration,
        defaultSearchEngine: SearchEngine,
        webSearchEnabled: Boolean,
        youtubeSearchEnabled: Boolean
    ): List<SearchSource> {
        val defaults = SearchSource.defaultSources().toMutableList()

        val legacyWebPrefixes = legacyPrefixConfigurations[ProviderId.WEB]?.prefixes
        val legacyYoutubePrefixes = legacyPrefixConfigurations[ProviderId.YOUTUBE]?.prefixes

        val migrated = defaults.map { source ->
            when (source.id) {
                "source_google" -> {
                    source.copy(
                        isEnabled = webSearchEnabled,
                        prefixes = (legacyWebPrefixes ?: source.prefixes)
                    )
                }
                "source_duckduckgo" -> {
                    source.copy(isEnabled = webSearchEnabled)
                }
                "source_youtube" -> {
                    source.copy(
                        isEnabled = youtubeSearchEnabled,
                        prefixes = (legacyYoutubePrefixes ?: source.prefixes)
                    )
                }
                else -> source
            }
        }

        // Apply default engine choice from legacy enum.
        val targetDefaultId = when (defaultSearchEngine) {
            SearchEngine.GOOGLE -> "source_google"
            SearchEngine.DUCKDUCKGO -> "source_duckduckgo"
            SearchEngine.BING -> "source_google"
            SearchEngine.BRAVE -> "source_google"
            SearchEngine.STARTPAGE -> "source_google"
        }

        return normalizeAndValidateSearchSources(
            migrated.map { source ->
                source.copy(isDefaultForPlainQueryAction = source.id == targetDefaultId)
            }
        )
    }
}
