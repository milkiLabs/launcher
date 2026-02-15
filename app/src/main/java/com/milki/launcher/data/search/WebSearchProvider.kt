/**
 * WebSearchProvider.kt - Search provider for web search engines
 *
 * This provider returns web search options (Google, DuckDuckGo) when
 * the user uses the "s" prefix.
 *
 * RESPONSIBILITIES:
 * - Define the "s" prefix configuration
 * - Generate web search results for queries
 * - NOT responsible for actually opening the browser (that's SearchAction)
 *
 * EXAMPLE:
 * User types: "s cats"
 * Provider returns: [
 *   WebSearchResult("Search \"cats\" on Google", "https://google.com/search?q=cats", "Google"),
 *   WebSearchResult("Search \"cats\" on DuckDuckGo", "https://duckduckgo.com/?q=cats", "DuckDuckGo")
 * ]
 */

package com.milki.launcher.data.search

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.model.WebSearchResult
import com.milki.launcher.domain.repository.SearchProvider

/**
 * Search provider for web search engines.
 *
 * Returns multiple web search options for each query,
 * allowing users to choose their preferred search engine.
 *
 * @property config Display configuration for this provider
 */
class WebSearchProvider : SearchProvider {

    override val config: SearchProviderConfig = SearchProviderConfig(
        prefix = "s",
        name = "Web Search",
        description = "Search the web",
        color = androidx.compose.ui.graphics.Color(0xFF4285F4), // Google Blue
        icon = Icons.Default.Search
    )

    /**
     * Generate web search results for the query.
     *
     * Returns results for multiple search engines so users can choose.
     *
     * @param query The search query (without the "s " prefix)
     * @return List of WebSearchResult objects
     */
    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }

        val encodedQuery = Uri.encode(query)

        return listOf(
            WebSearchResult(
                title = "Search \"$query\" on Google",
                url = "https://www.google.com/search?q=$encodedQuery",
                engine = "Google",
                query = query
            ),
            WebSearchResult(
                title = "Search \"$query\" on DuckDuckGo",
                url = "https://duckduckgo.com/?q=$encodedQuery",
                engine = "DuckDuckGo",
                query = query
            )
        )
    }
}
