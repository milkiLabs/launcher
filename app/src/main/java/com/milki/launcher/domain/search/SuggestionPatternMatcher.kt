package com.milki.launcher.domain.search

import com.milki.launcher.core.url.UrlValidator
import com.milki.launcher.domain.model.UrlSearchResult

/**
 * Shared parsing helpers for query and clipboard suggestion resolvers.
 */
internal object SuggestionPatternMatcher {

    fun resolveUrlResult(
        rawText: String,
        urlHandlerPort: UrlHandlerPort
    ): UrlSearchResult? {
        val validationResult = UrlValidator.validateUrl(rawText) ?: return null
        val handlerApp = urlHandlerPort.resolveNonBrowserUrlHandler(validationResult.url)
        val browserApp = runCatching { urlHandlerPort.resolveDefaultBrowser() }.getOrNull()

        return UrlSearchResult(
            url = validationResult.url,
            displayUrl = validationResult.displayUrl,
            handlerApp = handlerApp,
            browserApp = browserApp,
            browserFallback = true
        )
    }
}
