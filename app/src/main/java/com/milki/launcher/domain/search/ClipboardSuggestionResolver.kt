package com.milki.launcher.domain.search

import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.util.UrlValidator

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
 * 2) Phone number
 * 3) Email address
 * 4) Map-like text
 * 5) Plain text search
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

        val normalizedPhone = normalizePhone(rawText)
        if (normalizedPhone != null) {
            return ClipboardSuggestion.DialNumber(
                phoneNumber = normalizedPhone,
                rawText = rawText
            )
        }

        if (Patterns.EMAIL_ADDRESS.matcher(rawText).matches()) {
            return ClipboardSuggestion.ComposeEmail(
                emailAddress = rawText,
                rawText = rawText
            )
        }

        if (looksLikeMapLocation(rawText)) {
            return ClipboardSuggestion.OpenMapLocation(
                locationQuery = rawText,
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
        val validationResult = UrlValidator.validateUrl(rawText) ?: return null
        val handlerApp = urlHandlerResolver.resolveUrlHandler(validationResult.url)

        val result = UrlSearchResult(
            url = validationResult.url,
            displayUrl = validationResult.displayUrl,
            handlerApp = handlerApp,
            browserFallback = true
        )

        return ClipboardSuggestion.OpenUrl(
            urlResult = result,
            rawText = rawText
        )
    }

    /**
     * Lightweight phone normalization for dialer intents.
     *
     * We keep optional leading '+' and digits, and drop formatting separators.
     */
    private fun normalizePhone(rawText: String): String? {
        val candidate = rawText.replace(Regex("[^+\\d]"), "")

        val hasEnoughDigits = candidate.count { it.isDigit() } >= MIN_PHONE_DIGITS
        if (!hasEnoughDigits) return null

        val phonePatternMatches = Patterns.PHONE.matcher(rawText).matches()
        if (!phonePatternMatches) return null

        return candidate
    }

    /**
     * Heuristic map/location detector.
     *
     * Supported examples:
     * - "geo:37.4219983,-122.084"
     * - "37.4219983,-122.084"
     * - "1600 Amphitheatre Parkway, Mountain View"
     * - "maps.google.com/..."
     */
    private fun looksLikeMapLocation(rawText: String): Boolean {
        val lowered = rawText.lowercase()

        if (lowered.startsWith("geo:")) return true
        if (lowered.contains("maps.google.")) return true

        val coordinatePattern = Regex("""^\s*-?\d{1,2}(?:\.\d+)?\s*,\s*-?\d{1,3}(?:\.\d+)?\s*$""")
        if (coordinatePattern.matches(rawText)) return true

        // If text has spaces and at least one comma, it often represents
        // a human-readable location/address. Keep this lenient for usability.
        return rawText.contains(',') && rawText.contains(' ')
    }

    private companion object {
        private const val MIN_PHONE_DIGITS = 5
    }
}
