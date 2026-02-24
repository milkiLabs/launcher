/**
 * SearchResultAction.kt - Unified action system for search result interactions
 *
 * This file defines a sealed class hierarchy for all actions that can be
 * triggered from search results. This unified approach replaces the previous
 * callback prop drilling pattern with a single action handler.
 *
 * WHY THIS APPROACH:
 * Previously, we had multiple callbacks being passed through multiple UI layers:
 * - onResultClick: (SearchResult) -> Unit
 * - onDialClick: ((Contact, String) -> Unit)?
 *
 * This created tight coupling between all layers and made adding new actions
 * difficult (required modifying 5+ files).
 *
 * With SearchResultAction:
 * - Single action handler passed via CompositionLocal
 * - Easy to add new actions (just add a new subtype)
 * - Clear separation between UI intent and execution
 * - Better testability
 *
 * ARCHITECTURE:
 * ┌─────────────────┐
 * │ UI Component    │
 * │ (emits action)  │
 * └────────┬────────┘
 *          │ SearchResultAction
 *          ▼
 * ┌─────────────────┐
 * │ ActionExecutor  │
 * │ (handles action)│
 * └────────┬────────┘
 *          │
 *          ▼
 * ┌─────────────────┐
 * │ System/Action   │
 * │ (execute intent)│
 * └─────────────────┘
 */

package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.*

/**
 * Sealed class representing all possible actions from search results.
 *
 * Each subtype represents a specific user interaction with a search result.
 * The ActionExecutor handles each action type appropriately, including
 * permission checks for actions that require them.
 *
 * USAGE:
 * ```kotlin
 * val actionHandler = LocalSearchActionHandler.current
 * 
 * // On click
 * actionHandler(SearchResultAction.Tap(result))
 * 
 * // On dial icon click
 * actionHandler(SearchResultAction.DialContact(contact, phoneNumber))
 * ```
 */
sealed class SearchResultAction {
    /**
     * User tapped the main area of a search result.
     *
     * This is the default action for any search result:
     * - App: Launch the app
     * - Contact: Open dialer with number
     * - Web: Open web search URL
     * - URL: Open in browser or handler app
     * - File: Open with appropriate app
     *
     * @property result The search result that was tapped
     */
    data class Tap(val result: SearchResult) : SearchResultAction()
    
    /**
     * User tapped the dial icon on a contact result.
     *
     * This makes a DIRECT call (ACTION_CALL) instead of just opening
     * the dialer (ACTION_DIAL). Requires CALL_PHONE permission.
     *
     * DIFFERENCE FROM Tap(ContactSearchResult):
     * - Tap: Opens dialer (no permission needed)
     * - DialContact: Makes direct call (CALL_PHONE permission needed)
     *
     * @property contact The contact to call
     * @property phoneNumber The phone number to call
     */
    data class DialContact(
        val contact: Contact,
        val phoneNumber: String
    ) : SearchResultAction()
    
    /**
     * User tapped the "Open in Browser" option on a URL result.
     *
     * This explicitly opens the URL in a browser, bypassing any
     * app-specific deep link handling.
     *
     * @property url The URL to open
     */
    data class OpenUrlInBrowser(val url: String) : SearchResultAction()
    
    /**
     * User requested to grant a permission.
     *
     * This is triggered when the user taps a PermissionRequestResult.
     *
     * @property permission The Android permission to request
     * @property providerPrefix The search provider prefix (for context)
     */
    data class RequestPermission(
        val permission: String,
        val providerPrefix: String
    ) : SearchResultAction()
}

/**
 * Extension function to check if an action requires a permission.
 *
 * @return The required permission string, or null if no permission needed
 */
fun SearchResultAction.requiredPermission(): String? = when (this) {
    is SearchResultAction.DialContact -> android.Manifest.permission.CALL_PHONE
    is SearchResultAction.Tap -> null
    is SearchResultAction.OpenUrlInBrowser -> null
    is SearchResultAction.RequestPermission -> null
}

/**
 * Extension function to check if an action should close the search dialog.
 *
 * @return True if the action should close search after execution
 */
fun SearchResultAction.shouldCloseSearch(): Boolean = when (this) {
    is SearchResultAction.Tap -> true
    is SearchResultAction.DialContact -> true
    is SearchResultAction.OpenUrlInBrowser -> true
    is SearchResultAction.RequestPermission -> false
}
