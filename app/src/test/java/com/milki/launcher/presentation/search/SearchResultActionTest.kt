package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultActionTest {

    @Test
    fun launch_app_shortcut_closes_search() {
        val shortcut = HomeItem.AppShortcut(
            id = "shortcut:com.example.app/new-chat",
            packageName = "com.example.app",
            shortcutId = "new-chat",
            shortLabel = "New chat",
            longLabel = "Start a new chat"
        )

        val shouldClose = SearchResultAction.LaunchAppShortcut(shortcut).shouldCloseSearch()

        assertTrue(shouldClose)
    }

    @Test
    fun open_app_info_keeps_search_open() {
        val shouldClose = SearchResultAction.OpenAppInfo("com.example.app").shouldCloseSearch()

        assertFalse(shouldClose)
    }
}
