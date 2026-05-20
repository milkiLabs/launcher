package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo

/**
 * Filters installed apps for search queries with a deterministic ranking order.
 */
class FilterAppsUseCase {

    operator fun invoke(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<AppInfo> {
        if (query.isBlank()) {
            return recentApps.take(MAX_SEARCH_RESULTS)
        }

        return AppQueryRanker.rank(
            apps = installedApps,
            query = query,
            includePackageNameMatches = false,
            recentApps = recentApps
        ).take(MAX_SEARCH_RESULTS)
    }

    companion object {
        private const val MAX_SEARCH_RESULTS = 10
    }
}
