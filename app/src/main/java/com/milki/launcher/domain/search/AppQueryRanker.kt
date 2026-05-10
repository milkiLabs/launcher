package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.AppInfo
import kotlin.math.abs

/**
 * Shared app-ranking helper used by surfaces that perform query-based app filtering.
 */
object AppQueryRanker {

    fun rank(
        apps: List<AppInfo>,
        query: String,
        includePackageNameMatches: Boolean,
        recentApps: List<AppInfo> = emptyList()
    ): List<AppInfo> {
        val normalizedQuery = QueryTextMatcher.normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val queryTokens = tokens(normalizedQuery)
        val recentRanks = recentApps
            .withIndex()
            .associate { (index, app) -> app.identityKey() to index }

        return apps.asSequence()
            .mapNotNull { app ->
                val score = scoreApp(
                    app = app,
                    normalizedQuery = normalizedQuery,
                    queryTokens = queryTokens,
                    includePackageNameMatches = includePackageNameMatches,
                    recentRank = recentRanks[app.identityKey()]
                )
                score?.let { RankedApp(app = app, score = it) }
            }
            .sortedWith(
                compareByDescending<RankedApp> { it.score }
                    .thenBy { it.app.nameLower }
                    .thenBy { it.app.packageName }
                    .thenBy { it.app.activityName }
            )
            .map { it.app }
            .toList()
    }

    private data class RankedApp(
        val app: AppInfo,
        val score: Int
    )

    private fun scoreApp(
        app: AppInfo,
        normalizedQuery: String,
        queryTokens: List<String>,
        includePackageNameMatches: Boolean,
        recentRank: Int?
    ): Int? {
        val name = app.nameLower
        val packageName = app.packageLower

        val textScore = scoreText(
            text = name,
            normalizedQuery = normalizedQuery,
            queryTokens = queryTokens
        )
        val packageScore = if (includePackageNameMatches) {
            scorePackageName(packageName, normalizedQuery, queryTokens)
        } else {
            null
        }

        val baseScore = maxOf(textScore ?: Int.MIN_VALUE, packageScore ?: Int.MIN_VALUE)
        if (baseScore == Int.MIN_VALUE) return null

        return baseScore + recentBoost(recentRank)
    }

    private fun scoreText(
        text: String,
        normalizedQuery: String,
        queryTokens: List<String>
    ): Int? {
        val acronym = acronymFor(text)

        val base = when {
            text == normalizedQuery -> EXACT_MATCH
            text.startsWith(normalizedQuery) -> PREFIX_MATCH + prefixQuality(text, normalizedQuery)
            text.contains(" $normalizedQuery") -> WORD_PREFIX_MATCH + prefixQuality(text, normalizedQuery)
            text.contains(normalizedQuery) -> CONTAINS_MATCH - text.indexOf(normalizedQuery)
            acronym.isNotEmpty() && acronym.startsWith(normalizedQuery) -> ACRONYM_MATCH
            coversAllTokens(queryTokens, text, acronym) -> TOKEN_MATCH
            normalizedQuery.length >= MIN_SUBSEQUENCE_LENGTH &&
                isSubsequenceMatch(query = normalizedQuery, text = text) -> {
                SUBSEQUENCE_MATCH - subsequenceSpread(query = normalizedQuery, text = text)
            }
            normalizedQuery.length >= MIN_TYPO_LENGTH -> typoScore(normalizedQuery, text)
            else -> null
        }

        return base
    }

    private fun scorePackageName(
        packageName: String,
        normalizedQuery: String,
        queryTokens: List<String>
    ): Int? {
        return when {
            packageName == normalizedQuery -> PACKAGE_EXACT_MATCH
            packageName.contains(normalizedQuery) -> PACKAGE_CONTAINS_MATCH - packageName.indexOf(normalizedQuery)
            coversAllTokens(queryTokens, packageName, acronym = "") -> PACKAGE_TOKEN_MATCH
            else -> null
        }
    }

    private fun coversAllTokens(
        queryTokens: List<String>,
        text: String,
        acronym: String
    ): Boolean {
        if (queryTokens.size <= 1) return false
        return queryTokens.all { token ->
            text.contains(token) ||
                acronym.startsWith(token) ||
                (token.length >= MIN_TYPO_LENGTH && hasTokenWithinEditDistance(token, text))
        }
    }

    private fun typoScore(query: String, text: String): Int? {
        val bestDistance = bestTokenDistance(query, text) ?: return null
        val maxDistance = maxEditDistanceFor(query)
        if (bestDistance > maxDistance) return null
        return TYPO_MATCH - (bestDistance * TYPO_DISTANCE_PENALTY) - abs(text.length - query.length)
    }

