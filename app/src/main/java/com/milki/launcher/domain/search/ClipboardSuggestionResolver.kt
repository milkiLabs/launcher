package com.milki.launcher.domain.search

import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns
import com.milki.launcher.domain.model.UrlSearchResult

/**
 * ClipboardSuggestionResolver reads the current clipboard text once and converts it
 * into one actionable suggestion for the search dialog.
 *
 * IMPORTANT BEHAVIORAL CONTRACT:
 * - This resolver performs a single snapshot read.
 * - It does NOT observe clipboard change events.
 * - The caller decides when to invoke it (we use search dialog open time).
 *
 * PRIORITY ORDER (highest to lowest):
 * 1) URL
 * 2) Email address
 * 3) Plain text search
 *
 * WHY PRIORITY MATTERS:
 * We intentionally return exactly one suggestion to keep the UI simple and avoid
 * overwhelming users with multiple clipboard chips.
 */
class ClipboardSuggestionResolver(
    private val context: Context,
    private val urlHandlerResolver: UrlHandlerResolver
) {

    /**
     * Reads clipboard text and returns the best single suggestion, or null.
     */
    fun resolveFromClipboard(): ClipboardSuggestion? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        val primaryClip = clipboardManager.primaryClip ?: return null
        if (primaryClip.itemCount <= 0) return null

        val rawText = primaryClip
            .getItemAt(0)
            .coerceToText(context)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (rawText.isBlank()) return null

        return resolveFromText(rawText)
    }

    /**
     * Converts raw text into one suggestion using deterministic priority rules.
     */
    private fun resolveFromText(rawText: String): ClipboardSuggestion? {
        val urlSuggestion = resolveUrlSuggestion(rawText)
        if (urlSuggestion != null) return urlSuggestion

        if (Patterns.EMAIL_ADDRESS.matcher(rawText).matches()) {
            return ClipboardSuggestion.ComposeEmail(
                emailAddress = rawText,
                rawText = rawText
            )
        }

        return ClipboardSuggestion.SearchText(
            queryText = rawText,
            rawText = rawText
        )
    }

    /**
     * Attempts to parse the text as URL and resolve an app handler.
     */
    private fun resolveUrlSuggestion(rawText: String): ClipboardSuggestion.OpenUrl? {
        val result = SuggestionPatternMatcher.resolveUrlResult(rawText, urlHandlerResolver) ?: return null

        return ClipboardSuggestion.OpenUrl(
            urlResult = result,
            rawText = rawText
        )
    }
}
