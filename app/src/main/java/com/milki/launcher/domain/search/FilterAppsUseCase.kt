/**
 * FilterAppsUseCase.kt - Use case for filtering and prioritizing app results
 *
 * This file contains the business logic for filtering installed apps
 * based on a search query. It implements the Strategy Pattern for
 * different match types (exact, starts with, contains).
 *
 * WHY A USE CASE?
 * - Single Responsibility: Only handles app filtering logic
 * - Testable: Pure function with no dependencies
 * - Reusable: Can be used from different ViewModels or use cases
 *
 * MATCHING ALGORITHM:
 * Apps are matched against both name and package name, then sorted by priority:
 * 1. Exact matches (highest priority)
 * 2. Starts-with matches (medium priority)
 * 3. Contains matches (lowest priority)
 */

package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo

/**
 * Use case for filtering and prioritizing app search results.
 *
 * This is a pure function use case - it has no side effects and
 * always returns the same output for the same input.
 */
class FilterAppsUseCase {

    /**
     * Filter apps based on a search query.
     *
     * Uses a priority-based matching algorithm:
     * 1. Exact matches (name or package equals query exactly)
     * 2. Starts-with matches (name or package starts with query)
     * 3. Contains matches (name or package contains query anywhere)
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
                // Exact match: name or package equals query exactly
                app.nameLower == queryLower || app.packageLower == queryLower -> {
                    exactMatches.add(app)
                }
                // Starts with: name or package starts with query
                app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                    startsWithMatches.add(app)
                }
                // Contains: name or package contains query anywhere
                app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
                    containsMatches.add(app)
                }
            }
        }

        // Combine in priority order (exact → starts with → contains)
        return exactMatches + startsWithMatches + containsMatches
    }

    /**
     * Filter apps with a custom match threshold.
     *
     * Allows filtering with a minimum match type requirement.
     * For example, only return exact or starts-with matches.
     *
     * @param query The search query
     * @param installedApps All installed apps
     * @param recentApps Recent apps for empty query
     * @param minMatchType Minimum match type to include (0=exact, 1=starts, 2=contains)
     * @return Filtered list of apps
     */
    fun filterWithThreshold(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>,
        minMatchType: MatchType = MatchType.CONTAINS
    ): List<AppInfo> {
        if (query.isBlank()) {
            return recentApps
        }

        val queryLower = query.trim().lowercase()
        val results = mutableListOf<AppInfo>()

        installedApps.forEach { app ->
            val matchType = getMatchType(app, queryLower)

            // Only include if match type meets threshold
            if (matchType != null && matchType.ordinal <= minMatchType.ordinal) {
                results.add(app)
            }
        }

        // Sort by match type priority
        return results.sortedBy { app ->
            getMatchType(app, queryLower)?.ordinal ?: Int.MAX_VALUE
        }
    }

    /**
     * Get the match type for an app against a query.
     *
     * @param app The app to check
     * @param queryLower The lowercased query
     * @return The match type, or null if no match
     */
    private fun getMatchType(app: AppInfo, queryLower: String): MatchType? {
        return when {
            app.nameLower == queryLower || app.packageLower == queryLower -> MatchType.EXACT
            app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> MatchType.STARTS_WITH
            app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> MatchType.CONTAINS
            else -> null
        }
    }

    /**
     * Match type enum for prioritizing results.
     */
    enum class MatchType {
        EXACT,       // Name or package equals query exactly
        STARTS_WITH, // Name or package starts with query
        CONTAINS     // Name or package contains query anywhere
    }
}
