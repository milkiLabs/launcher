package com.milki.launcher.ui.components.common

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.search.SearchResultAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppItemContextMenuSupportTest {

    @Test
    fun app_menu_includes_quick_actions_before_app_info() {
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

        val menuActions = buildAppUtilityMenuActions(
            packageName = appInfo.packageName,
            appName = appInfo.name,
            quickActions = listOf(quickAction),
            actionHandler = {}
        )

        assertEquals(2, menuActions.size)
        assertEquals("New chat", menuActions[0].label)
        assertEquals(quickAction, menuActions[0].shortcutIcon)
        assertEquals("App info", menuActions[1].label)
    }

    @Test
    fun app_menu_without_quick_actions_keeps_app_info_only() {
        val appInfo = AppInfo(
            name = "Example",
            packageName = "com.example.app",
            activityName = "com.example.app.Main"
        )

        val menuActions = buildAppUtilityMenuActions(
            packageName = appInfo.packageName,
            appName = appInfo.name,
            actionHandler = {}
        )

        assertEquals(1, menuActions.size)
        assertEquals("App info", menuActions[0].label)
    }
}
