/**
 * FilterAppsUseCase.kt - Use case for filtering and prioritizing app results
 *
 * This file contains the business logic for filtering installed apps
 * based on a search query. It implements the Strategy Pattern for
 * different match types (exact, starts with, contains).
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo

/**
 * Use case for filtering and prioritizing app search results.
 *
 * This is a pure function use case - it has no side effects
 */
class FilterAppsUseCase {

    /**
     * Filter apps based on a search query.
     *
     * Uses a priority-based matching algorithm:
     * 1. Exact matches (name equals query exactly)
     * 2. Starts-with matches (name starts with query)
     * 3. Contains matches (name contains query anywhere)
     *
     * When the query is empty, returns recent apps instead of all apps.
     *
     * @param query The search query (will be trimmed and lowercased)
     * @param installedApps All installed apps to search through
     * @param recentApps Recent apps to show when query is empty
     * @return Filtered and prioritized list of apps
     */
    operator fun invoke(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<AppInfo> {
        // When search is empty, show recent apps
        if (query.isBlank()) {
            return recentApps
        }

        val queryLower = query.trim().lowercase()

        // Three lists for different match priorities
        val exactMatches = mutableListOf<AppInfo>()
        val startsWithMatches = mutableListOf<AppInfo>()
        val containsMatches = mutableListOf<AppInfo>()

        // Categorize each app based on match type
        installedApps.forEach { app ->
            when {
                // Exact match: name equals query exactly
                app.nameLower == queryLower -> {
                    exactMatches.add(app)
                }
                // Starts with: name starts with query
                app.nameLower.startsWith(queryLower) -> {
                    startsWithMatches.add(app)
                }
                // Contains: name contains query anywhere
                app.nameLower.contains(queryLower) -> {
                    containsMatches.add(app)
                }
            }
        }

        // Combine in priority order (exact → starts with → contains)
        return exactMatches + startsWithMatches + containsMatches
    }

}
