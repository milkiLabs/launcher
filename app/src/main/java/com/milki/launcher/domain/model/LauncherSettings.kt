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

/**
 * Search engine options for web search provider.
 */
enum class SearchEngine(val displayName: String, val urlTemplate: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s")
}

/**
 * Display layout options for search results.
 */
enum class SearchResultLayout(val displayName: String) {
    LIST("List"),
    GRID("Grid")
}

/**
 * Home screen tap behavior options.
 */
enum class HomeTapAction(val displayName: String) {
    OPEN_SEARCH("Open search dialog"),
    DO_NOTHING("Do nothing")
}

/**
 * Swipe-up behavior on homescreen.
 */
enum class SwipeUpAction(val displayName: String) {
    OPEN_SEARCH("Open search dialog"),
    OPEN_APP_DRAWER("Open app drawer (not yet implemented)"),
    DO_NOTHING("Do nothing")
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
 * 4. Search Providers - Enable/disable and configure providers
 */
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

    /** Maximum number of recent apps to display */
    val maxRecentApps: Int = 5,

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

    /** What happens when the user taps on the homescreen */
    val homeTapAction: HomeTapAction = HomeTapAction.OPEN_SEARCH,

    /** What happens when the user swipes up on the homescreen */
    val swipeUpAction: SwipeUpAction = SwipeUpAction.OPEN_SEARCH,

    /** Whether pressing home when search has text clears the text first */
    val homeButtonClearsQuery: Boolean = true,

    // ========================================================================
    // SEARCH PROVIDERS
    // ========================================================================

    /** Default search engine for web search (prefix "s") */
    val defaultSearchEngine: SearchEngine = SearchEngine.GOOGLE,

    /** Whether web search provider is enabled */
    val webSearchEnabled: Boolean = true,

    /** Whether contacts search provider is enabled */
    val contactsSearchEnabled: Boolean = true,

    /** Whether YouTube search provider is enabled */
    val youtubeSearchEnabled: Boolean = true,

    /** Whether files search provider is enabled */
    val filesSearchEnabled: Boolean = true,

    // ========================================================================
    // HIDDEN APPS
    // ========================================================================

    /** Package names of apps hidden from search results */
    val hiddenApps: Set<String> = emptySet()
)
