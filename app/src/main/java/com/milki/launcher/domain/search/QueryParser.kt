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
 * MULTIPLE PREFIXES PER PROVIDER:
 * A provider can have multiple prefixes configured. For example:
 * - FilesSearchProvider: ["f", "م", "find"]
 * - Any of these prefixes will trigger the same provider
 * - The parsing logic checks all configured prefixes, not just the default
 *
 * EXAMPLES:
 * - "s cats"    → Provider "s" (Web), query "cats"
 * - "م files"   → Provider "م" (Files, Arabic), query "files"
 * - "yt music"  → Provider "yt" (YouTube), query "music"
 * - "s"         → No provider, searches apps for "s"
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
 * Parses the search query using a SearchProviderRegistry.
 *
 * This is the primary parsing function that uses the registry's prefix mappings
 * to detect providers. It supports:
 * - Multiple prefixes per provider
 * - Multi-character prefixes
 * - Unicode prefixes (e.g., Arabic letters)
 *
 * IMPORTANT: Provider mode ONLY activates when user types "prefix " (with space).
 * Typing just "s" will search apps, not activate web search mode.
 *
 * FORMAT: "prefix query" or just "query"
 *
 * HOW IT WORKS:
 * 1. First, check if the input starts with any configured prefix followed by a space
 *    The registry maintains a complete list of all prefixes for all providers
 * 2. If found, extract the query and return the matched provider
 * 3. If no prefix+space found, check if the user typed just a partial prefix
 *    (e.g., "s" without space). In this case, treat it as app search
 * 4. If no conditions match, treat the entire input as an app search query
 *
 * ALGORITHM:
 * To support multi-character prefixes efficiently, we iterate through all
 * configured prefixes sorted by length (longest first). This ensures that
 * if both "y" and "yt" are prefixes, "yt music" matches "yt" not "y".
 *
 * @param input The raw user input from the search field
 * @param registry The registry containing provider and prefix mappings
 * @return ParsedQuery containing the detected provider and the actual query
 */
fun parseSearchQuery(
    input: String,
    registry: SearchProviderRegistry
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

    // Get all configured prefixes from the registry
    // This includes both default and custom prefixes for all providers
    val allPrefixes = registry.getAllPrefixes()

    // Sort prefixes by length (longest first) to ensure proper matching
    // This is critical for multi-character prefixes like "yt" vs "y"
    // Without this, "yt music" would incorrectly match "y" instead of "yt"
    val sortedPrefixes = allPrefixes.sortedByDescending { it.length }

    // Check if input starts with any configured prefix followed by a space
    // This is the main trigger for activating provider mode
    // Example: "s cats" activates web search for "cats"
    // The space is required to prevent accidental activation while typing app names
    for (prefix in sortedPrefixes) {
        // Build the prefix pattern (e.g., "s ", "yt ", "م ")
        val prefixWithSpace = prefix + " "

        // Check if input starts with this prefix followed by space
        if (input.startsWith(prefixWithSpace)) {
            // Found a match! Get the provider for this prefix
            val provider = registry.findByPrefix(prefix)

            if (provider != null) {
                // Extract the actual query part (everything after "prefix ")
                val query = input.substring(prefixWithSpace.length)

                // Return result with the matched provider and extracted query
                return ParsedQuery(
                    provider = provider,
                    query = query,
                    config = provider.config
                )
            }
        }
    }

    // No provider prefix with space found
    // Now check if user typed just a prefix (or partial prefix) without space
    // Example: user typed "s" or "yt" but hasn't typed space yet
    // We DON'T activate provider mode here - they might be searching for an app
    val trimmed = input.trim()

    // Check if the input matches or starts with any configured prefix
    // This handles both single-character prefixes ("s") and multi-character ("yt")
    for (prefix in sortedPrefixes) {
        // Check if user typed exactly the prefix
        if (trimmed == prefix) {
            // User typed just the prefix without space
            // Return as app search (they might be searching for an app)
            // The provider will be activated once they add a space
            return ParsedQuery(
                provider = null,
                query = input,
                config = null
            )
        }

        // Check if user is typing a multi-character prefix
        // Example: "y" could be start of "y" or "yt"
        // We still treat it as app search until they complete and add space
        if (prefix.startsWith(trimmed) && trimmed.length < prefix.length) {
            // User is typing a multi-character prefix
            // Don't activate provider - wait for completion and space
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
