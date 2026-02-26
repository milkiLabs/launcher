/**
 * UrlValidator.kt - Centralized URL validation and normalization utility
 *
 * This file provides a single, well-tested place for all URL detection logic.
 * Previously, URL detection was scattered in SearchViewModel with complex
 * multi-stage logic that was hard to maintain and test.
 *
 * WHY THIS FILE EXISTS:
 * - URL detection logic was complex and duplicated
 * - Multiple regex patterns made maintenance difficult
 * - No centralized place for URL normalization
 * - Testing URL detection separately from ViewModel was impossible
 *
 * DESIGN DECISIONS:
 * 1. Uses Android's built-in Patterns.WEB_URL as primary validation
 *    - This is maintained by Google and updated with new Android versions
 *    - Handles most common URL formats correctly
 *
 * 2. Simple fallback for edge cases (newer TLDs, regional TLDs)
 *    - Android's pattern may not recognize very new TLDs
 *    - Our fallback uses a simple pattern: domain.tld with 2+ letter TLD
 *
 * 3. All normalization happens in one place
 *    - Adding https:// scheme
 *    - Handling www. prefix
 *    - Ensuring consistent URL format
 *
 * USAGE:
 * ```kotlin
 * val result = UrlValidator.validateUrl("youtube.com")
 * if (result != null) {
 *     // result.url = "https://youtube.com"
 *     // result.displayUrl = "youtube.com"
 * }
 * ```
 */

package com.milki.launcher.util

import android.util.Patterns

/**
 * Result of URL validation containing both the normalized URL
 * and the original display text.
 *
 * @property url The normalized URL with scheme (e.g., "https://example.com")
 * @property displayUrl The original input text (e.g., "example.com")
 */
data class UrlValidationResult(
    val url: String,
    val displayUrl: String
)

/**
 * Utility object for URL validation and normalization.
 *
 * This object provides a single entry point for all URL detection logic.
 * It uses a simple, maintainable approach:
 *
 * 1. Fast-fail for obviously non-URL inputs (empty, spaces)
 * 2. Normalize URLs with known prefixes (www., http://, https://)
 * 3. Validate using Android's built-in WEB_URL pattern
 * 4. Simple fallback for newer/regional TLDs
 *
 * WHY NO HARDCODED TLD LIST:
 * - There are 1500+ TLDs and growing
 * - Hardcoded lists become outdated quickly
 * - Android's Patterns.WEB_URL is updated with each Android release
 * - Our simple fallback handles any 2+ letter TLD
 */
object UrlValidator {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    /**
     * URL schemes that indicate the user is typing a URL.
     * These are strong signals that the input should be treated as a URL.
     */
    private const val SCHEME_HTTP = "http://"
    private const val SCHEME_HTTPS = "https://"
    private const val PREFIX_WWW = "www."

    /**
     * Fallback regex pattern for URL detection.
     *
     * This pattern is used when Android's built-in WEB_URL pattern
     * doesn't match, which can happen with:
     * - Very new TLDs (Android's pattern may not be updated)
     * - Regional TLDs in non-latin scripts
     * - Less common TLDs
     *
     * Pattern explanation:
     * - ^                    Start of string
     * - [a-zA-Z0-9]          First character must be alphanumeric
     * - [a-zA-Z0-9-]*        Domain can have alphanumeric and hyphens
     * - \.                   Literal dot before TLD
     * - [a-zA-Z]{2,}         TLD must be at least 2 letters
     * - (?:/.*)?             Optional path starting with /
     * - $                    End of string
     *
     * Examples that match:
     * - "example.com"
     * - "my-site.org"
     * - "domain.co.uk"
     * - "site.ai"
     * - "example.com/path"
     *
     * Examples that don't match:
     * - "example" (no TLD)
     * - ".com" (no domain)
     * - "example." (no TLD after dot)
     * - "hi" (too short, no dot)
     */
    private val fallbackUrlPattern = Regex(
        "^[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,}(?:/.*)?$"
    )

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Validate and normalize a URL from user input.
     *
     * This is the main entry point for URL detection. It takes raw user input
     * and returns a validated, normalized URL if the input looks like a URL.
     *
     * VALIDATION STRATEGY:
     * 1. Fast-fail: Empty strings or strings with spaces are not URLs
     *    - Spaces indicate search queries, not URLs
     *    - "my website.com" is clearly a search, not a URL
     *
     * 2. Normalize prefixes: Handle http://, https://, and www.
     *    - "www.example.com" becomes "https://www.example.com"
     *    - "http://example.com" stays as-is
     *
     * 3. Validate format: Check if it looks like a valid URL
     *    - Use Android's Patterns.WEB_URL first
     *    - Fall back to simple regex for edge cases
     *
     * 4. Ensure scheme: All URLs need https:// for browser intent
     *
     * @param input The raw user input (e.g., "youtube.com", "www.google.com")
     * @return UrlValidationResult if valid URL, null otherwise
     *
     * Examples:
     * ```kotlin
     * UrlValidator.validateUrl("youtube.com")
     * // Returns: UrlValidationResult(url="https://youtube.com", displayUrl="youtube.com")
     *
     * UrlValidator.validateUrl("www.google.com")
     * // Returns: UrlValidationResult(url="https://www.google.com", displayUrl="www.google.com")
     *
     * UrlValidator.validateUrl("https://example.com/path")
     * // Returns: UrlValidationResult(url="https://example.com/path", displayUrl="https://example.com/path")
     *
     * UrlValidator.validateUrl("hello world")
     * // Returns: null (has spaces, not a URL)
     *
     * UrlValidator.validateUrl("")
     * // Returns: null (empty string)
     * ```
     */
    fun validateUrl(input: String): UrlValidationResult? {
        // Step 1: Trim and check for fast-fail conditions
        val trimmed = input.trim()
        
        // FAST-FAIL: Empty or space-containing queries aren't URLs
        // Users searching for apps will type single words like "youtube" or "maps"
        // URLs typed by users never have spaces
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            return null
        }
        
