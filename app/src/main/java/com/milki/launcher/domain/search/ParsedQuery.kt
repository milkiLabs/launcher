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
    if (input.isEmpty()) {
        return ParsedQuery(provider = null, query = "", config = null)
    }

    val sortedPrefixes = registry.getAllPrefixes().sortedByDescending { it.length }
    val matchedProviderQuery = sortedPrefixes.firstNotNullOfOrNull { prefix ->
        registry.findByPrefix(prefix)?.takeIf {
            input.startsWith("$prefix ", ignoreCase = true)
        }?.let { provider ->
            ParsedQuery(
                provider = provider,
                query = input.substring(prefix.length + 1),
                config = provider.config
            )
        }
    }

    return matchedProviderQuery ?: ParsedQuery(
        provider = null,
        query = input,
        config = null
    )
}
