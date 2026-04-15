package com.milki.launcher.ui.components.common

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.search.SearchResultAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppItemContextMenuSupportTest {

    @Test
    fun app_menu_includes_quick_actions_before_pin_and_info() {
        val appInfo = AppInfo(
            name = "Example",
            packageName = "com.example.app",
            activityName = "com.example.app.Main"
        )
        val quickAction = HomeItem.AppShortcut(
            id = "shortcut:com.example.app/new-chat",
            packageName = "com.example.app",
            shortcutId = "new-chat",
            shortLabel = "New chat",
            longLabel = "Start a new chat"
        )

        val menuActions = buildAppItemMenuActions(
            appInfo = appInfo,
            quickActions = listOf(quickAction)
        )

        assertEquals(3, menuActions.size)
        assertEquals("New chat", menuActions[0].label)
        assertTrue(menuActions[0].action is SearchResultAction.LaunchAppShortcut)
        assertEquals(quickAction, menuActions[0].shortcutIcon)
        assertEquals("Pin to home", menuActions[1].label)
        assertEquals("App info", menuActions[2].label)
    }

    @Test
    fun app_menu_without_quick_actions_keeps_default_actions() {
        val appInfo = AppInfo(
            name = "Example",
            packageName = "com.example.app",
            activityName = "com.example.app.Main"
        )

        val menuActions = buildAppItemMenuActions(appInfo = appInfo)

        assertEquals(2, menuActions.size)
        assertEquals("Pin to home", menuActions[0].label)
        assertEquals("App info", menuActions[1].label)
    }
}
