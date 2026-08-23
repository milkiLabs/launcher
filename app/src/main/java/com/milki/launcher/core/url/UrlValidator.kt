package com.milki.launcher.core.url

import android.util.Patterns

/**
 * Validates and normalizes URL-ish user input.
 *
 * Primary validation uses Android's [Patterns.WEB_URL] (maintained by Google,
 * updated with new TLDs); a simple `domain.tld` fallback covers newer/regional
 * TLDs it may miss. All normalization (scheme handling) happens here.
 */
data class UrlValidationResult(
    val url: String,
    val displayUrl: String
)

data class UrlDestinationValidationResult(
    val uri: String,
    val displayText: String,
    val isWebUrl: Boolean
)

object UrlValidator {

    private const val SCHEME_HTTP = "http://"
    private const val SCHEME_HTTPS = "https://"
    private const val PREFIX_WWW = "www."

    private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*$")

    // Matches "domain.tld" with a 2+ letter TLD and optional "/path".
    private val fallbackUrlPattern = Regex(
        "^[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,}(?:/.*)?$"
    )

    /** Returns a normalized URL for [input], or null if it isn't a URL. */
    fun validateUrl(input: String): UrlValidationResult? {
        val trimmed = input.trim()
        val validatedUrl = when {
            trimmed.isEmpty() || trimmed.contains(" ") -> null
            hasExplicitUrlPrefix(trimmed) -> validateWithPrefix(trimmed)
            else -> validateWithoutPrefix(trimmed)
        }

        return validatedUrl?.let { url ->
            UrlValidationResult(
                url = ensureScheme(url),
                displayUrl = trimmed
            )
        }
    }

    /** Like [validateUrl], but also accepts non-web URIs with an explicit scheme. */
    fun validateUrlOrUri(input: String): UrlDestinationValidationResult? {
        val webUrl = validateUrl(input)
        if (webUrl != null) {
            return UrlDestinationValidationResult(
                uri = webUrl.url,
                displayText = webUrl.displayUrl,
                isWebUrl = true
            )
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ") || !hasExplicitScheme(trimmed)) return null

        return UrlDestinationValidationResult(
            uri = trimmed,
            displayText = trimmed,
            isWebUrl = false
        )
    }

    /** Cheap heuristic check for use before full validation. */
    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) return false
        return hasExplicitUrlPrefix(trimmed) ||
            trimmed.split(".").let { it.size >= 2 && it.all(String::isNotEmpty) }
    }

    private fun hasExplicitUrlPrefix(input: String): Boolean =
        input.startsWith(SCHEME_HTTP, ignoreCase = true) ||
            input.startsWith(SCHEME_HTTPS, ignoreCase = true) ||
            input.startsWith(PREFIX_WWW, ignoreCase = true)

    private fun hasExplicitScheme(input: String): Boolean {
        val schemeEnd = input.indexOf(':')
        return schemeEnd > 0 && input.take(schemeEnd).matches(SCHEME_PATTERN)
    }

    private fun validateWithPrefix(input: String): String? {
        val urlToValidate =
            if (input.startsWith(PREFIX_WWW, ignoreCase = true)) "$SCHEME_HTTPS$input" else input
        return urlToValidate.takeIf { Patterns.WEB_URL.matcher(it).matches() }
    }

    private fun validateWithoutPrefix(input: String): String? =
        input.takeIf { Patterns.WEB_URL.matcher(it).matches() || fallbackUrlPattern.matches(it) }

    private fun ensureScheme(url: String): String =
        if (hasExplicitUrlPrefix(url)) url else "$SCHEME_HTTPS$url"
}
