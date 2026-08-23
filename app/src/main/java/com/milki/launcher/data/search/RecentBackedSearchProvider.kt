package com.milki.launcher.data.search

import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.repository.SearchRequest
import com.milki.launcher.domain.search.QueryRanker

/**
 * Skeleton shared by permission-gated providers that show recents for blank
 * queries and rank typed queries against recent items.
 *
 * Flow: [preQueryResults] → permission gate → [permissionPrompt] when denied;
 * blank query → [resolveRecentItems]; typed query → [searchTypedItems] ranked
 * via [QueryRanker], all capped at [maxResults].
 */
abstract class RecentBackedSearchProvider<T : Any>(
    private val maxResults: Int = MAX_SEARCH_RESULTS,
) : SearchProvider {

    final override suspend fun search(request: SearchRequest): List<SearchResult> {
        val state = permissionState(request)
        if (!state.isGranted) {
            return preQueryResults(request) + permissionPrompt(state)
        }

        if (request.query.isBlank()) {
            return resolveRecentItems(request).take(maxResults).map(toSearchResult)
        }

        return (
            preQueryResults(request) +
                QueryRanker.rank(
                    items = searchTypedItems(request),
                    query = request.query,
                    recentItems = resolveRecentItems(request),
                    nameSelector = nameSelector,
                    identitySelector = identitySelector,
                ).map(toSearchResult)
            ).take(maxResults)
    }

    /** Extra results shown ahead of everything else (e.g. dial-a-phone-number). */
    protected open fun preQueryResults(request: SearchRequest): List<SearchResult> = emptyList()

    protected abstract fun permissionState(request: SearchRequest): PermissionAccessState

    protected abstract fun permissionPrompt(state: PermissionAccessState): PermissionRequestResult

    protected abstract suspend fun searchTypedItems(request: SearchRequest): List<T>

    protected abstract suspend fun resolveRecentItems(request: SearchRequest): List<T>

    protected abstract val toSearchResult: (T) -> SearchResult

    protected abstract val nameSelector: (T) -> String

    protected abstract val identitySelector: (T) -> String
}
