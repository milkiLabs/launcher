package com.milki.launcher.domain.search

import java.net.URLEncoder

/**
 * UrlActionResolver.kt - Pure decisions for URL / YouTube launch flows.
 *
 * Extracted from ActionExecutor so package-selection and browser-fallback
 * policies live in a pure, unit-testable object; the caller stays a thin
 * Android adapter that feeds it PackageManager results.
 */
object UrlActionResolver {

    /**
     * Ordered candidate strategies for opening a URL externally:
     * pin to the default browser (when resolvable), then fall back to
     * the system chooser.
     */
    sealed interface ExternalBrowserStep {
        data class Pinned(val packageName: String?) : ExternalBrowserStep
        data object SystemChooser : ExternalBrowserStep
    }

    fun youtubeSearchUrl(query: String): String =
        "https://www.youtube.com/results?search_query=${encodeQuery(query)}"

    /**
     * Picks the YouTube app package from resolved activity packages,
     * matching "youtube" case-insensitively anywhere in the name.
     * Returns null when no candidate matches (generic handler is used).
     */
    fun selectYoutubePackage(candidatePackageNames: List<String>): String? =
        candidatePackageNames.firstOrNull { it.contains("youtube", ignoreCase = true) }

    /**
     * External-browser launch plan: always try the pinned/default-browser
     * intent first, then offer the system chooser. A null preferred package
     * yields an unpinned direct view followed by the chooser.
     */
    fun externalBrowserSteps(defaultBrowserPackage: String?): List<ExternalBrowserStep> =
        listOf(
            ExternalBrowserStep.Pinned(defaultBrowserPackage),
            ExternalBrowserStep.SystemChooser
        )

    private fun encodeQuery(query: String): String =
        URLEncoder.encode(query, Charsets.UTF_8.name())
}