        // Step 2: Determine if we have an explicit URL prefix
        val hasSchemePrefix = hasExplicitUrlPrefix(trimmed)
        
        // Step 3: Try to validate and extract the URL
        val validatedUrl = when {
            // Case A: Has explicit prefix (http://, https://, www.)
            hasSchemePrefix -> validateWithPrefix(trimmed)
            
            // Case B: No prefix, try standard validation
            else -> validateWithoutPrefix(trimmed)
        }
        
        // Step 4: Return null if no valid URL was found
        if (validatedUrl == null) return null
        
        // Step 5: Ensure the URL has a scheme for Intent.ACTION_VIEW
        // Without https://, the intent won't open a browser
        val normalizedUrl = ensureScheme(validatedUrl)
        
        return UrlValidationResult(
            url = normalizedUrl,
            displayUrl = trimmed
        )
    }

    /**
     * Check if a string looks like it might be a URL.
     *
     * This is a lighter-weight check that can be used for early filtering
     * before doing full validation. It's useful for:
     * - Quickly deciding whether to show a "visit URL" option
     * - Filtering search results before more expensive checks
     *
     * @param input The string to check
     * @return true if the string might be a URL, false otherwise
     */
    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        
        // Fast-fail conditions
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            return false
        }
        
        // Check for URL indicators
        return hasExplicitUrlPrefix(trimmed) || looksLikeDomain(trimmed)
    }

    // ========================================================================
    // PRIVATE HELPER FUNCTIONS
    // ========================================================================

    /**
     * Check if the input has an explicit URL prefix.
     *
     * Explicit prefixes are strong indicators that the user intends
     * to visit a URL rather than search for something.
     *
     * @param input The trimmed input string
     * @return true if input starts with http://, https://, or www.
     */
    private fun hasExplicitUrlPrefix(input: String): Boolean {
        return input.startsWith(SCHEME_HTTP, ignoreCase = true) ||
               input.startsWith(SCHEME_HTTPS, ignoreCase = true) ||
               input.startsWith(PREFIX_WWW, ignoreCase = true)
    }

    /**
     * Validate a URL that has an explicit prefix.
     *
     * When the user explicitly types http://, https://, or www.,
     * we have higher confidence this is a URL and should validate it.
     *
     * @param input The trimmed input with a URL prefix
     * @return Validated URL string, or null if invalid
     */
    private fun validateWithPrefix(input: String): String? {
        // Normalize www. to https://www. for validation
        val urlToValidate = when {
            input.startsWith(PREFIX_WWW, ignoreCase = true) -> {
                "$SCHEME_HTTPS$input"
            }
            else -> {
                input
            }
        }
        
        // Validate using Android's built-in pattern
        return if (Patterns.WEB_URL.matcher(urlToValidate).matches()) {
            urlToValidate
        } else {
            null
        }
    }

    /**
     * Validate a URL without an explicit prefix.
     *
     * For inputs like "example.com" without http:// or www.,
     * we need to be more careful to avoid false positives.
     *
     * Strategy:
     * 1. Try Android's WEB_URL pattern first
     * 2. Fall back to our simple pattern for newer TLDs
     *
     * @param input The trimmed input without a URL prefix
     * @return Validated URL string, or null if invalid
     */
    private fun validateWithoutPrefix(input: String): String? {
        // STAGE 1: Try Android's built-in WEB_URL pattern
        // This handles most standard URL formats
        if (Patterns.WEB_URL.matcher(input).matches()) {
            return input
        }
        
        // STAGE 2: Fallback regex for newer/regional TLDs
        // Older Android versions may not recognize newer TLDs like .ai, .eg, .shop
        // This pattern matches: domain.tld or domain.tld/path with any 2+ letter TLD
        if (fallbackUrlPattern.matches(input)) {
            return input
        }
        
        return null
    }

    /**
     * Ensure a URL has a scheme (https://).
     *
     * URLs need a scheme for Android's Intent.ACTION_VIEW to work properly.
     * Without it, the intent won't open a browser.
     *
     * @param url The URL to check
     * @return URL with scheme guaranteed
     */
    private fun ensureScheme(url: String): String {
        return when {
            url.startsWith(SCHEME_HTTP, ignoreCase = true) -> url
            url.startsWith(SCHEME_HTTPS, ignoreCase = true) -> url
            else -> "$SCHEME_HTTPS$url"
        }
    }

    /**
     * Check if a string looks like a domain name.
     *
     * A simple heuristic check for domain-like strings.
     * Used for quick filtering before full validation.
     *
     * @param input The string to check
     * @return true if it looks like a domain
     */
    private fun looksLikeDomain(input: String): Boolean {
        // Must contain a dot (domains have TLDs)
        if (!input.contains(".")) return false
        
        // Must not start or end with a dot
        if (input.startsWith(".") || input.endsWith(".")) return false
        
        // Simple check: word.word pattern
        val parts = input.split(".")
        return parts.size >= 2 && parts.all { it.isNotEmpty() }
    }
}
