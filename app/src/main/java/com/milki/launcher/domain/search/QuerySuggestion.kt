/**
 * QuerySuggestion.kt - Represents actionable suggestions for the current search query
 *
 * This sealed class defines the different types of suggestions that can be shown
 * when the user types in the search field. Unlike ClipboardSuggestion which reads
 * from the system clipboard, QuerySuggestion analyzes the current query text.
 *
 * WHY THIS MODEL EXISTS:
 * When users type in the search field, we want to provide quick actions based on
 * what they're typing. For example:
 * - If they type a URL, offer to open it in a browser or specific app
 * - If they type an email, offer to compose an email
 * - If they type plain text, offer to search the web
 *
 * This provides a consistent UX with the clipboard chip, but applies to the
 * actively typed query instead of past clipboard content.
 *
 * DESIGN CHOICE:
 * This is intentionally a sealed class so the compiler enforces exhaustive handling
 * in all `when` expressions that consume suggestions. This prevents us from
 * accidentally missing a suggestion type when adding new ones.
 *
 * VISIBILITY RULES:
 * The query suggestion chip is shown when:
 * - The search dialog is visible
 * - The query is NOT blank (user is typing)
 * - No special provider mode is active (default app search mode)
 * - A valid suggestion can be derived from the query
 */
package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.UrlSearchResult

/**
 * QuerySuggestion represents one actionable interpretation of the current query text.
 *
 * This is similar to ClipboardSuggestion but specifically for analyzing what the
 * user is currently typing in the search field. It provides quick action suggestions
 * that appear as a chip at the bottom of the search dialog.
 *
 * TYPES OF SUGGESTIONS:
 * - OpenUrl: The query looks like a URL that can be opened in a browser or app
 * - ComposeEmail: The query looks like an email address
 * - SearchWeb: The query is plain text that can be searched on the web
 */
sealed class QuerySuggestion {
    /**
     * The original query text used to derive this suggestion.
     *
     * Keeping the raw text helps the UI show context (for trust and clarity),
     * and allows future analytics/debugging without re-reading the query.
     */
    abstract val rawQuery: String

    /**
     * Suggestion for opening a URL.
     *
     * The `urlResult` already includes resolved handler-app information when
     * available, so the UI can show "Open in <App>" vs browser fallback.
     *
     * EXAMPLE:
     * Query: "youtube.com/watch?v=xyz"
     * Suggestion: OpenUrl with handlerApp = YouTube
     * UI shows: "Open in YouTube"
     */
    data class OpenUrl(
        val urlResult: UrlSearchResult,
        override val rawQuery: String
    ) : QuerySuggestion()

    /**
     * Suggestion for composing an email.
     *
     * EXAMPLE:
     * Query: "user@example.com"
     * Suggestion: ComposeEmail
     * UI shows: "Email user@example.com"
     */
    data class ComposeEmail(
        val emailAddress: String,
        override val rawQuery: String
    ) : QuerySuggestion()

    /**
     * Suggestion for searching the query on the web.
     *
     * This is the fallback suggestion for plain text queries that don't match
    * any other pattern (URL or email).
     *
     * EXAMPLE:
     * Query: "how to make pasta"
     * Suggestion: SearchWeb
     * UI shows: "Search with Google" (or configured default search engine)
     */
    data class SearchWeb(
        val searchQuery: String,
        override val rawQuery: String
    ) : QuerySuggestion()
}
