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
 * UrlHandlerApp - Represents an app that can handle a URL.
 *
 * When the user types a URL, Android can tell us which installed apps
 * are capable of opening that URL. This data class stores information
 * about each such app.
 *
 * HOW IT WORKS:
 * 1. User types "youtube.com/watch?v=xyz"
 * 2. We ask Android: "Which apps can open this URL?"
 * 3. Android returns: YouTube, Chrome, Firefox, etc.
 * 4. We show the user which app will handle the URL
 *
 * DETERMINING THE DEFAULT HANDLER:
 * Android resolves the "default" app based on:
 * - User's previously set default (if any)
 * - App capabilities (e.g., YouTube can handle youtube.com URLs)
 * - System preferences
 *
 * @property packageName The app's unique package identifier (e.g., "com.google.android.youtube")
 * @property activityName The specific activity that will handle the URL
 * @property label The human-readable app name (e.g., "YouTube")
 * @property isDefault Whether this app is the system's default handler for the URL
 */
data class UrlHandlerApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isDefault: Boolean = false
) {
    /**
     * Unique identifier combining package and activity name.
     * Used for deduplication and comparison.
     */
    val id: String = "${packageName}/${activityName}"
}

/**
 * Represents a direct URL result with app handling information.
 *
 * When the user types a valid URL (e.g., "github.com" or "https://example.com"),
 * this result appears to let them open it directly. The key enhancement is that
 * we now detect which installed apps can handle the URL and show this information
 * to the user.
 *
 * HOW URL HANDLING WORKS:
 * 1. User types a URL-like query (e.g., "youtube.com/watch?v=xyz")
 * 2. We normalize it (add https:// if needed)
 * 3. We ask Android's PackageManager which apps can handle this URL
 * 4. We determine the default handler (or fall back to browser)
 * 5. We show the user: "Open in [App Name]" with the app's icon
 *
 * EXAMPLE SCENARIOS:
 * - "youtube.com/watch?v=xyz" → Shows "Open in YouTube" (if installed)
 * - "twitter.com/user" → Shows "Open in Twitter/X" (if installed)
 * - "github.com/user/repo" → Shows "Open in Browser" (no specific app)
 * - "maps.google.com" → Shows "Open in Google Maps" (if installed)
 *
 * BROWSER FALLBACK:
 * If no specific app can handle the URL, we always have the browser as a fallback.
 * The browser option is always available as a secondary choice.
 *
 * URL VALIDATION:
 * The system detects URLs that:
 * - Start with http:// or https://
 * - Are domain-like patterns (e.g., "example.com", "sub.domain.org")
 * - Have common TLDs (.com, .org, .net, .io, etc.)
 *
 * @property url The complete URL to open (normalized with https:// if needed)
 * @property displayUrl The URL as shown to the user (may be truncated for display)
 * @property handlerApp The app that will open the URL (null = browser fallback)
 * @property browserFallback Always true - browser is always an option
 */
data class UrlSearchResult(
    val url: String,
    val displayUrl: String,
    val handlerApp: UrlHandlerApp? = null,
    val browserFallback: Boolean = true
) : SearchResult() {
    override val title: String = if (handlerApp != null) {
        "Open in ${handlerApp.label}"
    } else {
        "Open $displayUrl"
    }
    override val id: String = "url_${url.hashCode()}_${handlerApp?.id ?: "browser"}"
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
