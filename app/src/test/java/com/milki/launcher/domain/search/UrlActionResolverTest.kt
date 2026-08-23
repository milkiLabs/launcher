package com.milki.launcher.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlActionResolverTest {

    @Test
    fun youtubeSearchUrl_encodes_query() {
        assertEquals(
            "https://www.youtube.com/results?search_query=cat+videos",
            UrlActionResolver.youtubeSearchUrl("cat videos")
        )
    }

    @Test
    fun selectYoutubePackage_matches_case_insensitive_substring() {
        val candidates = listOf(
            "com.example.player",
            "app.free.youtube.tv",
            "com.other"
        )

        assertEquals(
            "app.free.youtube.tv",
            UrlActionResolver.selectYoutubePackage(candidates)
        )
    }

    @Test
    fun selectYoutubePackage_returns_null_when_no_candidate_matches() {
        assertNull(
            UrlActionResolver.selectYoutubePackage(listOf("com.example.browser", "org.web"))
        )
        assertNull(UrlActionResolver.selectYoutubePackage(emptyList()))
    }

    @Test
    fun externalBrowserSteps_pins_default_browser_then_falls_back_to_chooser() {
        val steps = UrlActionResolver.externalBrowserSteps("com.chrome.dev")

        assertEquals(
            listOf(
                UrlActionResolver.ExternalBrowserStep.Pinned("com.chrome.dev"),
                UrlActionResolver.ExternalBrowserStep.SystemChooser
            ),
            steps
        )
    }

    @Test
    fun externalBrowserSteps_without_default_browser_uses_unpinned_view_then_chooser() {
        val steps = UrlActionResolver.externalBrowserSteps(null)

        assertEquals(
            listOf(
                UrlActionResolver.ExternalBrowserStep.Pinned(null),
                UrlActionResolver.ExternalBrowserStep.SystemChooser
            ),
            steps
        )
    }
}
