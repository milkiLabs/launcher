package com.milki.launcher.domain.search

import kotlin.math.abs

/**
 * Generic ranking engine for query-based search across any item type.
 *
 * Usage:
 *   QueryRanker.rank(
 *       items = contacts,
 *       query = "john",
 *       recentItems = recentContacts,
 *       nameSelector = { it.displayName },
 *       identitySelector = { it.id.toString() },
 *   )
 *
 * Scoring hierarchy (highest to lowest):
 *   Exact match > Prefix > Word prefix > Contains > Acronym > Token > Subsequence > Typo
 *
 * Recent items receive a boost that can elevate them above weaker matches
 * but never above stronger text matches.
 */
object QueryRanker {

    fun <T : Any> rank(
        items: List<T>,
        query: String,
        recentItems: List<T> = emptyList(),
        nameSelector: (T) -> String,
        secondaryTextSelector: ((T) -> String)? = null,
        identitySelector: (T) -> String,
    ): List<T> {
        val normalizedQuery = QueryTextMatcher.normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val queryTokens = tokenize(normalizedQuery)
        val recentRanks = recentItems.withIndex().associate { (i, item) -> identitySelector(item) to i }

        return items.asSequence()
            .mapNotNull { item ->
                val score = scoreItem(
                    item = item,
                    normalizedQuery = normalizedQuery,
                    queryTokens = queryTokens,
                    recentRank = recentRanks[identitySelector(item)],
                    nameSelector = nameSelector,
                    secondaryTextSelector = secondaryTextSelector,
                )
                score?.let { s -> item to s }
            }
            .sortedWith(
                compareByDescending<Pair<T, Int>> { it.second }
                    .thenBy { nameSelector(it.first).lowercase() }
                    .thenBy { identitySelector(it.first) }
            )
            .map { it.first }
            .toList()
    }

    private fun <T : Any> scoreItem(
        item: T,
        normalizedQuery: String,
        queryTokens: List<String>,
        recentRank: Int?,
        nameSelector: (T) -> String,
        secondaryTextSelector: ((T) -> String)?,
    ): Int? {
        val name = nameSelector(item).lowercase()
        val nameScore = scorePrimaryText(name, normalizedQuery, queryTokens)

        val secondaryScore = secondaryTextSelector?.invoke(item)
            ?.lowercase()
            ?.let { scoreSecondaryText(it, normalizedQuery, queryTokens) }
            ?: Int.MIN_VALUE

        val baseScore = maxOf(nameScore ?: Int.MIN_VALUE, secondaryScore)
        if (baseScore == Int.MIN_VALUE) return null

        return baseScore + recentBoost(recentRank)
    }

    private fun scorePrimaryText(
        text: String,
        query: String,
        tokens: List<String>,
    ): Int? {
        val acronym = buildAcronym(text)

        return when {
            text == query -> EXACT_MATCH
            text.startsWith(query) -> PREFIX_MATCH + prefixQuality(text, query)
            text.contains(" $query") -> WORD_PREFIX_MATCH + prefixQuality(text, query)
            text.contains(query) -> CONTAINS_MATCH - text.indexOf(query)
            acronym.isNotEmpty() && acronym.startsWith(query) -> ACRONYM_MATCH
            allTokensCovered(tokens, text, acronym) -> TOKEN_MATCH
            query.length >= MIN_SUBSEQUENCE_LENGTH && isSubsequence(query, text) ->
                SUBSEQUENCE_MATCH - subsequenceSpread(query, text)
            query.length >= MIN_TYPO_LENGTH -> typoScore(query, text)
            else -> null
        }
    }

    private fun scoreSecondaryText(
        text: String,
        query: String,
        tokens: List<String>,
    ): Int? {
        return when {
            text == query -> SECONDARY_EXACT_MATCH
            text.contains(query) -> SECONDARY_CONTAINS_MATCH - text.indexOf(query)
            allTokensCovered(tokens, text, acronym = "") -> SECONDARY_TOKEN_MATCH
            else -> null
        }
    }

    private fun allTokensCovered(tokens: List<String>, text: String, acronym: String): Boolean {
        if (tokens.size <= 1) return false
        return tokens.all { token ->
            text.contains(token) ||
                acronym.startsWith(token) ||
                (token.length >= MIN_TYPO_LENGTH && tokenNearEnough(token, text))
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

    private fun prefixQuality(text: String, query: String): Int =
        (PREFIX_QUALITY_MAX - (text.length - query.length)).coerceAtLeast(0)

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<String>(4)
        var start = -1
        text.forEachIndexed { i, c ->
            if (c.isLetterOrDigit()) {
                if (start == -1) start = i
            } else if (start != -1) {
                result += text.substring(start, i)
                start = -1
            }
        }
        if (start != -1) result += text.substring(start)
        return result
    }

    private fun buildAcronym(text: String): String {
        val builder = StringBuilder()
        var boundary = true
        for (c in text) {
            if (c.isLetterOrDigit()) {
                if (boundary) builder.append(c)
                boundary = false
            } else {
                boundary = true
            }
        }
        return builder.toString()
    }

    private fun isSubsequence(query: String, text: String): Boolean {
        if (query.length > text.length) return false
        var qi = 0
        var ti = 0
        while (qi < query.length && ti < text.length) {
            if (query[qi] == text[ti]) qi++
            ti++
        }
        return qi == query.length
    }

    private fun subsequenceSpread(query: String, text: String): Int {
        var qi = 0
        var first = -1
        var last = -1
        text.forEachIndexed { i, c ->
            if (qi < query.length && c == query[qi]) {
                if (first == -1) first = i
                last = i
                qi++
            }
        }
        return if (first == -1) text.length else last - first - query.length
    }

    private fun tokenNearEnough(query: String, text: String): Boolean =
        bestTokenDistance(query, text)?.let { it <= maxEditDistanceFor(query) } ?: false

    private fun bestTokenDistance(query: String, text: String): Int? {
        val targetTokens = tokenize(text)
        if (targetTokens.isEmpty()) return null
        var best = Int.MAX_VALUE
        for (token in targetTokens) {
            if (abs(query.length - token.length) <= maxEditDistanceFor(query)) {
                best = minOf(best, levenshtein(query, token))
            }
        }
        return if (best == Int.MAX_VALUE) null else best
    }

    private fun maxEditDistanceFor(query: String): Int =
        if (query.length >= LONG_QUERY_MIN_LENGTH) 2 else 1

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            val ac = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ac == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[b.length]
    }

    private const val EXACT_MATCH = 10_000
    private const val PREFIX_MATCH = 9_000
    private const val WORD_PREFIX_MATCH = 8_500
    private const val CONTAINS_MATCH = 7_500
    private const val ACRONYM_MATCH = 7_000
    private const val TOKEN_MATCH = 6_700
    private const val SUBSEQUENCE_MATCH = 5_400
    private const val TYPO_MATCH = 5_900

    private const val SECONDARY_EXACT_MATCH = 5_200
    private const val SECONDARY_CONTAINS_MATCH = 4_900
    private const val SECONDARY_TOKEN_MATCH = 4_700

    private const val RECENT_BOOST_MAX = 650
    private const val RECENT_BOOST_STEP = 80
    private const val RECENT_BOOST_MIN = 120
    private const val PREFIX_QUALITY_MAX = 100
    private const val TYPO_DISTANCE_PENALTY = 160
    private const val MIN_SUBSEQUENCE_LENGTH = 2
    private const val MIN_TYPO_LENGTH = 3
    private const val LONG_QUERY_MIN_LENGTH = 6
}
