package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo

/**
 * Shared app-ranking helper used by surfaces that perform query-based app filtering.
 */
object AppQueryRanker {

    fun rank(
        apps: List<AppInfo>,
        query: String,
        includePackageNameMatches: Boolean
    ): List<AppInfo> {
        val normalizedQuery = QueryTextMatcher.normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val exactMatches = ArrayList<AppInfo>()
        val startsWithMatches = ArrayList<AppInfo>()
        val wordBoundaryMatches = ArrayList<AppInfo>()
        val containsMatches = ArrayList<AppInfo>()
        val fuzzyMatches = ArrayList<AppInfo>()
        val packageMatches = ArrayList<AppInfo>()

        apps.forEach { app ->
            val name = app.nameLower

            when {
                name == normalizedQuery -> exactMatches.add(app)
                name.startsWith(normalizedQuery) -> startsWithMatches.add(app)
                name.contains(" $normalizedQuery") -> wordBoundaryMatches.add(app)
                name.contains(normalizedQuery) -> containsMatches.add(app)
                isSubsequenceMatch(query = normalizedQuery, text = name) -> fuzzyMatches.add(app)
                includePackageNameMatches && app.packageLower.contains(normalizedQuery) -> {
                    packageMatches.add(app)
                }
            }
        }

        return buildList(
            capacity =
                exactMatches.size +
                    startsWithMatches.size +
                    wordBoundaryMatches.size +
                    containsMatches.size +
                    fuzzyMatches.size +
                    packageMatches.size
        ) {
            addAll(exactMatches)
            addAll(startsWithMatches)
            addAll(wordBoundaryMatches)
            addAll(containsMatches)
            addAll(fuzzyMatches)
            addAll(packageMatches)
        }
    }

    private fun isSubsequenceMatch(query: String, text: String): Boolean {
        if (query.length > text.length) return false

        var queryIndex = 0
        var textIndex = 0

        while (queryIndex < query.length && textIndex < text.length) {
            if (query[queryIndex] == text[textIndex]) {
                queryIndex++
            }
            textIndex++
        }

        return queryIndex == query.length
    }
}
