package com.milki.launcher.data.search

import android.net.Uri
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.WebSearchResult
import com.milki.launcher.domain.repository.SearchProvider

/**
 * ConfigurableUrlSearchProvider.kt - Runtime SearchProvider backed by SearchSource
 *
 * This provider converts one user-configurable SearchSource into an executable
 * SearchProvider instance for prefix-mode searches.
 *
 * WHY THIS CLASS EXISTS:
 * We want users to create/edit/delete sources in Settings without code changes.
 * The app therefore builds provider objects from persisted source data.
 */
class ConfigurableUrlSearchProvider(
    private val source: SearchSource
) : SearchProvider {

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = source.id,
        prefix = source.primaryPrefix,
        name = source.name,
        description = "Search ${source.name}"
    )

    /**
     * Returns one web/url result for this source.
     */
    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }

        val encodedQuery = Uri.encode(query)
        val finalUrl = source.buildUrl(encodedQuery)

        return listOf(
            WebSearchResult(
                title = "Search \"$query\" on ${source.name}",
                url = finalUrl,
                engine = source.name,
                query = query,
                providerId = source.id
            )
        )
    }
}
