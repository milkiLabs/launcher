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
}
