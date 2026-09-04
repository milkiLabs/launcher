package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconHostTest {

    private fun source(urlTemplate: String) = SearchSource(
        id = "source_test",
        name = "Test",
        urlTemplate = urlTemplate,
        prefixes = listOf("t"),
        isEnabled = true,
        showAsSuggestedAction = true,
        accentColorHex = "#4285F4"
    )

    @Test
    fun keeps_subdomain_for_fetch() {
        // Favicon must come from the exact host (en.wikipedia.org), unlike
        // the brand token used for app mapping.
        assertEquals(
            "en.wikipedia.org",
            FaviconHost.fromSource(source("https://en.wikipedia.org/w/index.php?search={query}"))
        )
    }

    @Test
    fun extracts_host_from_brave_search() {
        assertEquals(
            "search.brave.com",
            FaviconHost.fromSource(source("https://search.brave.com/search?q={query}"))
        )
    }

    @Test
    fun returns_null_for_non_web_template() {
        assertNull(FaviconHost.fromUrlTemplate("not a url"))
        assertNull(FaviconHost.fromUrlTemplate(""))
        assertNull(FaviconHost.fromUrlTemplate("mailto:foo@{query}"))
    }
}
