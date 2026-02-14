/**
 * SearchProvider.kt - Configuration for search modes with prefix-based routing
 * 
 * This file defines the SearchProvider data class which allows users to add
 * custom search providers using simple prefixes (e.g., "s" for web search).
 * 
 * users can add new providers by creating new SearchProvider instances with their desired prefix and search logic.
 */

package com.milki.launcher.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SearchProvider defines a searchable category that can be accessed via a prefix.
 * 
 * @property prefix The single character prefix that triggers this provider (e.g., "s", "c", "y")
 TODO: it shouldn't be only a single character
 * @property name Human-readable name shown in the UI (e.g., "Web Search", "Contacts")
 * @property description Short description of what this provider searches
 * @property color Visual indicator color for this provider (shown as bar/hint color)
 * @property icon Icon representing this provider
 * @property search Function that performs the search and returns results
 * 
 * Example usage:
 * ```kotlin
 * val webProvider = SearchProvider(
 *     prefix = "s",
 *     name = "Web Search",
 *     description = "Search the web",
 *     color = Color(0xFF4285F4), // Google blue
 *     icon = Icons.Default.Search,
 *     search = { query -> searchWeb(query) }
 * )
 * ```
 */
data class SearchProvider(
    /**
     * Single character prefix that activates this provider.
     * When user types "s query", the "s" provider is activated.
     */
    val prefix: String,
    
    /**
     * Display name shown in UI hints and indicators.
     */
    val name: String,
    
    /**
     * Short description shown below the search field.
     */
    val description: String,
    
    /**
     * Color used for visual indicators (progress bar, hint text, icons).
     * Helps users quickly identify which mode is active.
     */
    val color: Color,
    
    /**
     * Icon representing this search type.
     */
    val icon: ImageVector,
    
    /**
     * The search function that takes a query string and returns a list of SearchResults.
     * This is where the actual search logic lives.
     * 
     * @param query The search query (everything after the prefix and space)
     * @return List of SearchResult objects to display
     */
    val search: suspend (query: String) -> List<SearchResult>
)

/**
 * Predefined search providers for the launcher.
 * 
 * These are hardcoded for now but structured to be easily extensible.
 * Users can add their own providers by adding to this list.
 * 
 * Current providers:
 * - "s": Web Search (Google, DuckDuckGo)
 * - "c": Contacts (device contacts)
 * - "y": YouTube Search
 */
object SearchProviders {
    
    /**
     * Web Search Provider
     * Prefix: "s"
     * Examples: "s cats", "s weather today"
     * 
     * Returns multiple web search options (Google, DuckDuckGo).
     */
    fun webProvider(
        onSearchWeb: (query: String, engine: String) -> Unit
    ) = SearchProvider(
        prefix = "s",
        name = "Web Search",
        description = "Search the web",
        color = Color(0xFF4285F4), // Google Blue
        icon = Icons.Default.Search,
        search = { query ->
            listOf(
                WebSearchResult(
                    title = "Search \"$query\" on Google",
                    url = "https://www.google.com/search?q=$query",
                    engine = "Google",
                    onClick = { onSearchWeb(query, "google") }
                ),
                WebSearchResult(
                    title = "Search \"$query\" on DuckDuckGo",
                    url = "https://duckduckgo.com/?q=$query",
                    engine = "DuckDuckGo",
                    onClick = { onSearchWeb(query, "duckduckgo") }
                )
            )
        }
    )
    
    /**
     * YouTube Search Provider
     * Prefix: "y"
     * Examples: "y lofi music", "y cooking tutorials"
     * 
     * Returns YouTube search result.
     */
    fun youtubeProvider(
        onSearchYouTube: (query: String) -> Unit
    ) = SearchProvider(
        prefix = "y",
        name = "YouTube",
        description = "Search YouTube videos",
        color = Color(0xFFFF0000), // YouTube Red
        icon = Icons.Default.PlayArrow,
        search = { query ->
            listOf(
                WebSearchResult(
                    title = "Search \"$query\" on YouTube",
                    url = "https://www.youtube.com/results?search_query=$query",
                    engine = "YouTube",
                    onClick = { onSearchYouTube(query) }
                )
            )
        }
    )
    
