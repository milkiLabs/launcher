/**
 * QueryParser.kt - Parses search queries to detect provider prefixes
 *
 * This file contains pure functions for parsing search queries.
 * It separates the parsing logic from the search execution.
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
 * Parses the search query using a list of providers.
 *
 * This is the main implementation that contains all the parsing logic.
 * It accepts a list of providers directly, making it flexible and reusable.
 *
 * IMPORTANT: Provider mode ONLY activates when user types "prefix " (with space).
 * Typing just "s" will search apps, not activate web search mode.
 *
 * Format: "prefix query" or just "query"
 *
 * HOW IT WORKS:
 * 1. First, it checks if the input starts with any provider prefix followed by a space
 *    (e.g., "s cats" → web search for "cats")
 * 2. If no prefix+space is found, it checks if the user typed just a single prefix character
 *    (e.g., "s" without space). In this case, it treats it as an app search to avoid
 *    accidentally triggering provider mode while typing app names.
 * 3. If no conditions match, the entire input is treated as an app search query.
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
    // Empty input means user cleared the search field or hasn't started typing
    if (input.isEmpty()) {
        return ParsedQuery(
            provider = null,
            query = "",
            config = null
        )
    }

    // Check if input starts with a provider prefix followed by a space
    // This is the main trigger for activating provider mode
    // Example: "s cats" activates web search for "cats"
    // The space is required to prevent accidental activation while typing app names
    for (provider in providers) {
        // Build the prefix pattern (e.g., "s ", "c ", "y ")
        val prefixWithSpace = provider.config.prefix + " "

        // Check if input starts with this provider's prefix followed by space
        if (input.startsWith(prefixWithSpace)) {
            // Found a match! Extract the actual query part (everything after "prefix ")
            val query = input.substring(prefixWithSpace.length)

            // Return result with the matched provider and extracted query
            return ParsedQuery(
                provider = provider,
                query = query,
                config = provider.config
            )
        }
    }

    // No provider prefix with space found
    // Now check if user typed just a single prefix character (without space)
    // Example: user typed "s" but hasn't typed space yet
    // We DON'T activate provider mode here - they might be searching for an app like "Settings"
    val trimmed = input.trim()
    if (trimmed.length == 1) {
        // Look for a provider that has this single character as its prefix
        val matchingProvider = providers.find { it.config.prefix == trimmed }

        if (matchingProvider != null) {
            // User typed just the prefix without space
            // Return as app search (they might be searching for an app that starts with this letter)
            // The provider will be activated once they add a space
            return ParsedQuery(
                provider = null,
                query = input,
                config = null
            )
        }
    }

    // No provider prefix detected at all
    // Treat entire input as a regular app search query
    return ParsedQuery(
        provider = null,
        query = input,
        config = null
    )
}

/**
 * Parses the search query using a provider registry.
 *
 * This is a convenience overload that accepts a SearchProviderRegistry instead of a list.
 * It delegates to the main implementation by extracting the list of providers from the registry.
 *
 * Use this overload when you already have a SearchProviderRegistry instance available,
 * such as when parsing queries in ViewModels that receive the registry via dependency injection.
 *
 * @param input The raw user input from the search field
 * @param registry The registry containing all available providers
 * @return ParsedQuery containing the detected provider and the actual query
 */
fun parseSearchQuery(
    input: String,
    registry: SearchProviderRegistry
): ParsedQuery {
    // Delegate to the main implementation by converting registry to list
    // getAllProviders() returns all registered providers as a List<SearchProvider>
    return parseSearchQuery(input, registry.getAllProviders())
}
