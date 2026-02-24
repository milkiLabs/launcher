/**
 * SearchResult.kt - Pure data models for search results
 *
 * This file defines the search result types
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
    override val id: String = "app_${appInfo.activityName}"
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

/**
 * Represents a direct URL result.
 *
 * When the user types a valid URL (e.g., "github.com" or "https://example.com"),
 * this result appears to let them open it directly in the browser.
 * This provides a shortcut for quickly navigating to websites without
 * needing to use the "s " prefix for web search.
 *
 * URL VALIDATION:
 * The system detects URLs that:
 * - Start with http:// or https://
 * - Are domain-like patterns (e.g., "example.com", "sub.domain.org")
 * - Have common TLDs (.com, .org, .net, .io, etc.)
 *
 * @property url The complete URL to open (normalized with https:// if needed)
 * @property displayUrl The URL as shown to the user (may be truncated for display)
 */
data class UrlSearchResult(
    val url: String,
    val displayUrl: String
) : SearchResult() {
    override val title: String = "Open $displayUrl"
    override val id: String = "url_${url.hashCode()}"
}

/**
 * Represents a file/document search result.
 *
 * This result type is used for searching documents on the device.
 * It supports various document formats including PDF, EPUB, Word,
 * Excel, PowerPoint, and text files.
 *
 * When clicked, the file will be opened with an appropriate app
 * (e.g., PDF viewer, word processor, ebook reader).
 *
 * EXCLUDED FILE TYPES:
 * Images and videos are NOT included in this search to keep
 * the focus on productivity documents. Gallery apps are better
 * suited for media file browsing.
 *
 * @property file The complete file information including name, type, size, and URI
 */
data class FileDocumentSearchResult(
    val file: FileDocument
) : SearchResult() {
    override val title: String = file.name
    override val id: String = "file_${file.id}_${file.name.hashCode()}"
}
