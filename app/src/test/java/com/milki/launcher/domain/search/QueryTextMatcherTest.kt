package com.milki.launcher.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryTextMatcherTest {

    @Test
    fun tokenize_splits_on_non_alphanumeric_runs() {
        assertEquals(listOf("foo", "bar", "baz"), QueryTextMatcher.tokenize("foo-bar baz"))
    }

    @Test
    fun tokenize_returns_empty_for_blank_text() {
        assertEquals(emptyList<String>(), QueryTextMatcher.tokenize("   "))
    }

    @Test
    fun build_acronym_uses_first_letter_of_each_alphanumeric_run() {
        assertEquals("gps", QueryTextMatcher.buildAcronym("Google Play Store"))
        assertEquals("g2f", QueryTextMatcher.buildAcronym("google 2 fast"))
    }

    @Test
    fun build_acronym_of_single_word_is_the_word() {
        assertEquals("notes", QueryTextMatcher.buildAcronym("Notes"))
    }

    @Test
    fun levenshtein_computes_edit_distance() {
        assertEquals(0, QueryTextMatcher.levenshtein("kitten", "kitten"))
        assertEquals(3, QueryTextMatcher.levenshtein("kitten", "sitting"))
        assertEquals(1, QueryTextMatcher.levenshtein("xyz", "xya"))
        assertEquals(4, QueryTextMatcher.levenshtein("", "abcd"))
    }
}