    private fun recentBoost(recentRank: Int?): Int {
        if (recentRank == null) return 0
        return (RECENT_BOOST_MAX - (recentRank * RECENT_BOOST_STEP)).coerceAtLeast(RECENT_BOOST_MIN)
    }

    private fun prefixQuality(text: String, query: String): Int {
        return (PREFIX_QUALITY_MAX - (text.length - query.length)).coerceAtLeast(0)
    }

    private fun tokens(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<String>(4)
        var start = -1
        text.forEachIndexed { index, char ->
            if (char.isLetterOrDigit()) {
                if (start == -1) start = index
            } else if (start != -1) {
                result += text.substring(start, index)
                start = -1
            }
        }
        if (start != -1) {
            result += text.substring(start)
        }
        return result
    }

    private fun acronymFor(text: String): String {
        val builder = StringBuilder()
        var previousWasBoundary = true
        text.forEach { char ->
            if (char.isLetterOrDigit()) {
                if (previousWasBoundary) builder.append(char)
                previousWasBoundary = false
            } else {
                previousWasBoundary = true
            }
        }
        return builder.toString()
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

    private fun subsequenceSpread(query: String, text: String): Int {
        var queryIndex = 0
        var firstMatch = -1
        var lastMatch = -1

        text.forEachIndexed { index, char ->
            if (queryIndex < query.length && char == query[queryIndex]) {
                if (firstMatch == -1) firstMatch = index
                lastMatch = index
                queryIndex++
            }
        }

        return if (firstMatch == -1) text.length else lastMatch - firstMatch - query.length
    }

    private fun hasTokenWithinEditDistance(query: String, text: String): Boolean {
        return bestTokenDistance(query, text)?.let { distance ->
            distance <= maxEditDistanceFor(query)
        } ?: false
    }

    private fun bestTokenDistance(query: String, text: String): Int? {
        val targetTokens = tokens(text)
        if (targetTokens.isEmpty()) return null

        var bestDistance = Int.MAX_VALUE
        targetTokens.forEach { token ->
            if (abs(query.length - token.length) <= maxEditDistanceFor(query)) {
                bestDistance = minOf(bestDistance, levenshteinDistance(query, token))
            }
        }

        return if (bestDistance == Int.MAX_VALUE) null else bestDistance
    }

    private fun maxEditDistanceFor(query: String): Int {
        return if (query.length >= LONG_QUERY_MIN_LENGTH) 2 else 1
    }

    private fun levenshteinDistance(first: String, second: String): Int {
        if (first == second) return 0
        if (first.isEmpty()) return second.length
        if (second.isEmpty()) return first.length

        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)

        for (firstIndex in 1..first.length) {
            current[0] = firstIndex
            val firstChar = first[firstIndex - 1]

            for (secondIndex in 1..second.length) {
                val substitutionCost = if (firstChar == second[secondIndex - 1]) 0 else 1
                current[secondIndex] = minOf(
                    current[secondIndex - 1] + 1,
                    previous[secondIndex] + 1,
                    previous[secondIndex - 1] + substitutionCost
                )
            }

            val swap = previous
            previous = current
            current = swap
        }

        return previous[second.length]
    }

    private fun AppInfo.identityKey(): String = "$packageName/$activityName"

    private const val EXACT_MATCH = 10_000
    private const val PREFIX_MATCH = 9_000
    private const val WORD_PREFIX_MATCH = 8_500
    private const val CONTAINS_MATCH = 7_500
    private const val ACRONYM_MATCH = 7_000
    private const val TOKEN_MATCH = 6_700
    private const val TYPO_MATCH = 5_900
    private const val SUBSEQUENCE_MATCH = 5_400
    private const val PACKAGE_EXACT_MATCH = 5_200
    private const val PACKAGE_CONTAINS_MATCH = 4_900
    private const val PACKAGE_TOKEN_MATCH = 4_700
    private const val RECENT_BOOST_MAX = 650
    private const val RECENT_BOOST_STEP = 80
    private const val RECENT_BOOST_MIN = 120
    private const val PREFIX_QUALITY_MAX = 100
    private const val TYPO_DISTANCE_PENALTY = 160
    private const val MIN_SUBSEQUENCE_LENGTH = 2
    private const val MIN_TYPO_LENGTH = 3
    private const val LONG_QUERY_MIN_LENGTH = 6
}
