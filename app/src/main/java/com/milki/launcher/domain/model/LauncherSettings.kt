/**
 * LauncherSettings.kt - Data model for all launcher preferences
 *
 * This file defines the complete set of user-configurable launcher settings.
 * All settings are persisted via DataStore (see SettingsRepositoryImpl).
 *
 * DESIGN DECISIONS:
 * - Immutable data class: Prevents accidental mutations, works well with StateFlow
 * - Enum types: Type-safe option selection, no string comparisons
 * - Sensible defaults: App works out of the box without configuration
 */

package com.milki.launcher.domain.model

import kotlinx.serialization.Serializable

/**
 * ProviderPrefixConfiguration.kt - Type alias for provider prefix settings
 *
 * This type alias makes the code more readable by giving a meaningful name
 * to the Map<String, PrefixConfig> type used throughout the app.
 *
 * The map keys are provider IDs (see ProviderId constants).
 * The map values are PrefixConfig objects containing the list of prefixes.
 */
typealias ProviderPrefixConfiguration = Map<String, PrefixConfig>

/**
 * Display layout options for search results.
 */
@Serializable
enum class SearchResultLayout(val displayName: String) {
    LIST("List"),
    GRID("Grid")
}

/**
 * Home surface trigger types that can be mapped to launcher actions.
 */
@Serializable
enum class LauncherTrigger(val displayName: String) {
    HOME_TAP(displayName = "Homescreen tap"),
    HOME_SWIPE_UP(displayName = "Swipe up")
}

/**
 * Actions that can be assigned to launcher triggers.
 */
@Serializable
enum class LauncherTriggerAction(val displayName: String) {
    OPEN_SEARCH("Open search dialog"),
    OPEN_APP_DRAWER("Open app drawer"),
    DO_NOTHING("Do nothing")
}

/**
 * Central catalog for trigger/action mapping.
 *
 * Add new triggers/actions here and they automatically become available to
 * settings UI and runtime mapping logic.
 */
object LauncherInteractionCatalog {
    val configurableTriggers: List<LauncherTrigger> = LauncherTrigger.entries
    val allActions: List<LauncherTriggerAction> = LauncherTriggerAction.entries

    fun availableActions(): List<LauncherTriggerAction> {
        return allActions
    }

    fun defaultActionFor(trigger: LauncherTrigger): LauncherTriggerAction {
        return when (trigger) {
            LauncherTrigger.HOME_TAP -> LauncherTriggerAction.DO_NOTHING
            LauncherTrigger.HOME_SWIPE_UP -> LauncherTriggerAction.OPEN_APP_DRAWER
        }
    }

    fun defaultTriggerActions(): Map<LauncherTrigger, LauncherTriggerAction> {
        return configurableTriggers.associateWith(::defaultActionFor)
    }
}

/**
 * Complete set of user-configurable launcher settings.
 *
 * All fields have sensible defaults so the launcher works out of the box.
 *
 * CATEGORIES:
 * 1. Search Behavior - How search works
 * 2. Appearance - Visual customizations
 * 3. Home Screen - Home button/gesture behavior
 * 4. Search Providers - Enable local providers and configure dynamic sources
 */
@Serializable
data class LauncherSettings(

    // ========================================================================
    // SEARCH BEHAVIOR
    // ========================================================================

    /** Maximum number of search results to show per query */
    val maxSearchResults: Int = 8,

    /** Whether to auto-focus the keyboard when search dialog opens */
    val autoFocusKeyboard: Boolean = true,

    /** Whether to show recent apps when search is opened with empty query */
    val showRecentApps: Boolean = true,

    /** Whether to close search dialog after launching an app */
    val closeSearchOnLaunch: Boolean = true,

    // ========================================================================
    // APPEARANCE
    // ========================================================================

    /** Layout for search results (list or grid) */
    val searchResultLayout: SearchResultLayout = SearchResultLayout.LIST,

    /** Whether to show the "Tap to search" hint text on homescreen */
    val showHomescreenHint: Boolean = true,

    /** Whether to show app icons in search results */
    val showAppIcons: Boolean = true,

    // ========================================================================
    // HOME SCREEN
    // ========================================================================

    /** Trigger -> action mapping for homescreen interactions */
    val triggerActions: Map<LauncherTrigger, LauncherTriggerAction> =
        LauncherInteractionCatalog.defaultTriggerActions(),

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    /** Whether contacts search provider is enabled */
    val contactsSearchEnabled: Boolean = true,

    /** Whether files search provider is enabled */
    val filesSearchEnabled: Boolean = true,

    /**
     * Dynamic external search sources configured by the user.
     *
     * This is the new unified model for:
     * - Web engines (Google, DuckDuckGo, etc.)
    * - Social/search sources (YouTube, Instagram, ...)
     * - Any custom source created in Settings
     *
     * Behavior notes:
     * - Prefix mode uses each source's `prefixes` values.
     * - `accentColorHex` is used to tint provider visuals in UI.
     */
    val searchSources: List<SearchSource> = SearchSource.defaultSources(),

    // ========================================================================
    // PREFIX CONFIGURATION
    // ========================================================================

    /**
     * Configurable prefixes for each search provider.
     *
     * This map allows users to:
    * 1. Change the default prefix for a local provider (contacts/files)
    * 2. Add multiple prefixes per provider (e.g., "f" and "م" for files)
     * 3. Use prefixes with multiple characters (e.g., "web", "find")
     *
     * The keys are provider IDs (see ProviderId constants):
     * - "contacts" -> Contacts search
     * - "files" -> Files search
     *
     * If a provider is not in this map, its default prefix is used.
     * If the map is empty, all providers use their default prefixes.
     *
     * Example:
     * ```kotlin
    * mapOf(
    *     "files" to PrefixConfig(listOf("f", "م", "find")),
    *     "contacts" to PrefixConfig(listOf("c", "ct"))
    * )
     * ```
     */
    val prefixConfigurations: ProviderPrefixConfiguration = emptyMap(),

    // ========================================================================
    // HIDDEN APPS
    // ========================================================================

    /** Package names of apps hidden from search results */
    val hiddenApps: Set<String> = emptySet()
)

/**
 * Resolves effective action for a trigger with default fallback for missing keys.
 */
fun LauncherSettings.actionForTrigger(trigger: LauncherTrigger): LauncherTriggerAction {
    return triggerActions[trigger] ?: LauncherInteractionCatalog.defaultActionFor(trigger)
}
