package com.milki.launcher.data.repository.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milki.launcher.core.util.lenientJson
import com.milki.launcher.domain.model.FileSearchExtensionConfig
import com.milki.launcher.domain.model.LauncherInteractionCatalog
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchLayout
import com.milki.launcher.domain.model.SearchSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single-file storage layer for launcher settings: DataStore instance,
 * schema (keys + serialized representations), and read/write codecs.
 *
 * Writes are unconditional; DataStore already skips disk writes when the
 * resulting [Preferences] snapshot is unchanged.
 */

// ========================================================================
// SCHEMA
// ========================================================================

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
 * Shared serialized representation for trigger -> launch target payloads.
 */
internal typealias SerializedTriggerTargets = Map<String, LauncherTriggerTarget>

/**
 * Persisted state values for search source storage semantics.
 */
internal object SearchSourcesStorageState {
    const val INITIALIZED = "initialized"
}

/**
 * Centralized preference keys for launcher settings persistence.
 *
 * Keeping all keys together makes the storage schema easier to audit and
 * reduces coupling between repository read/write paths.
 */
internal object SettingsPreferenceKeys {
    // Home Screen
    val TRIGGER_ACTIONS = stringPreferencesKey("trigger_actions")
    val TRIGGER_TARGETS = stringPreferencesKey("trigger_targets")

    // Legacy Home Screen keys (read fallback only)
    val HOME_TAP_ACTION = stringPreferencesKey("home_tap_action")
    val SWIPE_UP_ACTION = stringPreferencesKey("swipe_up_action")

    // Search Providers
    val SEARCH_LAYOUT = stringPreferencesKey("search_layout")
    val CONTACTS_SEARCH_ENABLED = booleanPreferencesKey("contacts_search_enabled")
    val FILES_SEARCH_ENABLED = booleanPreferencesKey("files_search_enabled")

    // Prefix Configuration - stored as JSON string
    val PREFIX_CONFIGURATIONS = stringPreferencesKey("prefix_configurations")

    // Dynamic source configuration - stored as JSON array
    val SEARCH_SOURCES = stringPreferencesKey("search_sources")

    // Explicit persisted state marker for search source semantics.
    val SEARCH_SOURCES_STATE = stringPreferencesKey("search_sources_state")

    // ID of the user-selected default search engine source.
    val DEFAULT_SEARCH_SOURCE_ID = stringPreferencesKey("default_search_source_id")

    // File search extension configuration - stored as JSON string
    val FILE_SEARCH_EXTENSION_CONFIG = stringPreferencesKey("file_search_extension_config")
}

/**
 * Shared Json instance for settings serialization/deserialization.
 *
 * Derived from the lenient baseline (core/util JsonHelpers):
 * - ignoreUnknownKeys = true:
 *   Allows forward compatibility if a future version adds extra fields.
 * - encodeDefaults = true:
 *   Keeps output deterministic when defaultable values are introduced later.
 */
internal val settingsStorageJson: Json = lenientJson()

