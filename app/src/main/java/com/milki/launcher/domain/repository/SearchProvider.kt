/**
 * SearchProvider.kt - Interface for pluggable search providers
 *
 * This interface defines the contract for all search providers. Each provider
 * is responsible for a specific search type (apps, web, contacts, etc.).
 *
 * PLUGIN ARCHITECTURE:
 * This follows the Plugin Pattern from Clean Architecture:
 * - The domain layer defines the interface (this file)
 * - The data layer provides implementations
 * - New providers can be added without modifying existing code
 *
 *
 * HOW TO ADD A NEW SEARCH PROVIDER:
 * 1. Create a class implementing SearchProvider
 * 2. Implement the config property with display settings
 * 3. Implement the search() function with your logic
 * 4. Register the provider in SearchProviderRegistry
 *
 * Example:
 * ```kotlin
 * class RedditSearchProvider : SearchProvider {
 *     override val config = SearchProviderConfig(
 *         prefix = "r",
 *         name = "Reddit",
 *         description = "Search Reddit",
 *         color = Color(0xFFFF4500),
 *         icon = Icons.Default.Forum
 *     )
 *
 *     override suspend fun search(query: String): List<SearchResult> {
 *         // Return RedditSearchResult objects
 *     }
 * }
 * ```
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult

/**
 * Interface for pluggable search providers.
 *
 * Each search provider handles a specific search domain:
 * - AppSearchProvider: Searches installed apps
 * - WebSearchProvider: Provides web search options
 * - ContactsSearchProvider: Searches device contacts
 * - YouTubeSearchProvider: Provides YouTube search
 *
 * Providers are responsible for:
 * 1. Defining their prefix and display configuration
 * 2. Executing searches and returning results
 */
interface SearchProvider {
    /**
     * Configuration for this provider's visual appearance.
     * Contains prefix, name, description, color, and icon.
     */
    val config: SearchProviderConfig

    /**
     * Execute a search for the given query.
     *
     * This is a suspend function to allow for async operations
     * (e.g., database queries for contacts).
     *
     * @param query The search query (already parsed, without prefix)
     * @return List of SearchResult objects to display
     */
    suspend fun search(query: String): List<SearchResult>
}
