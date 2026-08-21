package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.UrlHandlerApp

/**
 * Resolves the non-browser app that would handle a given URL, if any.
 */
fun interface UrlHandlerPort {
    fun resolveNonBrowserUrlHandler(url: String): UrlHandlerApp?
}