/**
 * DataStore instance for launcher settings, scoped to application context.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

// ========================================================================
// READ
// ========================================================================

internal fun Preferences.toLauncherSettings(): LauncherSettings {
    val defaults = LauncherSettings()
    return LauncherSettings(
        triggerActions = parseTriggerActions(),
        triggerTargets = parseTriggerTargets(),
        searchLayout = this[SettingsPreferenceKeys.SEARCH_LAYOUT]
            ?.let { storedValue ->
                runCatching { SearchLayout.valueOf(storedValue) }.getOrNull()
            }
            ?: defaults.searchLayout,
        contactsSearchEnabled =
            this[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED]
                ?: defaults.contactsSearchEnabled,
        filesSearchEnabled =
            this[SettingsPreferenceKeys.FILES_SEARCH_ENABLED]
                ?: defaults.filesSearchEnabled,
        searchSources = parseStoredSearchSources(),
        prefixConfigurations = parsePrefixConfigurations(
            this[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS]
        ),
        defaultSearchSourceId = this[SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID],
        fileSearchExtensionConfig = parseFileSearchExtensionConfig(
            this[SettingsPreferenceKeys.FILE_SEARCH_EXTENSION_CONFIG]
        )
    )
}

internal fun Preferences.parseStoredSearchSources(): List<SearchSource> {
    val isInitialized =
        this[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] == SearchSourcesStorageState.INITIALIZED

    return parseSearchSources(
        json = this[SettingsPreferenceKeys.SEARCH_SOURCES],
        isInitialized = isInitialized
    )
}

internal fun Preferences.parseTriggerActions(): Map<LauncherTrigger, LauncherTriggerAction> {
    val storedJson = this[SettingsPreferenceKeys.TRIGGER_ACTIONS]
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

    return mergeWithDefaultTriggerActions(parseLegacyTriggerActions())
}

private fun Preferences.parseLegacyTriggerActions(): Map<LauncherTrigger, LauncherTriggerAction> {
    val legacyMappings = mutableMapOf<LauncherTrigger, LauncherTriggerAction>()
    this[SettingsPreferenceKeys.HOME_TAP_ACTION]?.let { storedActionName ->
        runCatching { LauncherTriggerAction.valueOf(storedActionName) }
            .getOrNull()
            ?.let { legacyMappings[LauncherTrigger.HOME_TAP] = it }
    }
    this[SettingsPreferenceKeys.SWIPE_UP_ACTION]?.let { storedActionName ->
        runCatching { LauncherTriggerAction.valueOf(storedActionName) }
            .getOrNull()
            ?.let { legacyMappings[LauncherTrigger.HOME_SWIPE_UP] = it }
    }
    return legacyMappings
}

internal fun mergeWithDefaultTriggerActions(
    overrides: Map<LauncherTrigger, LauncherTriggerAction>
): Map<LauncherTrigger, LauncherTriggerAction> {
    return LauncherTrigger.entries.associateWith { trigger ->
        overrides[trigger] ?: LauncherInteractionCatalog.defaultActionFor(trigger)
    }
}

internal fun Preferences.parseTriggerTargets(): Map<LauncherTrigger, LauncherTriggerTarget> {
    val storedJson = this[SettingsPreferenceKeys.TRIGGER_TARGETS]
    if (storedJson.isNullOrBlank()) return emptyMap()

    return runCatching {
        val decoded: SerializedTriggerTargets = settingsStorageJson.decodeFromString(storedJson)
        decoded.mapNotNull { (triggerName, target) ->
            val trigger = runCatching { LauncherTrigger.valueOf(triggerName) }.getOrNull()
            if (trigger == null) null else trigger to target
        }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun parsePrefixConfigurations(json: String?): ProviderPrefixConfiguration {
    if (json.isNullOrBlank()) {
        return emptyMap()
    }

    return runCatching {
        val serializedConfiguration: SerializedPrefixConfiguration =
            settingsStorageJson.decodeFromString(json)

        serializedConfiguration
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, prefixes) -> PrefixConfig(prefixes) }
    }.getOrElse {
        emptyMap()
    }
}

internal fun serializePrefixConfigurations(config: ProviderPrefixConfiguration): String {
    if (config.isEmpty()) {
        return "{}"
    }

    val serializedConfiguration: SerializedPrefixConfiguration =
        config
            .mapValues { (_, prefixConfig) -> prefixConfig.prefixes }
            .filterValues { it.isNotEmpty() }

    if (serializedConfiguration.isEmpty()) {
        return "{}"
    }

    return settingsStorageJson.encodeToString(serializedConfiguration)
}

internal fun parseSearchSources(
    json: String?,
    isInitialized: Boolean
): List<SearchSource> {
    if (!isInitialized) {
        if (json.isNullOrBlank()) {
            return SearchSource.defaultSources()
        }

        return runCatching {
            val decoded: SerializedSearchSources =
                settingsStorageJson.decodeFromString(json)
            normalizeAndValidateSearchSources(decoded)
        }.getOrElse {
            SearchSource.defaultSources()
        }
    }

    if (json.isNullOrBlank()) {
        return emptyList()
    }

    return runCatching {
        val decoded: SerializedSearchSources =
            settingsStorageJson.decodeFromString(json)
        normalizeAndValidateSearchSources(decoded)
    }.getOrElse {
        emptyList()
    }
}

internal fun serializeSearchSources(sources: List<SearchSource>): String {
    val normalized = normalizeAndValidateSearchSources(sources)
    return settingsStorageJson.encodeToString(normalized)
}

internal fun normalizeAndValidateSearchSources(
    rawSources: List<SearchSource>
): List<SearchSource> {
    val normalized = rawSources.mapIndexed { index, source ->
        val normalizedPrefixes = SearchSource.normalizePrefixes(source.prefixes)

        val safeName = source.name.trim().ifBlank { "Source ${index + 1}" }
        val safeTemplate = if (SearchSource.isValidUrlTemplate(source.urlTemplate)) {
            source.urlTemplate.trim()
        } else {
            "https://www.google.com/search?q={query}"
        }

        val normalizedDefaultPrefixes = SearchSource.normalizePrefixes(source.defaultPrefixes)
            .ifEmpty { normalizedPrefixes }

        source.copy(
            name = safeName,
            urlTemplate = safeTemplate,
            prefixes = normalizedPrefixes,
            accentColorHex = SearchSource.normalizeHexColor(source.accentColorHex),
            defaultPrefixes = normalizedDefaultPrefixes
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

internal fun parseFileSearchExtensionConfig(json: String?): FileSearchExtensionConfig {
    if (json.isNullOrBlank()) {
        return FileSearchExtensionConfig()
    }

    return runCatching {
        settingsStorageJson.decodeFromString<FileSearchExtensionConfig>(json)
    }.getOrElse {
        FileSearchExtensionConfig()
    }
}

internal fun serializeFileSearchExtensionConfig(config: FileSearchExtensionConfig): String {
    return settingsStorageJson.encodeToString(config)
}

// ========================================================================
// WRITE
// ========================================================================

/**
 * Encodes every field of [settings] into [preferences] unconditionally;
 * DataStore skips disk writes when the resulting snapshot is equal.
 */
