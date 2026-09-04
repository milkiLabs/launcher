package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.SearchSource

/**
 * Full host for favicon fetch, derived from a source's urlTemplate.
 *
 * Unlike [SearchSourceAppMapper]'s brand token (registrable domain), the
 * favicon must be fetched from the exact host the search runs on, e.g.
 * `en.wikipedia.org` (not `wikipedia.org`), so subdomains keep working.
 * Pure string parsing — no Android, unit-testable.
 */
object FaviconHost {

    fun fromSource(source: SearchSource): String? = fromUrlTemplate(source.urlTemplate)

    fun fromUrlTemplate(urlTemplate: String): String? {
        val withoutScheme = urlTemplate.substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return null
        val hostWithPort = withoutScheme.substringBefore("/").substringBefore("?").trim()
        if (hostWithPort.isEmpty()) return null
        val host = hostWithPort.substringBefore(":").lowercase()
        if ("." !in host || host.startsWith(".") || host.endsWith(".")) return null
        if (host.any { it.isWhitespace() }) return null
        return host
    }
}
