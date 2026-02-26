/**
 * YouTubeSearchProvider.kt - Search provider for YouTube
 *
 * This provider returns YouTube search options when the user uses the "y" prefix.
 *
 * RESPONSIBILITIES:
 * - Define the "y" prefix configuration
 * - Generate YouTube search results for queries
 * - NOT responsible for actually opening YouTube (that's SearchResultAction)
 *
 * EXAMPLE:
 * User types: "y lofi music"
 * Provider returns: [
 *   YouTubeSearchResult("lofi music")
 * ]
 */

package com.milki.launcher.data.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.model.YouTubeSearchResult
import com.milki.launcher.domain.repository.SearchProvider

/**
 * Search provider for YouTube.
 *
 * Returns a YouTube search result that the Activity can handle
 * by opening the YouTube app or browser.
 *
 * @property config Display configuration for this provider
 */
class YouTubeSearchProvider : SearchProvider {

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = ProviderId.YOUTUBE,
        prefix = "y",
        name = "YouTube",
        description = "Search YouTube videos",
        color = androidx.compose.ui.graphics.Color(0xFFFF0000), // YouTube Red
        icon = Icons.Default.PlayArrow
    )

    /**
     * Generate YouTube search result for the query.
     *
     * @param query The search query (without the "y " prefix)
     * @return List containing a single YouTubeSearchResult
     */
    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }

        return listOf(
            YouTubeSearchResult(query = query)
        )
    }
}
