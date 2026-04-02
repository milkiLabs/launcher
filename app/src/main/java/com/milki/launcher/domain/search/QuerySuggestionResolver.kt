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
 * 2) Phone number - Strong signal for calling
 * 3) Email address - Clear intent to send email
 * 4) Map-like text - Could be an address or coordinates
 * 5) Plain text search - Fallback for everything else
 *
 * WHY EXACTLY ONE SUGGESTION:
 * We intentionally return exactly one suggestion to keep the UI simple and avoid
 * overwhelming users with multiple chips. The priority order ensures the most
 * likely intended action is suggested.
 */
package com.milki.launcher.domain.search

import android.util.Patterns
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.core.url.UrlValidator

/**
 * Analyzes query text and returns the best single suggestion for the user.
 *
 * This class encapsulates all the logic for determining what action to suggest
 * based on what the user is typing. It uses a combination of:
 * - Android's built-in patterns (URL, phone, email)
 * - Custom heuristics (map locations)
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
     * 2. Phone number detection (via Patterns.PHONE)
     * 3. Email address detection (via Patterns.EMAIL_ADDRESS)
     * 4. Map/location heuristics
     * 5. Plain text search fallback
     *
     * @param rawText The trimmed query text
     * @return Exactly one suggestion based on priority rules
     */
    private fun resolveFromText(rawText: String): QuerySuggestion {
        // Priority 1: URL detection
        val urlSuggestion = resolveUrlSuggestion(rawText)
        if (urlSuggestion != null) return urlSuggestion

        // Priority 2: Phone number detection
        val normalizedPhone = normalizePhone(rawText)
        if (normalizedPhone != null) {
            return QuerySuggestion.DialNumber(
                phoneNumber = normalizedPhone,
                rawQuery = rawText
            )
        }

        // Priority 3: Email address detection
        if (Patterns.EMAIL_ADDRESS.matcher(rawText).matches()) {
            return QuerySuggestion.ComposeEmail(
                emailAddress = rawText,
                rawQuery = rawText
            )
        }

        // Priority 4: Map/location heuristics
        if (looksLikeMapLocation(rawText)) {
            return QuerySuggestion.OpenMapLocation(
                locationQuery = rawText,
                rawQuery = rawText
            )
        }

        // Priority 5: Plain text search fallback
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
        // Use UrlValidator to check if this is a valid URL
        val validationResult = UrlValidator.validateUrl(rawText) ?: return null

        // Resolve which app can handle this URL (e.g., YouTube for youtube.com)
        val handlerApp = urlHandlerResolver.resolveUrlHandler(validationResult.url)

        // Create the UrlSearchResult with handler information
        val result = UrlSearchResult(
            url = validationResult.url,
            displayUrl = validationResult.displayUrl,
            handlerApp = handlerApp,
            browserFallback = true
        )

        return QuerySuggestion.OpenUrl(
            urlResult = result,
            rawQuery = rawText
        )
    }

    /**
     * Lightweight phone normalization for dialer intents.
     *
     * We keep optional leading '+' and digits, and drop formatting separators.
     * This handles various phone number formats:
     * - "+1 (555) 123-4567" → "+15551234567"
     * - "555-123-4567" → "5551234567"
     * - "call 5551234567" → null (has letters, not a pure number)
     *
     * VALIDATION:
     * - Must have at least MIN_PHONE_DIGITS (5) digits
     * - Must match Android's Patterns.PHONE regex
     * - Must only contain digits and optional leading '+'
     *
     * @param rawText The text to normalize
     * @return Normalized phone number, or null if not a valid phone
     */
    private fun normalizePhone(rawText: String): String? {
        // Remove all non-digit/non-plus characters
        val candidate = rawText.replace(Regex("[^+\\d]"), "")

        // Must have enough digits to be a real phone number
        val hasEnoughDigits = candidate.count { it.isDigit() } >= MIN_PHONE_DIGITS
        if (!hasEnoughDigits) return null

        // Must match Android's phone pattern
        val phonePatternMatches = Patterns.PHONE.matcher(rawText).matches()
        if (!phonePatternMatches) return null

        return candidate
    }

    /**
     * Heuristic map/location detector.
     *
     * This method uses several heuristics to detect if the query looks like
     * a location that should be opened in a maps app.
     *
     * SUPPORTED PATTERNS:
     * - "geo:37.4219983,-122.084" - Geo URI scheme
     * - "37.4219983,-122.084" - Raw coordinates
     * - "1600 Amphitheatre Parkway, Mountain View" - Addresses with commas and spaces
     * - "maps.google.com/..." - Google Maps URLs
     *
     * WHY THESE HEURISTICS:
     * - Geo URIs are explicit location markers
     * - Google Maps URLs are common paste targets
     * - Coordinates pattern catches raw lat/long
     * - Comma + space pattern catches most addresses
     *
     * @param rawText The query text to analyze
     * @return true if the text looks like a location query
     */
    private fun looksLikeMapLocation(rawText: String): Boolean {
        val lowered = rawText.lowercase()

        // Check for geo: URI scheme (e.g., "geo:37.4219983,-122.084")
        if (lowered.startsWith("geo:")) return true

        // Check for Google Maps URLs
        if (lowered.contains("maps.google.")) return true

        // Check for coordinate pattern (e.g., "37.4219983,-122.084")
        // Pattern: optional whitespace, optional minus, digits, optional decimal,
        //          comma, optional whitespace, optional minus, digits, optional decimal
        val coordinatePattern = Regex("""^\s*-?\d{1,2}(?:\.\d+)?\s*,\s*-?\d{1,3}(?:\.\d+)?\s*$""")
        if (coordinatePattern.matches(rawText)) return true

        // If text has spaces and at least one comma, it often represents
        // a human-readable location/address. Keep this lenient for usability.
        // Examples: "1600 Amphitheatre Parkway, Mountain View"
        return rawText.contains(',') && rawText.contains(' ')
    }

    private companion object {
        /**
         * Minimum number of digits required to consider text a phone number.
         * This prevents false positives on short strings like "123".
         */
        private const val MIN_PHONE_DIGITS = 5
    }
}
