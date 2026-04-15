package com.milki.launcher.data.repository.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.domain.model.SearchSource
import kotlinx.serialization.json.Json

/**
 * Shared serialized representation used for persisting provider prefixes.
 *
 * IMPORTANT COMPATIBILITY NOTE:
 * The persisted JSON format intentionally remains:
 * {
 *   "web": ["s", "ج"],
 *   "files": ["f", "م"]
 * }
 *
 * We keep this as Map<String, List<String>> to preserve backward compatibility
 * with already stored values.
 */
internal typealias SerializedPrefixConfiguration = Map<String, List<String>>

/**
 * Shared serialized representation for dynamic external search sources.
 */
internal typealias SerializedSearchSources = List<SearchSource>

/**
 * Shared serialized representation for trigger -> action mappings.
 */
internal typealias SerializedTriggerActions = Map<String, String>

/**
 * DataStore instance for launcher settings, scoped to application context.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

/**
 * Centralized preference keys for launcher settings persistence.
 *
 * Keeping all keys together makes the storage schema easier to audit and
 * reduces coupling between repository read/write paths.
 */
internal object SettingsPreferenceKeys {
    // Search Behavior
    val MAX_SEARCH_RESULTS = intPreferencesKey("max_search_results")
    val AUTO_FOCUS_KEYBOARD = booleanPreferencesKey("auto_focus_keyboard")
    val SHOW_RECENT_APPS = booleanPreferencesKey("show_recent_apps")
    val CLOSE_SEARCH_ON_LAUNCH = booleanPreferencesKey("close_search_on_launch")

    // Appearance
    val SEARCH_RESULT_LAYOUT = stringPreferencesKey("search_result_layout")
    val SHOW_HOMESCREEN_HINT = booleanPreferencesKey("show_homescreen_hint")
    val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")

    // Home Screen
    val TRIGGER_ACTIONS = stringPreferencesKey("trigger_actions")

    // Legacy Home Screen keys (read fallback only)
    val HOME_TAP_ACTION = stringPreferencesKey("home_tap_action")
    val SWIPE_UP_ACTION = stringPreferencesKey("swipe_up_action")

    // Search Providers
    val CONTACTS_SEARCH_ENABLED = booleanPreferencesKey("contacts_search_enabled")
    val FILES_SEARCH_ENABLED = booleanPreferencesKey("files_search_enabled")

    // Hidden Apps
    val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")

    // Prefix Configuration - stored as JSON string
    val PREFIX_CONFIGURATIONS = stringPreferencesKey("prefix_configurations")

    // Dynamic source configuration - stored as JSON array
    val SEARCH_SOURCES = stringPreferencesKey("search_sources")

    // Explicit persisted state marker for search source semantics.
    val SEARCH_SOURCES_STATE = stringPreferencesKey("search_sources_state")
}

/**
 * Persisted state values for search source storage semantics.
 */
internal object SearchSourcesStorageState {
    const val INITIALIZED = "initialized"
}

/**
 * Shared Json instance for settings serialization/deserialization.
 *
 * CONFIGURATION CHOICES:
 * - ignoreUnknownKeys = true:
 *   Allows forward compatibility if a future version adds extra fields.
 * - encodeDefaults = true:
 *   Keeps output deterministic when defaultable values are introduced later.
 */
internal val settingsStorageJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
