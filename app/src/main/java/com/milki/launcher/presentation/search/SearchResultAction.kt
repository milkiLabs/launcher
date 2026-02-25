/**
 * SearchResultAction.kt - Unified action system for all user interactions
 *
 * This file defines a sealed class hierarchy for all actions that can be
 * triggered from search results and the home screen. This unified approach
 * replaces multiple callback patterns with a single action handler.
 *
 * WHY THIS APPROACH:
 * Previously, we had multiple callback systems:
 * - SearchResultAction for search result clicks
 * - LocalPinAction for pinning items
 * - Duplicate menu state in multiple components
 *
 * With a unified SearchResultAction:
 * - Single action handler for all interactions
 * - Easy to add new actions (just add a new subtype)
 * - Clear separation between UI intent and execution
 * - Better testability
 * - Consistent pattern across all UI components
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
 * Sealed class representing all possible actions from search results and home screen.
 *
 * Each subtype represents a specific user interaction.
 * The ActionExecutor handles each action type appropriately, including
 * permission checks for actions that require them.
 *
 * ACTION CATEGORIES:
 * 1. Tap actions - Primary click on an item
 * 2. Secondary actions - Long-press menu actions (pin, app info, etc.)
 * 3. Permission actions - Request permissions
 */
sealed class SearchResultAction {
    
    // ========================================================================
    // TAP ACTIONS - Primary click on search results
    // ========================================================================
    
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
     * @property contact The contact to call
     * @property phoneNumber The phone number to call
     */
    data class DialContact(
        val contact: Contact,
        val phoneNumber: String
    ) : SearchResultAction()
    
    /**
     * User tapped the "Open in Browser" option on a URL result.
     */
    data class OpenUrlInBrowser(val url: String) : SearchResultAction()
    
    // ========================================================================
    // PIN ACTIONS - Pin/unpin items to home screen
    // ========================================================================
    
    /**
     * User wants to pin an app to the home screen.
     *
     * @property appInfo The app to pin
     */
    data class PinApp(val appInfo: AppInfo) : SearchResultAction()
    
    /**
     * User wants to pin a file to the home screen.
     *
     * @property file The file to pin
     */
    data class PinFile(val file: FileDocument) : SearchResultAction()
    
    /**
     * User wants to remove an item from the home screen.
     *
     * @property itemId The ID of the item to unpin
     */
    data class UnpinItem(val itemId: String) : SearchResultAction()
    
    // ========================================================================
    // APP ACTIONS - Actions specific to apps
    // ========================================================================
    
    /**
     * User wants to open the system app info screen.
     *
     * @property packageName The package name of the app
     */
    data class OpenAppInfo(val packageName: String) : SearchResultAction()
    
    // ========================================================================
    // PERMISSION ACTIONS
    // ========================================================================
    
    /**
     * User requested to grant a permission.
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
 */
fun SearchResultAction.requiredPermission(): String? = when (this) {
    is SearchResultAction.DialContact -> android.Manifest.permission.CALL_PHONE
    else -> null
}

/**
 * Extension function to check if an action should close the search dialog.
 */
fun SearchResultAction.shouldCloseSearch(): Boolean = when (this) {
    is SearchResultAction.Tap -> true
    is SearchResultAction.DialContact -> true
    is SearchResultAction.OpenUrlInBrowser -> true
    is SearchResultAction.PinApp -> false
    is SearchResultAction.PinFile -> false
    is SearchResultAction.UnpinItem -> false
    is SearchResultAction.OpenAppInfo -> false
    is SearchResultAction.RequestPermission -> false
}
