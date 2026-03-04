/**
 * SearchUiState.kt - UI state for the search screen
 *
 * This file defines the complete UI state for the search feature.
 * Using a data class for state ensures:
 * - Immutable state (no accidental mutations)
 * - Easy testing (compare state snapshots)
 * - Clear documentation of what the UI needs
 *
 * DESIGN PRINCIPLE - ONLY UI-RELEVANT FIELDS:
 * This class contains ONLY fields that the UI composables actually read.
 * Internal ViewModel data (installed apps list, recent apps list, permission
 * flags) are intentionally excluded — those are implementation details of
 * the search pipeline, not rendering state. Keeping them out of SearchUiState
 * means the UI doesn't recompose when those internal values change.
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

import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult

/**
 * Complete UI state for the search screen.
 *
 * This is a single source of truth for everything the search UI needs to render.
 * All fields are immutable — state changes create new instances.
 *
 * WHAT'S IN HERE (UI needs it):
 * - query: The text shown in the TextField
 * - isSearchVisible: Whether the dialog is open
 * - results: What to display in the results area
 * - activeProviderConfig: Which provider mode is active (provider visuals are mapped in UI)
 * - isLoading: Whether to show the loading bar
 *
 * WHAT'S NOT IN HERE (ViewModel-internal):
 * - installedApps, recentApps: Used internally by the search pipeline to compute
 *   results; no composable reads these directly.
 * - hasContactsPermission, hasFilesPermission: The search providers check permissions
 *   themselves and return PermissionRequestResult when denied; the UI doesn't need
 *   raw permission flags.
 *
 * @property query The current search query text
 * @property isSearchVisible Whether the search dialog is visible
 * @property results Current search results to display
 * @property activeProviderConfig Configuration of the active provider (null for app search)
 * @property isLoading Whether a search is in progress
 */
data class SearchUiState(
    val query: String = "",
    val isSearchVisible: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val activeProviderConfig: SearchProviderConfig? = null,
    val isLoading: Boolean = false
) {
    /**
     * Whether results are available to display.
     * Used by the UI to decide between showing results or empty state.
     */
    val hasResults: Boolean
        get() = results.isNotEmpty()

    /**
     * Placeholder text for the search field.
     * Changes based on active provider so the user knows what mode they're in.
     *
     * Examples:
     * - No provider → "Search apps..."
     * - "s" prefix → "Search the web..."
     * - "c" prefix → "Search contacts..."
     * - "f" prefix → "Search files..."
     * - "y" prefix → "Search YouTube..."
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
     * Hint text for available prefixes.
     * Shown in the empty state to help users discover search modes.
     */
    val prefixHint: String
        get() = "Prefix shortcuts:\ns - Web search\nc - Contacts\nf - Files\ny - YouTube"
}
