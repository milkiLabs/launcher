package com.milki.launcher.domain.search

import android.util.Patterns
import com.milki.launcher.core.url.UrlValidator
import com.milki.launcher.domain.model.UrlSearchResult

/**
 * Shared parsing helpers for query and clipboard suggestion resolvers.
 */
internal object SuggestionPatternMatcher {

    private const val MIN_PHONE_DIGITS = 5

    private val coordinatePattern =
        Regex("""^\s*-?\d{1,2}(?:\.\d+)?\s*,\s*-?\d{1,3}(?:\.\d+)?\s*$""")

    fun resolveUrlResult(
        rawText: String,
        urlHandlerResolver: UrlHandlerResolver
    ): UrlSearchResult? {
        val validationResult = UrlValidator.validateUrl(rawText) ?: return null
        val handlerApp = urlHandlerResolver.resolveUrlHandler(validationResult.url)

        return UrlSearchResult(
            url = validationResult.url,
            displayUrl = validationResult.displayUrl,
            handlerApp = handlerApp,
            browserFallback = true
        )
    }

    fun normalizePhone(
        rawText: String,
        isPhonePatternMatch: (String) -> Boolean = { candidate ->
            Patterns.PHONE.matcher(candidate).matches()
        }
    ): String? {
        val candidate = rawText.replace(Regex("[^+\\d]"), "")

        val hasEnoughDigits = candidate.count { it.isDigit() } >= MIN_PHONE_DIGITS
        if (!hasEnoughDigits) return null

        if (!isPhonePatternMatch(rawText)) return null

        return candidate
    }

    fun looksLikeMapLocation(rawText: String): Boolean {
        val lowered = rawText.lowercase()

        if (lowered.startsWith("geo:")) return true
        if (lowered.contains("maps.google.")) return true
        if (coordinatePattern.matches(rawText)) return true

        return rawText.contains(',') && rawText.contains(' ')
    }
}
