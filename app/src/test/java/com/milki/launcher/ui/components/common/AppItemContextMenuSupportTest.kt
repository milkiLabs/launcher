package com.milki.launcher.ui.components.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.launcher.MenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppItemContextMenuSupportTest {

    @Test
    fun pinned_app_menu_includes_app_info_and_unpin() {
        val pinnedApp = HomeItem.PinnedApp(
            id = "app:com.example.app",
            packageName = "com.example.app",
            label = "Example",
            activityName = ""
        )

        val menuActions = buildHomeItemMenuActionsTest(pinnedApp)

        assertTrue(menuActions.any { it.label == "App info" })
        assertTrue(menuActions.any { it.label == "Unpin from home" })
    }

    @Test
    fun app_shortcut_menu_only_includes_unpin() {
        val shortcut = HomeItem.AppShortcut(
            id = "shortcut:com.example.app/new-chat",
            packageName = "com.example.app",
            shortcutId = "new-chat",
            shortLabel = "New chat",
            longLabel = "Start a new chat"
        )

        val menuActions = buildHomeItemMenuActionsTest(shortcut)

        assertEquals(1, menuActions.size)
        assertEquals("Unpin from home", menuActions[0].label)
    }

    @Test
    fun action_shortcut_menu_only_includes_unpin() {
        val shortcut = HomeItem.ActionShortcut(
            id = "action:com.example.app/settings",
            packageName = "com.example.app",
            label = "Settings",
            destinationUri = "android.settings.APPLICATION_SETTINGS"
        )

        val menuActions = buildHomeItemMenuActionsTest(shortcut)

        assertEquals(1, menuActions.size)
        assertEquals("Unpin from home", menuActions[0].label)
    }

    @Test
    fun folder_item_menu_only_includes_unpin() {
        val folder = HomeItem.FolderItem(
            id = "folder:my-folder",
            name = "My Folder",
            children = emptyList()
        )

        val menuActions = buildHomeItemMenuActionsTest(folder)

        assertEquals(1, menuActions.size)
        assertEquals("Unpin from home", menuActions[0].label)
    }

    @Test
    fun extra_actions_are_appended() {
        val pinnedApp = HomeItem.PinnedApp(
            id = "app:com.example.app",
            packageName = "com.example.app",
            label = "Example",
            activityName = ""
        )
        val extraAction = MenuAction(
            label = "Custom action",
            icon = Icons.Default.Delete,
            onClick = {}
        )

        val menuActions = buildHomeItemMenuActionsTest(pinnedApp, listOf(extraAction))

        assertTrue(menuActions.any { it.label == "Custom action" })
        assertEquals("Custom action", menuActions.last().label)
    }

    @Composable
    private fun buildHomeItemMenuActionsTest(
        item: HomeItem,
        extraActions: List<MenuAction> = emptyList()
    ): List<String> {
        return buildHomeItemMenuActions(item, extraActions).map { it.label }
    }
}
