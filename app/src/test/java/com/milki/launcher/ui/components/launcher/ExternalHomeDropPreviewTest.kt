package com.milki.launcher.ui.components.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import com.milki.launcher.domain.drag.reorder.ReorderMode
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExternalHomeDropPreviewTest {

    @Test
    fun appDropOntoRegularHomeIcon_usesSecondaryValidHighlight() {
        val occupant = pinnedApp(id = "app:home", position = GridPosition(row = 1, column = 1))
        val preview = resolveExternalDropAction(
            item = externalApp(name = "Dragged"),
            dropPosition = occupant.position,
            items = listOf(occupant),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Preview
        )?.previewState

        assertNotNull(preview)
        assertEquals(GridPosition(row = 1, column = 1), preview?.targetPosition)
        assertEquals(ExternalDropHighlightKind.Secondary, preview?.highlightKind)
    }

    @Test
    fun appDropOntoFolder_usesSecondaryValidHighlight() {
        val folder = HomeItem.FolderItem(
            id = "folder:1",
            children = listOf(pinnedApp(id = "app:child")),
            position = GridPosition(row = 2, column = 0)
        )
        val preview = resolveExternalDropAction(
            item = externalApp(name = "Dragged"),
            dropPosition = folder.position,
            items = listOf(folder),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Preview
        )?.previewState

        assertNotNull(preview)
        assertEquals(ExternalDropHighlightKind.Secondary, preview?.highlightKind)
    }

    @Test
    fun appDropOntoWidget_usesErrorHighlight() {
        val widget = HomeItem.WidgetItem(
            id = "widget:42",
            appWidgetId = 42,
            providerPackage = "pkg",
            providerClass = "Provider",
            label = "Widget",
            position = GridPosition(row = 0, column = 0),
            span = GridSpan(columns = 2, rows = 2)
        )
        val preview = resolveExternalDropAction(
            item = externalApp(name = "Dragged"),
            dropPosition = GridPosition(row = 1, column = 1),
            items = listOf(widget),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Preview
        )?.previewState

        assertNotNull(preview)
        assertEquals(ExternalDropHighlightKind.Error, preview?.highlightKind)
    }

    @Test
    fun widgetDropCanPreviewNearestOpenCellInsteadOfRedCollision() {
        val occupant = pinnedApp(id = "app:home", position = GridPosition(row = 0, column = 0))
        val providerInfo = AppWidgetProviderInfo().apply {
            provider = ComponentName("com.example.widgets", "ExampleWidgetProvider")
        }
        val preview = resolveExternalDropAction(
            item = ExternalDragItem.Widget(
                providerInfo = providerInfo,
                providerComponent = providerInfo.provider,
                span = GridSpan.SINGLE
            ),
            dropPosition = GridPosition(row = 0, column = 0),
            items = listOf(occupant),
            gridColumns = 4,
            maxVisibleRows = 4,
            reorderMode = ReorderMode.Preview
        )?.previewState

        assertNotNull(preview)
        assertEquals(GridPosition(row = 0, column = 1), preview?.targetPosition)
        assertEquals(ExternalDropHighlightKind.Primary, preview?.highlightKind)
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
