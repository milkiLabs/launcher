package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo

/**
 * App-specific ranking delegate.
 * Delegates all scoring to [QueryRanker] with app-specific selectors.
 */
object AppQueryRanker {

    fun rank(
        apps: List<AppInfo>,
        query: String,
        includePackageNameMatches: Boolean,
        recentApps: List<AppInfo> = emptyList(),
    ): List<AppInfo> = QueryRanker.rank(
        items = apps,
        query = query,
        recentItems = recentApps,
        nameSelector = { it.name },
        secondaryTextSelector = if (includePackageNameMatches) ({ it.packageName }) else null,
        identitySelector = { "${it.packageName}/${it.activityName}" },
    )
}
