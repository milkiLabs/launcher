package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.drag.drop.RejectReason
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalHomeDropActionTest {

    @Test
    fun resolveInternalDropAction_appOntoFolder_returnsAddToFolder() {
        val folder = HomeItem.FolderItem(
            id = "folder:1",
            children = listOf(pinnedApp("app:child")),
            position = GridPosition(row = 1, column = 1)
        )
        val action = resolveInternalDropAction(
            draggedItem = pinnedApp("app:dragged", position = GridPosition(row = 0, column = 0)),
            dropPosition = folder.position,
            items = listOf(folder),
            gridColumns = 4,
            gridRows = 4
        )

        assertTrue(action is InternalDropAction.AddToFolder)
        assertEquals("folder:1", (action as InternalDropAction.AddToFolder).folderId)
    }

    @Test
    fun resolveInternalDropAction_folderOntoApp_rejectsInvalidFolderRoute() {
        val folder = HomeItem.FolderItem(
            id = "folder:dragged",
            children = listOf(pinnedApp("app:child")),
            position = GridPosition(row = 0, column = 0)
        )
        val occupant = pinnedApp("app:occupant", position = GridPosition(row = 1, column = 1))

        val action = resolveInternalDropAction(
            draggedItem = folder,
            dropPosition = occupant.position,
            items = listOf(folder, occupant),
            gridColumns = 4,
            gridRows = 4
        )

        assertTrue(action is InternalDropAction.Reject)
        assertEquals(
            RejectReason.INVALID_FOLDER_ROUTE,
            (action as InternalDropAction.Reject).reason
        )
    }

    @Test
    fun resolveInternalDropAction_widgetUsesNearestValidAnchor() {
        val widget = HomeItem.WidgetItem(
            id = "widget:42",
            appWidgetId = 42,
            providerPackage = "pkg",
            providerClass = "Provider",
            label = "Widget",
            position = GridPosition(row = 2, column = 0),
            span = GridSpan(columns = 2, rows = 2)
        )
        val blocker = pinnedApp("app:blocker", position = GridPosition(row = 0, column = 2))

        val action = resolveInternalDropAction(
            draggedItem = widget,
            dropPosition = GridPosition(row = 0, column = 1),
            items = listOf(widget, blocker),
            gridColumns = 4,
            gridRows = 4
        )

        assertTrue(action is InternalDropAction.MoveItem)
        assertEquals(
            GridPosition(row = 0, column = 0),
            (action as InternalDropAction.MoveItem).position
        )
    }

    @Test
    fun applyInternalDropAction_mergeFolders_invokesHandlerAndConfirm() {
        var merge: Pair<String, String>? = null
        var confirmed = false

        applyInternalDropAction(
            action = InternalDropAction.MergeFolders(
                sourceFolderId = "folder:source",
                targetFolderId = "folder:target"
            ),
            handlers = InternalDropHandlers(
                onItemMove = { _, _ -> },
                onCreateFolder = { _, _, _ -> },
                onAddItemToFolder = { _, _ -> },
                onMergeFolders = { source, target -> merge = source to target },
                onConfirmDrop = { confirmed = true }
            )
        )

        assertEquals("folder:source" to "folder:target", merge)
        assertTrue(confirmed)
    }

    private fun pinnedApp(
        id: String,
        position: GridPosition = GridPosition.DEFAULT
    ): HomeItem.PinnedApp {
        return HomeItem.PinnedApp(
            id = id,
            packageName = "com.example.${id.substringAfter(':')}",
            activityName = "MainActivity",
            label = id,
            position = position
        )
    }
}
