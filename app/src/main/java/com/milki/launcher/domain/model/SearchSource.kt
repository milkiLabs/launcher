package com.milki.launcher.domain.model

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * SearchSource.kt - User-configurable search source model
 *
 * This file defines a fully user-configurable "search source" concept used by the
 * launcher's unified external search system.
 *
 * WHY THIS MODEL EXISTS:
 * Historically, web and YouTube searches were hard-coded in provider classes.
 * That made it hard for users to:
 * - Add new sources like Instagram or Twitter/X
 * - Change prefixes without being constrained to one predefined source
 * - Customize source visuals (accent colors) in the UI
 * - Delete sources they do not need
 *
 * With SearchSource, each source becomes plain data stored in settings.
 * The runtime creates providers from this data, so the feature is dynamic.
 */

/**
 * Serializable configuration for one external search source.
 *
 * @property id Stable unique source identifier.
 *              - Generated once and persisted.
 *              - Used for lookups, updates, and provider IDs.
 *
 * @property name User-visible source name shown in Settings and search results.
 *
 * @property urlTemplate URL template used to produce final search URL.
 *                       Must contain the `{query}` placeholder exactly once.
 *                       Example: `https://www.youtube.com/results?search_query={query}`
 *
 * @property prefixes Prefixes that activate this source in provider mode.
 *                    Parsing rule in query parser remains `prefix + space`.
 *
 * @property isEnabled If false, this source is excluded from prefix activation.
 *
 * @property accentColorHex Custom user-selected color used by UI accents for this
 *                          source. Stored as `#RRGGBB` for simplicity.
 */
@Serializable
data class SearchSource(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val prefixes: List<String>,
    val isEnabled: Boolean,
    val accentColorHex: String
) {

    /**
     * Returns the first configured prefix, or empty string if none exists.
     * This value is used only as fallback display/default metadata.
     */
    val primaryPrefix: String
        get() = prefixes.firstOrNull().orEmpty()

    /**
     * Builds a final URL by replacing `{query}` with URI-encoded query text.
     *
     * IMPORTANT:
     * - The placeholder replacement is intentionally straightforward and predictable.
     * - Validation of placeholder existence is handled by helper methods and UI.
     */
    fun buildUrl(encodedQuery: String): String {
        return urlTemplate.replace("{query}", encodedQuery)
    }

    companion object {

        /**
         * Creates a new source instance with generated stable ID.
         */
        fun create(
            name: String,
            urlTemplate: String,
            prefixes: List<String>,
            accentColorHex: String,
            isEnabled: Boolean = true
        ): SearchSource {
            return SearchSource(
                id = "source_${UUID.randomUUID()}",
                name = name,
                urlTemplate = urlTemplate,
                prefixes = prefixes,
                isEnabled = isEnabled,
                accentColorHex = normalizeHexColor(accentColorHex)
            )
        }

        /**
         * Default starter sources used for first run and reset fallback.
         *
         * These defaults intentionally include the user-requested examples:
         * - YouTube
         * - Instagram
         * - Twitter/X
         *
         * We also include web engines so web search and source search are unified.
         */
        fun defaultSources(): List<SearchSource> {
            return listOf(
                SearchSource(
                    id = "source_google",
                    name = "Google",
                    urlTemplate = "https://www.google.com/search?q={query}",
                    prefixes = listOf("g"),
                    isEnabled = true,
                    accentColorHex = "#4285F4"
                ),
                SearchSource(
                    id = "source_duckduckgo",
                    name = "DuckDuckGo",
                    urlTemplate = "https://duckduckgo.com/?q={query}",
                    prefixes = listOf("d"),
                    isEnabled = true,
                    accentColorHex = "#DE5833"
                ),
                SearchSource(
                    id = "source_youtube",
                    name = "YouTube",
                    urlTemplate = "https://www.youtube.com/results?search_query={query}",
                    prefixes = listOf("y", "yt"),
                    isEnabled = true,
                    accentColorHex = "#FF0000"
                ),
                SearchSource(
                    id = "source_instagram",
                    name = "Instagram",
                    urlTemplate = "https://www.instagram.com/explore/tags/{query}/",
                    prefixes = listOf("ig"),
                    isEnabled = true,
                    accentColorHex = "#E1306C"
                ),
                SearchSource(
                    id = "source_twitter",
                    name = "Twitter/X",
                    urlTemplate = "https://x.com/search?q={query}",
                    prefixes = listOf("x", "tw"),
                    isEnabled = true,
                    accentColorHex = "#1D9BF0"
                )
            )
        }

        /**
         * Returns true only when template is usable for runtime URL generation.
         */
        fun isValidUrlTemplate(template: String): Boolean {
            val normalized = template.trim()
            if (normalized.isEmpty()) return false
            if (!normalized.contains("{query}")) return false
            val hasWebScheme = normalized.startsWith("https://") || normalized.startsWith("http://")
            return hasWebScheme
        }

        /**
         * Normalizes user prefix input by trimming and lowercasing.
         *
         * This supports case-insensitive matching and stable persistence.
         */
        fun normalizePrefix(prefix: String): String {
            return prefix.trim().lowercase()
        }

        /**
         * Normalizes color input into strict #RRGGBB form.
         * Falls back to Google blue when input is invalid.
         */
        fun normalizeHexColor(raw: String): String {
            val candidate = raw.trim().uppercase()
            val withHash = if (candidate.startsWith("#")) candidate else "#$candidate"
            val regex = Regex("^#[0-9A-F]{6}$")
            return if (regex.matches(withHash)) withHash else "#4285F4"
        }
    }
}
