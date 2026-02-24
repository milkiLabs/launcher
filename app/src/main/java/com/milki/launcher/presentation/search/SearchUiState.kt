/**
 * SearchUiState.kt - UI state for the search screen
 *
 * This file defines the complete UI state for the search feature.
 * Using a data class for state ensures:
 * - Immutable state (no accidental mutations)
 * - Easy testing (compare state snapshots)
 * - Clear documentation of what the UI needs
 *
 * STATE VS EVENT:
 * - State: Current values that the UI displays (query, results, etc.)
 * - Events: One-time occurrences that trigger actions (SearchResultAction)
 *
 * The UI observes State, and emits Actions in response to user input.
 *
 * NOTE: Permission-requiring actions (like DialContact) are handled by
 * ActionExecutor, not via state. This simplifies the state management.
 */

package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult

/**
 * Complete UI state for the search screen.
 *
 * This is a single source of truth for everything the search UI needs to render.
 * All fields are immutable - state changes create new instances.
 *
 * @property query The current search query text
 * @property isSearchVisible Whether the search dialog is visible
 * @property results Current search results to display
 * @property activeProviderConfig Configuration of the active provider (null for app search)
 * @property isLoading Whether a search is in progress
 * @property recentApps Recent apps to show when query is empty
 * @property installedApps All installed apps for filtering
 * @property hasContactsPermission Whether contacts permission is granted
 * @property hasFilesPermission Whether files/storage permission is granted
 */
data class SearchUiState(
    val query: String = "",
    val isSearchVisible: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val activeProviderConfig: SearchProviderConfig? = null,
    val isLoading: Boolean = false,
    val recentApps: List<AppInfo> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val hasContactsPermission: Boolean = false,
    val hasFilesPermission: Boolean = false
) {
    /**
     * Whether results are available to display.
     */
    val hasResults: Boolean
        get() = results.isNotEmpty()

    /**
     * Whether the query is empty.
     */
    val isQueryEmpty: Boolean
        get() = query.isBlank()

    /**
     * The actual search query (without provider prefix).
     * For UI display purposes.
     */
    val displayQuery: String
        get() = if (activeProviderConfig != null) {
            query.removePrefix("${activeProviderConfig.prefix} ")
        } else {
            query
        }

    /**
     * Placeholder text for the search field.
     * Changes based on active provider.
     */
    val placeholderText: String
        get() = when (activeProviderConfig?.prefix) {
            "s" -> "Search the web..."
            "c" -> "Search contacts..."
            "y" -> "Search YouTube..."
            "f" -> "Search files..."
            else -> "Search apps..."
        }

    /**
     * Whether the contacts permission prompt should be shown.
     */
    val showPermissionPrompt: Boolean
        get() = activeProviderConfig?.prefix == "c" && !hasContactsPermission

    /**
     * Hint text for available prefixes.
     */
    val prefixHint: String
        get() = "Prefix shortcuts:\ns - Web search\nc - Contacts\nf - Files\ny - YouTube"
}
