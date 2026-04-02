package com.milki.launcher.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionPatternMatcherTest {

    @Test
    fun normalizePhone_keepsLeadingPlusAndDigits() {
        val normalized = SuggestionPatternMatcher.normalizePhone(
            rawText = "+1 (555) 123-4567",
            isPhonePatternMatch = { true }
        )
        assertEquals("+15551234567", normalized)
    }

    @Test
    fun normalizePhone_returnsNullForShortInput() {
        val normalized = SuggestionPatternMatcher.normalizePhone(
            rawText = "1234",
            isPhonePatternMatch = { true }
        )
        assertEquals(null, normalized)
    }

    @Test
    fun looksLikeMapLocation_detectsGeoScheme() {
        assertTrue(SuggestionPatternMatcher.looksLikeMapLocation("geo:37.4219983,-122.084"))
    }

    @Test
    fun looksLikeMapLocation_detectsCoordinates() {
        assertTrue(SuggestionPatternMatcher.looksLikeMapLocation("37.4219983,-122.084"))
    }

    @Test
    fun looksLikeMapLocation_detectsAddressHeuristic() {
        assertTrue(SuggestionPatternMatcher.looksLikeMapLocation("1600 Amphitheatre Parkway, Mountain View"))
    }

    @Test
    fun looksLikeMapLocation_rejectsPlainText() {
        assertFalse(SuggestionPatternMatcher.looksLikeMapLocation("launch notes tomorrow"))
    }
}
