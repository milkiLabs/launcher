package com.milki.launcher.domain.model

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * SearchSource.kt - User-configurable search source model
 *
 * This file defines a fully user-configurable "search source" concept used by the
 * launcher's unified external search system.
 */

@Serializable
data class SearchSource(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val prefixes: List<String>,
    val isEnabled: Boolean,
    val showAsSuggestedAction: Boolean = true,
    val accentColorHex: String,
    val defaultPrefixes: List<String> = emptyList()
) {

    val primaryPrefix: String
        get() = prefixes.firstOrNull().orEmpty()

    fun buildUrl(encodedQuery: String): String {
        return urlTemplate.replace("{query}", encodedQuery)
    }

    companion object {

        /** Prefix shared by all dynamically-created source IDs (e.g., "source_youtube"). */
        const val ID_PREFIX = "source_"

        fun create(
            name: String,
            urlTemplate: String,
            prefixes: List<String>,
            accentColorHex: String,
            isEnabled: Boolean = true,
            showAsSuggestedAction: Boolean = true
        ): SearchSource {
            val normalizedPrefixes = normalizePrefixes(prefixes)
            return SearchSource(
                id = "$ID_PREFIX${UUID.randomUUID()}",
                name = name,
                urlTemplate = urlTemplate,
                prefixes = normalizedPrefixes,
                isEnabled = isEnabled,
                showAsSuggestedAction = showAsSuggestedAction,
                accentColorHex = normalizeHexColor(accentColorHex),
                defaultPrefixes = normalizedPrefixes
            )
        }

        fun defaultSources(): List<SearchSource> {
            return listOf(
                SearchSource(
                    id = "${ID_PREFIX}duckduckgo",
                    name = "DuckDuckGo",
                    urlTemplate = "https://duckduckgo.com/?q={query}",
                    prefixes = listOf("k"),
                    isEnabled = true,
                    showAsSuggestedAction = true,
                    accentColorHex = "#DE5833",
                    defaultPrefixes = listOf("k")
                ),
                SearchSource(
                    id = "${ID_PREFIX}youtube",
                    name = "YouTube",
                    urlTemplate = "https://www.youtube.com/results?search_query={query}",
                    prefixes = listOf("y", "yt"),
                    isEnabled = true,
                    showAsSuggestedAction = true,
                    accentColorHex = "#FF0000",
                    defaultPrefixes = listOf("y", "yt")
                ),
                SearchSource(
                    id = "${ID_PREFIX}instagram",
                    name = "Instagram",
                    urlTemplate = "https://www.instagram.com/explore/tags/{query}/",
                    prefixes = listOf("ig"),
                    isEnabled = true,
                    showAsSuggestedAction = true,
                    accentColorHex = "#E1306C",
                    defaultPrefixes = listOf("ig")
                )
            )
        }

        fun isValidUrlTemplate(template: String): Boolean {
            val normalized = template.trim()
            if (normalized.isEmpty()) return false
            if (!normalized.contains("{query}")) return false
            val hasWebScheme = normalized.startsWith("https://") || normalized.startsWith("http://")
            return hasWebScheme
        }

        fun normalizePrefix(prefix: String): String {
            return prefix.trim().lowercase()
        }

        fun normalizePrefixes(prefixes: List<String>): List<String> {
            return prefixes
                .map(::normalizePrefix)
                .filter { it.isNotBlank() && !it.contains(" ") }
                .distinct()
        }

        fun normalizeHexColor(raw: String): String {
            val candidate = raw.trim().uppercase()
            val withHash = if (candidate.startsWith("#")) candidate else "#$candidate"
            val regex = Regex("^#[0-9A-F]{6}$")
            return if (regex.matches(withHash)) withHash else "#4285F4"
        }
    }
}