internal fun MutablePreferences.writeSettings(settings: LauncherSettings) {
    this[SettingsPreferenceKeys.SEARCH_LAYOUT] = settings.searchLayout.name
    this[SettingsPreferenceKeys.CONTACTS_SEARCH_ENABLED] = settings.contactsSearchEnabled
    this[SettingsPreferenceKeys.FILES_SEARCH_ENABLED] = settings.filesSearchEnabled

    writeSearchSources(settings.searchSources)
    writePrefixConfigurations(settings.prefixConfigurations)

    if (settings.defaultSearchSourceId == null) {
        remove(SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID)
    } else {
        this[SettingsPreferenceKeys.DEFAULT_SEARCH_SOURCE_ID] = settings.defaultSearchSourceId
    }

    this[SettingsPreferenceKeys.FILE_SEARCH_EXTENSION_CONFIG] =
        serializeFileSearchExtensionConfig(settings.fileSearchExtensionConfig)

    writeTriggerActions(settings.triggerActions, this)
    writeTriggerTargets(settings.triggerTargets, this)
}

internal fun writeTriggerActions(
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

internal fun writeTriggerTargets(
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

internal fun MutablePreferences.writePrefixConfigurations(configurations: ProviderPrefixConfiguration) {
    if (configurations.isEmpty()) {
        remove(SettingsPreferenceKeys.PREFIX_CONFIGURATIONS)
        return
    }
    this[SettingsPreferenceKeys.PREFIX_CONFIGURATIONS] =
        serializePrefixConfigurations(configurations)
}

internal fun MutablePreferences.writeSearchSources(sources: List<SearchSource>) {
    this[SettingsPreferenceKeys.SEARCH_SOURCES_STATE] =
        SearchSourcesStorageState.INITIALIZED
    this[SettingsPreferenceKeys.SEARCH_SOURCES] =
        serializeSearchSources(sources)
}
