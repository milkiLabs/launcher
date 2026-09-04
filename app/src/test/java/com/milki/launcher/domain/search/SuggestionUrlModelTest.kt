package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.UrlHandlerApp
import com.milki.launcher.domain.model.UrlSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM checks for the icon-first suggestion model.
 *
 * UrlValidator itself needs Android (Patterns.WEB_URL), so resolution is
 * verified manually / on-device; here we lock the model contract the UI
 * depends on.
 */
class SuggestionUrlModelTest {

    @Test
    fun url_result_carries_both_handler_and_browser() {
        val result = UrlSearchResult(
            url = "https://facebook.com/x",
            displayUrl = "facebook.com/x",
            handlerApp = UrlHandlerApp("com.facebook.katana", "Main", "Facebook"),
            browserApp = UrlHandlerApp("com.android.chrome", "Main", "Chrome")
        )

        assertEquals("Facebook", result.handlerApp?.label)
        assertEquals("Chrome", result.browserApp?.label)
    }

    @Test
    fun browser_null_means_generic_globe_fallback() {
        val result = UrlSearchResult(
            url = "https://example.com/a",
            displayUrl = "example.com/a"
        )

        assertNull(result.handlerApp)
        assertNull(result.browserApp)
    }
}
