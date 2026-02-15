/**
 * QueryParser.kt - Parses search queries to detect provider prefixes
 *
 * This file contains pure functions for parsing search queries.
 * It separates the parsing logic from the search execution,
 * following the Single Responsibility Principle.
 *
 * HOW PREFIX DETECTION WORKS:
 * - A prefix only activates when followed by a space: "s cats" → web search for "cats"
 * - Typing just "s" searches apps that start with "s", NOT web search
 * - This prevents accidentally triggering providers while typing app names
 *
 * EXAMPLES:
 * - "s cats"    → Provider "s" (Web), query "cats"
 * - "s"         → No provider, searches apps for "s"
 * - "y music"   → Provider "y" (YouTube), query "music"
 * - "calculator" → No provider, search apps for "calculator"
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.model.SearchProviderConfig

/**
 * Result of parsing a search query.
 *
 * @property provider The detected provider, or null if no prefix matched
 * @property query The actual search query (without prefix)
 * @property config The provider's configuration for UI display
 */
data class ParsedQuery(
    val provider: SearchProvider?,
    val query: String,
    val config: SearchProviderConfig?
)

/**
 * Parses the search query to detect provider prefixes.
 *
 * IMPORTANT: Provider mode ONLY activates when user types "prefix " (with space).
 * Typing just "s" will search apps, not activate web search mode.
 *
 * Format: "prefix query" or just "query"
 *
 * @param input The raw user input from the search field
 * @param registry The registry of available providers
 * @return ParsedQuery containing the detected provider and the actual query
 */
fun parseSearchQuery(
    input: String,
    registry: SearchProviderRegistry
): ParsedQuery {
    // If input is empty, no provider
    if (input.isEmpty()) {
        return ParsedQuery(
            provider = null,
            query = "",
            config = null
        )
    }

    // Check if input starts with a provider prefix followed by a space
    // This must be checked BEFORE trimming to detect "s " vs "s"
    for (provider in registry.getAllProviders()) {
        val prefixWithSpace = provider.config.prefix + " "
        if (input.startsWith(prefixWithSpace)) {
            // Found a provider prefix with space after it
            // Extract everything after "prefix " and trim it
            val query = input.substring(prefixWithSpace.length)
            return ParsedQuery(
                provider = provider,
                query = query,
                config = provider.config
            )
        }
    }

    // No provider prefix with space found
    // Check if it's just a single prefix character (without space)
    // In that case, DON'T activate provider mode - let user finish typing
    val trimmed = input.trim()
    if (trimmed.length == 1) {
        if (registry.hasProvider(trimmed)) {
            // User typed just the prefix, no space yet
            // Return as app search (they might be searching for an app that starts with this letter)
            return ParsedQuery(
                provider = null,
                query = input,
                config = null
            )
        }
    }

    // Treat entire input as app search query
    return ParsedQuery(
        provider = null,
        query = input,
        config = null
    )
}

/**
 * Parses the search query using a list of providers.
 *
 * Convenience overload for when you have a list of providers
 * instead of a registry.
 *
 * @param input The raw user input from the search field
 * @param providers List of available providers to match against
 * @return ParsedQuery containing the detected provider and the actual query
 */
fun parseSearchQuery(
    input: String,
    providers: List<SearchProvider>
): ParsedQuery {
    // If input is empty, no provider
    if (input.isEmpty()) {
        return ParsedQuery(
            provider = null,
            query = "",
            config = null
        )
    }

    // Check if input starts with a provider prefix followed by a space
    for (provider in providers) {
        val prefixWithSpace = provider.config.prefix + " "
        if (input.startsWith(prefixWithSpace)) {
            val query = input.substring(prefixWithSpace.length)
            return ParsedQuery(
                provider = provider,
                query = query,
                config = provider.config
            )
        }
    }

    // No provider prefix with space found
    val trimmed = input.trim()
    if (trimmed.length == 1) {
        val matchingProvider = providers.find { it.config.prefix == trimmed }
        if (matchingProvider != null) {
            return ParsedQuery(
                provider = null,
                query = input,
                config = null
            )
        }
    }

    return ParsedQuery(
        provider = null,
        query = input,
        config = null
    )
}
