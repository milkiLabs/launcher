package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.SearchSource

/**
 * Maps a user-configurable [SearchSource] to an installed native app, if any.
 *
 * Pure function over an in-memory package set — no PackageManager IPC, so it is
 * safe to call during uiState derivation on every keystroke.
 *
 * Matching order:
 * 1) Well-known packages for default sources (id or name contains key).
 * 2) Host-derived heuristic: urlTemplate host tokens vs installed package names.
 * 3) null → caller renders monogram fallback.
 */
object SearchSourceAppMapper {

    private val wellKnownPackages: Map<String, List<String>> = mapOf(
        "youtube" to listOf(
            "com.google.android.youtube",
            "com.google.android.youtube.tv",
            "app.revanced.android.youtube"
        ),
        "instagram" to listOf("com.instagram.android"),
        "duckduckgo" to listOf("com.duckduckgo.mobile.android"),
        "brave" to listOf("com.brave.browser"),
        "chrome" to listOf("com.android.chrome"),
        "firefox" to listOf("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix"),
        "edge" to listOf("com.microsoft.emmx"),
        "opera" to listOf("com.opera.browser", "com.opera.mini.native"),
        "samsung internet" to listOf("com.sec.android.app.sbrowser"),
        "samsunginternet" to listOf("com.sec.android.app.sbrowser"),
        "vivaldi" to listOf("com.vivaldi.browser"),
        "google" to listOf(
            "com.google.android.googlequicksearchbox",
            "com.android.chrome"
        ),
        "bing" to listOf("com.microsoft.bing"),
        "ecosia" to listOf("com.ecosia.android"),
        "startpage" to listOf("com.startpage.private_search"),
        "yandex" to listOf("com.yandex.browser", "ru.yandex.searchplugin"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "spotify" to listOf("com.spotify.music"),
        "reddit" to listOf("com.reddit.frontpage"),
        "linkedin" to listOf("com.linkedin.android"),
        "pinterest" to listOf("com.pinterest"),
        "telegram" to listOf("org.telegram.messenger"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "maps" to listOf("com.google.android.apps.maps"),
        "gmail" to listOf("com.google.android.gm")
    )

    /** Generic host labels that must never drive an icon match. */
    private val genericHostTokens: Set<String> = setOf(
        "search", "web", "www", "m", "mobile", "app", "apps", "go",
        "home", "my", "online", "browser", "internet", "newtab", "start",
        "searchbox", "query", "results", "find"
    )

    private const val MIN_HOST_TOKEN_LENGTH = 4

    fun packageFor(source: SearchSource, installedPackages: Set<String>): String? {
        if (installedPackages.isEmpty()) return null

        val keys = buildMatchKeys(source)
        val wellKnown = keys.firstNotNullOfOrNull { key ->
            wellKnownPackages[key]?.firstOrNull { it in installedPackages }
        }

        // Host heuristic for custom sources. Uses exact package-segment match
        // (not substring) so generic "search" never matches Google's
        // "googlequicksearchbox". Generic tokens are skipped entirely.
        val token = hostToken(source.urlTemplate)
            ?.takeIf { it.length >= MIN_HOST_TOKEN_LENGTH && it !in genericHostTokens }
        val hostMatch = token?.let { t ->
            installedPackages.firstOrNull { pkg ->
                pkg.lowercase().split(".").any { it == t }
            }
        }
        return wellKnown ?: hostMatch
    }

    fun packagesFor(
        sources: List<SearchSource>,
        installedPackages: Set<String>
    ): Map<String, String> {
        if (sources.isEmpty() || installedPackages.isEmpty()) return emptyMap()
        return buildMap {
            sources.forEach { source ->
                packageFor(source, installedPackages)?.let { put(source.id, it) }
            }
        }
    }

    private fun buildMatchKeys(source: SearchSource): List<String> {
        val keys = LinkedHashSet<String>()
        source.id.removePrefix(SearchSource.ID_PREFIX).lowercase().let(keys::add)
        source.name.lowercase().let(keys::add)
        hostToken(source.urlTemplate)?.let(keys::add)
        return keys.filter { it.isNotBlank() }
    }

    private fun hostToken(urlTemplate: String): String? {
        return runCatching {
            val withoutScheme = urlTemplate
                .substringAfter("://", missingDelimiterValue = urlTemplate)
            val host = withoutScheme.substringBefore("/").substringBefore("?").lowercase()
            val stripped = host.removePrefix("www.").removePrefix("m.")
            val parts = stripped.split(".").filter { it.isNotBlank() }
            if (parts.isEmpty()) return@runCatching null
            // Registrable-domain heuristic: "search.brave.com" → "brave",
            // "youtube.com" → "youtube", "brave.co.uk" → "brave".
            val token = when {
                parts.size >= 3 && parts.last().length == 2 -> parts[parts.size - 3]
                parts.size >= 3 -> parts[parts.size - 2]
                parts.size == 2 -> parts[0]
                else -> parts[0]
            }
            token.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
