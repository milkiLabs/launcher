/**
 * SearchResult.kt - Pure data models for search results
 *
 * This file defines the search result types as pure data classes without
 * any UI callbacks. This follows the Single Responsibility Principle -
 * these models only hold data, they don't define behavior.
 *
 * WHY NO onClick CALLBACKS?
 * - Domain models should be pure data (no behavior)
 * - Makes testing easier (no need to mock callbacks)
 * - Allows different UI implementations to handle clicks differently
 * - Follows separation of concerns: data vs presentation
 *
 * HOW CLICKS ARE HANDLED:
 * The UI layer (SearchViewModel) emits SearchAction sealed class events
 * when results are clicked. The Activity observes these and performs actions.
 */

package com.milki.launcher.domain.model

/**
 * Sealed class representing different types of search results.
 *
 * Each result type contains all the data needed to display it,
 * but NO callbacks. Click handling is done via the SearchAction
 * sealed class in the presentation layer.
 *
 * This design allows:
 * - Easy testing of search logic
 * - Different UI implementations (Compose, View, etc.)
 * - Serialization for caching or analytics
 */
sealed class SearchResult {
    /**
     * Unique identifier for this result.
     * Used for LazyColumn keys and result deduplication.
     */
    abstract val id: String

    /**
     * Primary text displayed for this result.
     */
    abstract val title: String
}

/**
 * Represents an installed app search result.
 *
 * Contains the full AppInfo object so the UI has all the data it needs
 * to display the app and launch it when clicked.
 *
 * @property appInfo The complete app information including name, package, and launch intent
 */
data class AppSearchResult(
    val appInfo: AppInfo
) : SearchResult() {
    override val title: String = appInfo.name
    override val id: String = "app_${appInfo.packageName}"
}

/**
 * Represents a web search result option.
 *
 * Used for search engines (Google, DuckDuckGo) and other web-based searches.
 * The URL is pre-built so the UI just needs to open it.
 *
 * @property url The complete URL to open when this result is selected
 * @property engine The display name of the search engine (e.g., "Google", "DuckDuckGo")
 * @property query The original search query that will be searched
 */
data class WebSearchResult(
    override val title: String,
    val url: String,
    val engine: String,
    val query: String
) : SearchResult() {
    override val id: String = "web_${engine}_${url.hashCode()}"
}

/**
 * Represents a contact search result.
 *
 * Contains the full Contact object so the UI can display all details
 * (name, phone, photo) and initiate actions (call, SMS) when clicked.
 *
 * @property contact The complete contact information
 */
data class ContactSearchResult(
    val contact: Contact
) : SearchResult() {
    override val title: String = contact.displayName
    override val id: String = "contact_${contact.id}_${contact.lookupKey}"
}

/**
 * Represents a permission request placeholder in search results.
 *
 * Shown when a search provider requires a permission that hasn't been granted.
 * This is a UI-only result type - it doesn't represent searchable content.
 *
 * When the user clicks this result, the UI should request the permission.
 *
 * @property permission The Android permission string (e.g., "android.permission.READ_CONTACTS")
 * @property providerPrefix The prefix that triggered this provider (for display purposes)
 * @property message Human-readable explanation of why permission is needed
 * @property buttonText Text for the action button
 */
data class PermissionRequestResult(
    val permission: String,
    val providerPrefix: String,
    val message: String,
    val buttonText: String
) : SearchResult() {
    override val title: String = message
    override val id: String = "permission_${permission}_${providerPrefix}"
}

/**
 * Represents a YouTube search result.
 *
 * YouTube searches are handled specially because we try to open
 * the YouTube app first, falling back to browser if not installed.
 *
 * @property query The search query for YouTube
 */
data class YouTubeSearchResult(
    val query: String
) : SearchResult() {
    override val title: String = "Search \"$query\" on YouTube"
    override val id: String = "youtube_${query.hashCode()}"
}