    /**
     * Contacts Search Provider
     * Prefix: "c"
     * Examples: "c mom", "c john"
     * 
     * Returns matching contacts from the device.
     * Requires READ_CONTACTS permission to function.
     * 
     * If permission is not granted, returns a PermissionRequestResult
     * that shows a button to request permission.
     * 
     * @param hasPermission Whether contacts permission is currently granted
     * @param onRequestPermission Called when user clicks the permission request button
     * @param searchContacts Function to search contacts (only called if permission granted)
     * @param onCallContact Called when user clicks a contact to make a phone call
     */
    fun contactsProvider(
        hasPermission: Boolean,
        onRequestPermission: () -> Unit,
        searchContacts: suspend (query: String) -> List<Contact>,
        onCallContact: (contact: Contact) -> Unit
    ) = SearchProvider(
        prefix = "c",
        name = "Contacts",
        description = "Search your contacts",
        color = Color(0xFF34A853), // Contacts Green
        icon = Icons.Default.Person,
        search = { query ->
            if (!hasPermission) {
                // Permission not granted - show permission request button
                listOf(
                    PermissionRequestResult(
                        permission = "android.permission.READ_CONTACTS",
                        providerPrefix = "c",
                        message = "Contacts permission required to search contacts",
                        buttonText = "Grant Permission",
                        onClick = onRequestPermission
                    )
                )
            } else {
                // Permission granted - perform actual contact search
                val contacts = searchContacts(query)
                
                if (contacts.isEmpty()) {
                    // No contacts found
                    listOf(
                        ContactSearchResult(
                            title = "No contacts found for \"$query\"",
                            contact = Contact(
                                id = -1,
                                displayName = "No results",
                                phoneNumbers = emptyList(),
                                emails = emptyList(),
                                photoUri = null,
                                lookupKey = "none"
                            ),
                            onClick = { }
                        )
                    )
                } else {
                    // Return found contacts
                    contacts.map { contact ->
                        ContactSearchResult(
                            title = contact.displayName,
                            contact = contact,
                            onClick = { onCallContact(contact) }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Sealed class representing different types of search results.
 * 
 * Using a sealed class allows us to have different data for different result types
 * while still treating them uniformly in lists.
 */
sealed class SearchResult {
    /**
     * Unique identifier for this result (used for LazyColumn keys).
     */
    abstract val id: String
    
    /**
     * Title displayed for this result.
     */
    abstract val title: String
    
    /**
     * Callback invoked when user taps this result.
     */
    abstract val onClick: () -> Unit
}

/**
 * Represents a web search result.
 * Used for Google, DuckDuckGo, YouTube, and other web searches.
 */
data class WebSearchResult(
    override val title: String,
    val url: String,
    val engine: String,
    override val onClick: () -> Unit
) : SearchResult() {
    /**
     * Generate unique ID from engine and URL.
     */
    override val id: String = "web_${engine}_${url.hashCode()}"
}

/**
 * Represents a contact search result.
 * 
 * Contains the actual Contact object with all contact details.
 * When clicked, initiates a phone call to the contact's primary number.
 */
data class ContactSearchResult(
    override val title: String,
    val contact: Contact,
    override val onClick: () -> Unit
) : SearchResult() {
    /**
     * Generate unique ID from contact ID and lookup key.
     */
    override val id: String = "contact_${contact.id}_${contact.lookupKey}"
}

/**
 * Represents a permission request button in search results.
 * 
 * Shown when a search provider requires a permission that hasn't been granted.
 * Clicking this will trigger the permission request flow.
 */
data class PermissionRequestResult(
    val permission: String,
    val providerPrefix: String,
    val message: String,
    val buttonText: String,
    override val onClick: () -> Unit
) : SearchResult() {
    override val title: String = message
    override val id: String = "permission_${permission}_${providerPrefix}"
}

/**
 * Represents an installed app result.
 * Wraps AppInfo to fit the SearchResult hierarchy.
 */
data class AppSearchResult(
    val appInfo: AppInfo,
    override val onClick: () -> Unit
) : SearchResult() {
    override val title: String = appInfo.name
    override val id: String = "app_${appInfo.packageName}"
}

/**
 * Parses the search query to detect provider prefixes.
 * 
 * IMPORTANT: Provider mode ONLY activates when user types "prefix " (with space).
 * Typing just "s" will search apps, not activate web search mode.
 * 
 * Format: "prefix query" or just "query"
 * Examples:
 * - "s cats" → Provider "s" (Web), query "cats" ✓ Activates web mode
 * - "s" → No provider, searches apps for "s" ✗ Does NOT activate web mode
 * - "y music" → Provider "y" (YouTube), query "music"
 * - "calculator" → No provider, search apps for "calculator"
 * 
 * @param input The raw user input from the search field
 * @param providers List of available providers to match against
 * @return Pair of (detected provider or null, actual query string)
 */
fun parseSearchQuery(
    input: String,
    providers: List<SearchProvider>
): Pair<SearchProvider?, String> {
    // If input is empty, no provider
    if (input.isEmpty()) {
        return Pair(null, "")
    }
    
    // Check if input starts with a provider prefix followed by a space
    // This must be checked BEFORE trimming to detect "s " vs "s"
    for (provider in providers) {
        val prefixWithSpace = provider.prefix + " "
        if (input.startsWith(prefixWithSpace)) {
            // Found a provider prefix with space after it
            // Extract everything after "prefix " and trim it
            val query = input.substring(prefixWithSpace.length)
            return Pair(provider, query)
        }
    }
    
    // No provider prefix with space found
    // Check if it's just a single prefix character (without space)
    // In that case, DON'T activate provider mode - let user finish typing
    val trimmed = input.trim()
    if (trimmed.length == 1) {
        val potentialPrefix = trimmed
        val provider = providers.find { it.prefix == potentialPrefix }
        if (provider != null) {
            // User typed just the prefix, no space yet
            // Return as app search (they might be searching for an app that starts with this letter)
            return Pair(null, input)
        }
    }
    
    // Treat entire input as app search query
    return Pair(null, input)
}
