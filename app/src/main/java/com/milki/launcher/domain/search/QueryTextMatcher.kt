package com.milki.launcher.domain.search

/**
 * Shared text-normalization and matching helpers for query-driven filtering.
 */
object QueryTextMatcher {

    fun normalize(query: String): String {
        return query.trim().lowercase()
    }

    fun containsNormalized(text: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        return text.lowercase().contains(normalizedQuery)
    }

    /**
     * Splits [text] into alphanumeric word tokens ("foo-bar baz" -> ["foo", "bar", "baz"]).
     */
    fun tokenize(text: String): List<String> {
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

    /**
     * Builds the acronym of [text] from the first character of each
     * alphanumeric run ("Google Play Store" -> "gps").
     */
    fun buildAcronym(text: String): String {
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

    /**
     * Standard Levenshtein edit distance between [a] and [b].
     */
    fun levenshtein(a: String, b: String): Int {
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
}
