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
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.search.ActionSuggestion

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
 * - clipboardSuggestion: One smart action suggestion derived from clipboard text
 * - querySuggestion: One smart action suggestion derived from query text
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
 * @property clipboardSuggestion Optional single clipboard-driven suggestion
 * @property querySuggestion Optional single query-driven suggestion
 */
data class SearchUiState(
    val query: String = "",
    val isSearchVisible: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val activeProviderConfig: SearchProviderConfig? = null,
    val isLoading: Boolean = false,
    val autoFocusKeyboard: Boolean = true,
    val clipboardSuggestion: ActionSuggestion? = null,
    val querySuggestion: ActionSuggestion? = null,
    val providerAccentColorById: Map<String, String> = emptyMap(),
    /** Enabled search sources used to render the suggested-action chip row. */
    val suggestedActionSources: List<SearchSource> = emptyList(),
    /** The user's preferred default search engine source ID. */
    val defaultSearchSourceId: String? = null
) {
    /**
     * Whether results are available to display.
     * Used by the UI to decide between showing results or empty state.
     */
    val hasResults: Boolean
        get() = results.isNotEmpty()

    /**
     * Controls clipboard chip visibility.
     *
     * UX RULES:
     * - Show only while dialog is visible
     * - Show only in default app-search mode (no provider prefix)
     * - Show only when query is blank and we have a clipboard suggestion
     */
    val shouldShowClipboardSuggestion: Boolean
        get() = isSearchVisible &&
            activeProviderConfig == null &&
            query.isBlank() &&
            clipboardSuggestion != null

    /**
     * Controls query suggestion chip row visibility.
     *
     * UX RULES:
     * - Show only while dialog is visible
     * - Show only in default app-search mode (no provider prefix)
     * - Show only when query is NOT blank (user is typing)
     *
     * MUTUAL EXCLUSIVITY:
     * This chip row and the clipboard chip are mutually exclusive:
     * - Clipboard chip shows when query is BLANK
     * - Query suggestions show when query is NOT BLANK
     *
     * This prevents UI clutter and provides a clear, focused suggestion.
     */
    val shouldShowQuerySuggestion: Boolean
        get() = isSearchVisible &&
            activeProviderConfig == null &&
            query.isNotBlank() &&
            querySuggestion != null

    /**
     * Ordered list of sources for the suggested-action chip row.
     *
     * The default source (matching [defaultSearchSourceId]) is always first.
     * If no explicit default is set, the first enabled source leads.
     */
    val orderedSuggestedSources: List<SearchSource>
        get() {
            if (suggestedActionSources.isEmpty()) return emptyList()
            val defaultId = defaultSearchSourceId
                ?: return suggestedActionSources
            val default = suggestedActionSources.firstOrNull { it.id == defaultId }
                ?: return suggestedActionSources
            return listOf(default) + suggestedActionSources.filter { it.id != defaultId }
        }

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
        get() = activeProviderConfig?.let { "Search ${it.name}..." } ?: "Search apps..."

    /**
     * Hint text for available prefixes.
     * Shown in the empty state to help users discover search modes.
     */
    val prefixHint: String
        get() = "Tip: type a source prefix followed by a space to search that source"
}
