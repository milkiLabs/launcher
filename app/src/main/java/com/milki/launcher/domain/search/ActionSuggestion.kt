package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.UrlSearchResult

/**
 * ActionSuggestion represents one actionable interpretation of text (e.g. from clipboard or query).
 *
 * WHY THIS MODEL EXISTS:
 * The input can contain many different kinds of text:
 * - URLs
 * - email addresses
 * - plain text
 *
 * The UI does not want to re-implement this classification logic, and the action
 * executor should not guess intent from raw text at click time. Instead, we create
 * an explicit typed model at detection time and carry that through the UI.
 *
 * DESIGN CHOICE:
 * This is intentionally a sealed class so the compiler enforces exhaustive handling
 * in all `when` expressions that consume suggestions.
 */
sealed class ActionSuggestion {
    /**
     * The original text used to derive this suggestion.
     *
     * Keeping the raw text helps the UI show context (for trust and clarity),
     * and allows future analytics/debugging without re-reading data.
     */
    abstract val rawText: String

    /**
     * Suggestion for opening a URL.
     *
     * The `urlResult` already includes resolved handler-app information when
     * available, so the UI can show "Open in <App>" vs browser fallback.
     */
    data class OpenUrl(
        val urlResult: UrlSearchResult,
        override val rawText: String
    ) : ActionSuggestion()

    /**
     * Suggestion for composing an email.
     */
    data class ComposeEmail(
        val emailAddress: String,
        override val rawText: String
    ) : ActionSuggestion()

    /**
     * Suggestion for using the text as a search query inside the launcher.
     */
    data class SearchText(
        val queryText: String,
        override val rawText: String
    ) : ActionSuggestion()
}
