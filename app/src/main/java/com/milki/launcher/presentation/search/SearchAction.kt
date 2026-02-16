/**
 * SearchAction.kt - Sealed class for navigation and action events
 *
 * This file defines all possible actions that can result from search interactions.
 * Instead of passing callbacks through multiple layers, we emit SearchAction events
 * that the Activity observes and handles.
 *
 * WHY SEALED CLASSES INSTEAD OF CALLBACKS?
 * - Type-safe: All possible actions are known at compile time
 * - Testable: Can verify that correct actions are emitted
 * - Decoupled: UI doesn't need to know HOW to perform actions
 * - Lifecycle-aware: Easy to handle with StateFlow/LiveData
 *
 * ARCHITECTURE FLOW:
 * ┌─────────────────┐     SearchAction      ┌─────────────────┐
 * │  SearchViewModel │ ──────────────────► │  MainActivity    │
 * │  (emits actions) │                      │  (handles them) │
 * └─────────────────┘                       └─────────────────┘
 *
 * The ViewModel emits actions via StateFlow<SearchAction?>
 * The Activity observes and performs the actual navigation.
 */

package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument

/**
 * Sealed class representing all possible search-related actions.
 *
 * These are "side effects" or "navigation events" that occur as a result
 * of user interactions with search results. The ViewModel decides what
 * action to take, and the Activity executes it.
 *
 * Using a sealed class ensures:
 * - Exhaustive when expressions (no missed cases)
 * - Type-safe payload data
 * - Easy testing and logging
 */
sealed class SearchAction {
    /**
     * Launch an installed app.
     *
     * Emitted when user taps an app search result.
     *
     * @property appInfo The app to launch
     */
    data class LaunchApp(val appInfo: AppInfo) : SearchAction()

    /**
     * Open a web search in the browser.
     *
     * Emitted when user taps a web search result.
     *
     * @property url The complete URL to open
     * @property query The original search query
     * @property engine The search engine name (for analytics)
     */
    data class OpenWebSearch(
        val url: String,
        val query: String,
        val engine: String
    ) : SearchAction()

    /**
     * Open a YouTube search.
     *
     * Emitted when user taps a YouTube search result.
     * The Activity will try the YouTube app first, then browser fallback.
     *
     * @property query The search query for YouTube
     */
    data class OpenYouTubeSearch(val query: String) : SearchAction()

    /**
     * Open a URL directly in the browser.
     *
     * Emitted when the user types a valid URL and taps the URL result.
     * This provides a shortcut for navigating to websites without
     * needing to use the "s " prefix for web search.
     *
     * @property url The complete URL to open (normalized with https:// if needed)
     */
    data class OpenUrl(val url: String) : SearchAction()

    /**
     * Make a phone call to a contact.
     *
     * Emitted when user taps a contact result.
     * Opens the dialer with the contact's number pre-filled.
     *
     * @property contact The contact to call
     * @property phoneNumber The phone number to dial (extracted for convenience)
     */
    data class CallContact(
        val contact: Contact,
        val phoneNumber: String
    ) : SearchAction()

    /**
     * Request contacts permission.
     *
     * Emitted when user tries to search contacts without permission,
     * or taps the permission request result.
     */
    data object RequestContactsPermission : SearchAction()

    /**
     * Request files/storage permission.
     *
     * Emitted when user tries to search files without permission
     * (only needed on Android 10 and below), or taps the permission
     * request result.
     */
    data object RequestFilesPermission : SearchAction()

    /**
     * Open a file/document with an appropriate app.
     *
     * Emitted when user taps a file search result.
     * Opens the file with the best available app (PDF viewer, word processor, etc.).
     *
     * @property file The file to open
     */
    data class OpenFile(val file: FileDocument) : SearchAction()

    /**
     * Close the search dialog.
     *
     * Emitted when search is cancelled or after an action is performed.
     */
    data object CloseSearch : SearchAction()

    /**
     * Clear the search query.
     *
     * Emitted when user wants to start a new search.
     */
    data object ClearQuery : SearchAction()
}

/**
 * Extension to check if an action should close the search.
 *
 * Most actions close the search after execution, but some (like
 * requesting permission) should keep it open.
 */
fun SearchAction.shouldCloseSearch(): Boolean {
    return when (this) {
        is SearchAction.LaunchApp -> true
        is SearchAction.OpenWebSearch -> true
        is SearchAction.OpenYouTubeSearch -> true
        is SearchAction.OpenUrl -> true
        is SearchAction.CallContact -> true
        is SearchAction.OpenFile -> true
        is SearchAction.RequestContactsPermission -> false
        is SearchAction.RequestFilesPermission -> false
        is SearchAction.CloseSearch -> true
        is SearchAction.ClearQuery -> false
    }
}
