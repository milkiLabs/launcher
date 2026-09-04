package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSourceAppMapperTest {

    private fun source(
        id: String = "source_youtube",
        name: String = "YouTube",
        urlTemplate: String = "https://www.youtube.com/results?search_query={query}"
    ) = SearchSource(
        id = id,
        name = name,
        urlTemplate = urlTemplate,
        prefixes = listOf("y"),
        isEnabled = true,
        showAsSuggestedAction = true,
        accentColorHex = "#FF0000"
    )

    @Test
    fun youtube_source_maps_when_installed() {
        val result = SearchSourceAppMapper.packageFor(
            source(),
            setOf("com.google.android.youtube", "com.android.chrome")
        )

        assertEquals("com.google.android.youtube", result)
    }

    @Test
    fun returns_null_when_no_match() {
        val duckDuckGo = source(
            id = "source_duckduckgo",
            name = "DuckDuckGo",
            urlTemplate = "https://duckduckgo.com/?q={query}"
        )

        val result = SearchSourceAppMapper.packageFor(
            duckDuckGo,
            setOf("com.android.chrome")
        )

        assertNull(result)
    }

    @Test
    fun returns_null_when_nothing_installed() {
        val result = SearchSourceAppMapper.packageFor(source(), emptySet())

        assertNull(result)
    }

    @Test
    fun custom_source_never_filters_out() {
        // Mapper returns null for unknown hosts; UI must still render monogram.
        val custom = source(
            id = "source_custom",
            name = "My Wiki",
            urlTemplate = "https://wiki.mycompany.internal/search?q={query}"
        )

        val result = SearchSourceAppMapper.packageFor(
            custom,
            setOf("com.android.chrome")
        )

        assertNull(result)
    }

    @Test
    fun packagesFor_only_contains_matched_sources() {
        val sources = listOf(
            source(),
            source(
                id = "source_duckduckgo",
                name = "DuckDuckGo",
                urlTemplate = "https://duckduckgo.com/?q={query}"
            )
        )

        val result = SearchSourceAppMapper.packagesFor(
            sources,
            setOf("com.google.android.youtube")
        )

        assertEquals(mapOf("source_youtube" to "com.google.android.youtube"), result)
    }

    @Test
    fun brave_search_maps_to_brave_browser() {
        val brave = source(
            id = "source_brave",
            name = "Brave",
            urlTemplate = "https://search.brave.com/search?q={query}"
        )

        val result = SearchSourceAppMapper.packageFor(
            brave,
            setOf(
                "com.brave.browser",
                "com.google.android.googlequicksearchbox",
                "com.android.chrome"
            )
        )

        assertEquals("com.brave.browser", result)
    }

    @Test
    fun generic_search_subdomain_never_matches_google_app() {
        // Regression: "search.brave.com" used to yield token "search", which
        // substring-matched "googlequicksearchbox" and showed the Google icon
        // for a Brave source.
        val brave = source(
            id = "source_custom_brave",
            name = "Brave Search",
            urlTemplate = "https://search.brave.com/search?q={query}"
        )

        val result = SearchSourceAppMapper.packageFor(
            brave,
            setOf("com.google.android.googlequicksearchbox", "com.android.chrome")
        )

        assertNull(result)
    }
}
