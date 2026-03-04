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
            writeSettingsToPreferences(newSettings, preferences)
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

            // Prefix Configuration
            prefixConfigurations = parsePrefixConfigurations(preferences[Keys.PREFIX_CONFIGURATIONS]),

            // Hidden Apps
            hiddenApps = preferences[Keys.HIDDEN_APPS] ?: defaults.hiddenApps
        )
    }

    /**
     * Write LauncherSettings data class to DataStore Preferences.
     */
    private fun writeSettingsToPreferences(
        settings: LauncherSettings,
        preferences: MutablePreferences
    ) {
        // Search Behavior
        preferences[Keys.MAX_SEARCH_RESULTS] = settings.maxSearchResults
        preferences[Keys.AUTO_FOCUS_KEYBOARD] = settings.autoFocusKeyboard
        preferences[Keys.SHOW_RECENT_APPS] = settings.showRecentApps
        preferences[Keys.MAX_RECENT_APPS] = settings.maxRecentApps
        preferences[Keys.CLOSE_SEARCH_ON_LAUNCH] = settings.closeSearchOnLaunch

        // Appearance
        preferences[Keys.SEARCH_RESULT_LAYOUT] = settings.searchResultLayout.name
        preferences[Keys.SHOW_HOMESCREEN_HINT] = settings.showHomescreenHint
        preferences[Keys.SHOW_APP_ICONS] = settings.showAppIcons

        // Home Screen
        preferences[Keys.HOME_TAP_ACTION] = settings.homeTapAction.name
        preferences[Keys.SWIPE_UP_ACTION] = settings.swipeUpAction.name
        preferences[Keys.HOME_BUTTON_CLEARS_QUERY] = settings.homeButtonClearsQuery

        // Search Providers
        preferences[Keys.DEFAULT_SEARCH_ENGINE] = settings.defaultSearchEngine.name
        preferences[Keys.WEB_SEARCH_ENABLED] = settings.webSearchEnabled
        preferences[Keys.CONTACTS_SEARCH_ENABLED] = settings.contactsSearchEnabled
        preferences[Keys.YOUTUBE_SEARCH_ENABLED] = settings.youtubeSearchEnabled
        preferences[Keys.FILES_SEARCH_ENABLED] = settings.filesSearchEnabled

        // Prefix Configuration
        preferences[Keys.PREFIX_CONFIGURATIONS] = serializePrefixConfigurations(settings.prefixConfigurations)

        // Hidden Apps
        preferences[Keys.HIDDEN_APPS] = settings.hiddenApps
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
}
