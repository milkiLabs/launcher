package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.UrlHandlerApp

/**
 * Resolves apps that can handle a given URL.
 *
 * Both methods are binder IPC under the hood and must be called off the main
 * thread (SuggestionResolver already runs on Dispatchers.IO).
 */
interface UrlHandlerPort {
    fun resolveNonBrowserUrlHandler(url: String): UrlHandlerApp?

    fun resolveDefaultBrowser(): UrlHandlerApp?
}
