package com.milki.launcher.ui.components.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import com.milki.launcher.domain.drag.drop.RejectReason
import com.milki.launcher.domain.drag.reorder.ReorderMode
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalHomeDropActionTest {

    @Test
    fun resolveExternalDropAction_appOntoFolder_returnsAddToFolder() {
        val folder = HomeItem.FolderItem(
            id = "folder:1",
            children = listOf(pinnedApp("app:child")),
            position = GridPosition(row = 1, column = 1)
        )

        val action = resolveExternalDropAction(
            item = externalApp("Dragged"),
            dropPosition = folder.position,
            items = listOf(folder),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Commit
        )

        assertTrue(action is ExternalDropAction.AddToFolder)
        assertEquals("folder:1", (action as ExternalDropAction.AddToFolder).folderId)
    }

    @Test
    fun resolveExternalDropAction_sameFolderChildTarget_rejects() {
        val folder = HomeItem.FolderItem(
            id = "folder:1",
            children = listOf(pinnedApp("app:child")),
            position = GridPosition(row = 0, column = 0)
        )
        val draggedChild = ExternalDragItem.FolderChild(
            folderId = "folder:1",
            childItem = pinnedApp("app:dragged")
        )

        val action = resolveExternalDropAction(
            item = draggedChild,
            dropPosition = folder.position,
            items = listOf(folder),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Commit
        )

        assertTrue(action is ExternalDropAction.Reject)
        assertEquals(
            RejectReason.INVALID_FOLDER_ROUTE,
            (action as ExternalDropAction.Reject).reason
        )
    }

    @Test
    fun applyExternalDropAction_createFolder_invokesHandlerAndConfirm() {
        val dragged = pinnedApp("app:dragged")
        val occupant = pinnedApp("app:occupant", position = GridPosition(row = 2, column = 2))
        var createdWith: Triple<HomeItem, HomeItem, GridPosition>? = null
        var confirmed = false

        val handled = applyExternalDropAction(
            action = ExternalDropAction.CreateFolder(
                draggedItem = dragged,
                occupantItem = occupant,
                position = occupant.position,
                previewState = ExternalDropPreviewState(
                    targetPosition = occupant.position,
                    dragSpan = GridSpan.SINGLE,
                    highlightKind = ExternalDropHighlightKind.Secondary
                )
            ),
            handlers = externalHandlers(
                onCreateFolder = { item1, item2, position ->
                    createdWith = Triple(item1, item2, position)
                },
                onConfirmDrop = { confirmed = true }
            )
        )

        assertTrue(handled)
        assertEquals(Triple(dragged, occupant, occupant.position), createdWith)
        assertTrue(confirmed)
    }

    @Test
    fun resolveExternalDropAction_widgetUsesResolvedPlacement() {
        val providerInfo = AppWidgetProviderInfo().apply {
            provider = ComponentName("com.example.widgets", "ExampleWidgetProvider")
        }
        val action = resolveExternalDropAction(
            item = ExternalDragItem.Widget(
                providerInfo = providerInfo,
                providerComponent = providerInfo.provider,
                span = GridSpan.SINGLE
            ),
            dropPosition = GridPosition(row = 0, column = 0),
            items = listOf(pinnedApp("app:home", position = GridPosition(row = 0, column = 0))),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Commit
        )

        assertTrue(action is ExternalDropAction.PlaceWidget)
        assertEquals(GridPosition(row = 0, column = 1), (action as ExternalDropAction.PlaceWidget).position)
    }

    private fun externalHandlers(
        onCreateFolder: (HomeItem, HomeItem, GridPosition) -> Unit = { _, _, _ -> },
        onConfirmDrop: () -> Unit = {}
    ): ExternalDropHandlers {
        return ExternalDropHandlers(
            onItemDroppedToHome = { _, _ -> },
            onCreateFolder = onCreateFolder,
            onAddItemToFolder = { _, _ -> },
            onFolderItemExtracted = { _, _, _ -> },
            onMoveFolderItemToFolder = { _, _, _ -> },
            onFolderChildDroppedOnItem = { _, _, _, _ -> },
            onWidgetDroppedToHome = { _, _, _ -> },
            onConfirmDrop = onConfirmDrop
        )
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

    private fun externalApp(name: String): ExternalDragItem.App {
        return ExternalDragItem.App(
            appInfo = AppInfo(
                name = name,
                packageName = "com.example.${name.lowercase()}",
                activityName = "MainActivity"
            )
        )
    }
}
