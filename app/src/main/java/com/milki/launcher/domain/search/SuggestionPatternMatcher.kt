package com.milki.launcher.domain.search

import com.milki.launcher.core.url.UrlValidator
import com.milki.launcher.domain.model.UrlSearchResult

/**
 * Shared parsing helpers for query and clipboard suggestion resolvers.
 */
internal object SuggestionPatternMatcher {

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
}
