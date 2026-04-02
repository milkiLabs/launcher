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
            return recentApps
        }

        return AppQueryRanker.rank(
            apps = installedApps,
            query = query,
            includePackageNameMatches = false
        )
    }
}
