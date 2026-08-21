package com.milki.launcher.domain.search

import android.util.Patterns

/**
 * SuggestionResolver reads text and converts it into one actionable suggestion.
 *
 * IMPORTANT BEHAVIORAL CONTRACT:
 * - When resolving from clipboard, it performs a single snapshot read.
 * - It does NOT observe clipboard change events.
 *
 * PRIORITY ORDER (highest to lowest):
 * 1) URL
 * 2) Email address
 * 3) Plain text search
 */
class SuggestionResolver(
    private val clipboardReader: ClipboardReader,
    private val urlHandlerPort: UrlHandlerPort
) {

    /**
     * Reads clipboard text and returns the best single suggestion, or null.
     */
    fun resolveFromClipboard(): ActionSuggestion? {
        val rawText = clipboardReader.readText() ?: return null
        if (rawText.isBlank()) return null

        return resolveFromText(rawText)
    }

    /**
     * Converts raw text into one suggestion using deterministic priority rules.
     */
    fun resolveFromText(rawText: String): ActionSuggestion? {
        val urlSuggestion = resolveUrlSuggestion(rawText)
        if (urlSuggestion != null) return urlSuggestion

        if (Patterns.EMAIL_ADDRESS.matcher(rawText).matches()) {
            return ActionSuggestion.ComposeEmail(
                emailAddress = rawText,
                rawText = rawText
            )
        }

        return ActionSuggestion.SearchText(
            queryText = rawText,
            rawText = rawText
        )
    }

    /**
     * Attempts to parse the text as URL and resolve an app handler.
     */
    private fun resolveUrlSuggestion(rawText: String): ActionSuggestion.OpenUrl? {
        val result = SuggestionPatternMatcher.resolveUrlResult(rawText, urlHandlerPort) ?: return null

        return ActionSuggestion.OpenUrl(
            urlResult = result,
            rawText = rawText
        )
    }
}
