/**
 * QuerySuggestionResolver.kt - Analyzes query text and provides actionable suggestions
 *
 * This resolver examines the current search query and determines what kind of
 * action the user might want to take. It uses pattern matching and heuristics
 * to classify the query into one of several suggestion types.
 *
 * DIFFERENCE FROM ClipboardSuggestionResolver:
 * - ClipboardSuggestionResolver reads from system clipboard
 * - QuerySuggestionResolver analyzes the actively typed query
 * - Both use similar classification logic but different input sources
 *
 * PRIORITY ORDER (highest to lowest):
 * 1) URL - Most specific, indicates intent to visit a website
 * 2) Email address - Clear intent to send email
 * 3) Plain text search - Fallback for everything else
 *
 * WHY EXACTLY ONE SUGGESTION:
 * We intentionally return exactly one suggestion to keep the UI simple and avoid
 * overwhelming users with multiple chips. The priority order ensures the most
 * likely intended action is suggested.
 */
package com.milki.launcher.domain.search

import android.util.Patterns

/**
 * Analyzes query text and returns the best single suggestion for the user.
 *
 * This class encapsulates all the logic for determining what action to suggest
 * based on what the user is typing. It uses a combination of:
 * - Android's built-in patterns (URL, email)
 * - URL validation and handler resolution
 *
 * THREADING:
 * This class performs I/O operations (URL handler resolution).
 * Call resolveFromQuery() from a background thread (Dispatchers.IO or Default).
 *
 * @property urlHandlerResolver Resolver for determining which apps can handle URLs
 */
class QuerySuggestionResolver(
    private val urlHandlerResolver: UrlHandlerResolver
) {
    /**
     * Analyzes the query text and returns the best suggestion, or null if blank.
     *
     * This is the main entry point for query analysis. It takes the current
     * query text and returns exactly one suggestion (or null if the query is blank).
     *
     * @param query The current search query text
     * @return The best suggestion for this query, or null if query is blank
     */
    fun resolveFromQuery(query: String): QuerySuggestion? {
        // Fast-fail for empty queries - no suggestion needed
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return null

        return resolveFromText(trimmedQuery)
    }

    /**
     * Converts raw query text into one suggestion using deterministic priority rules.
     *
     * PRIORITY ORDER:
     * 1. URL detection (via UrlValidator)
    * 2. Email address detection (via Patterns.EMAIL_ADDRESS)
    * 3. Plain text search fallback
     *
     * @param rawText The trimmed query text
     * @return Exactly one suggestion based on priority rules
     */
    private fun resolveFromText(rawText: String): QuerySuggestion {
        // Priority 1: URL detection
        val urlSuggestion = resolveUrlSuggestion(rawText)
        if (urlSuggestion != null) return urlSuggestion

        // Priority 2: Email address detection
        if (Patterns.EMAIL_ADDRESS.matcher(rawText).matches()) {
            return QuerySuggestion.ComposeEmail(
                emailAddress = rawText,
                rawQuery = rawText
            )
        }

        // Priority 3: Plain text search fallback
        return QuerySuggestion.SearchWeb(
            searchQuery = rawText,
            rawQuery = rawText
        )
    }

    /**
     * Attempts to parse the text as URL and resolve an app handler.
     *
     * This method uses UrlValidator to determine if the query looks like a URL.
     * If it is a valid URL, we also resolve which app can handle it (e.g., YouTube
     * for youtube.com URLs) to show "Open in [App Name]" in the UI.
     *
     * @param rawText The query text to analyze
     * @return OpenUrl suggestion if valid URL, null otherwise
     */
    private fun resolveUrlSuggestion(rawText: String): QuerySuggestion.OpenUrl? {
        val result = SuggestionPatternMatcher.resolveUrlResult(rawText, urlHandlerResolver) ?: return null

        return QuerySuggestion.OpenUrl(
            urlResult = result,
            rawQuery = rawText
        )
    }
}
