package com.milki.launcher.data.repository.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeItemSerializerTest {

    private val serializer = HomeItemSerializer()

    @Test
    fun missing_preference_returns_default_docs_shortcut() {
        val items = serializer.readFrom(mutablePreferencesOf())

        assertEquals(listOf(HomeItem.ActionShortcut.DefaultDocsShortcut), items)
    }

    @Test
    fun explicitly_empty_home_remains_empty() {
        val preferences = mutablePreferencesOf()
        serializer.writeTo(emptyList(), preferences)

        assertTrue(serializer.readFrom(preferences).isEmpty())
    }

    @Test
    fun shortcut_position_survives_round_trip() {
        val preferences = mutablePreferencesOf()
        val shortcut = HomeItem.ActionShortcut.DefaultDocsShortcut
            .withPosition(GridPosition(row = 3, column = 2))
        serializer.writeTo(listOf(shortcut), preferences)

        assertEquals(listOf(shortcut), serializer.readFrom(preferences))
    }
}
