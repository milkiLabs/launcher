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

    @Test
    fun typed_phone_number_call_requires_call_permission() {
        val permission = SearchResultAction.DialPhoneNumber("123456").requiredPermission()

        assertTrue(permission == android.Manifest.permission.CALL_PHONE)
    }

    @Test
    fun save_phone_number_closes_search_without_runtime_permission() {
        val action = SearchResultAction.SavePhoneNumber("123456")

        assertTrue(action.shouldCloseSearch())
        assertTrue(action.requiredPermission() == null)
    }
}
