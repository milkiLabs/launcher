package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraggablePinnedItemsGridLookupTest {

    @Test
    fun buildOccupancyLookup_mapsWidgetOccupiedCells() {
        val widget = HomeItem.WidgetItem(
            id = "widget:42",
            appWidgetId = 42,
            providerPackage = "pkg",
            providerClass = "Provider",
            label = "Widget",
            position = GridPosition(row = 1, column = 1),
            span = GridSpan(columns = 2, rows = 2)
        )

        val app = HomeItem.PinnedApp(
            id = "app:a",
            packageName = "com.example",
            activityName = "Main",
            label = "App",
            position = GridPosition(row = 0, column = 0)
        )

        val lookup = buildHomeOccupancyLookup(listOf(app, widget))

        assertEquals(app, lookup[GridPosition(row = 0, column = 0)])
        assertEquals(widget, lookup[GridPosition(row = 1, column = 1)])
        assertEquals(widget, lookup[GridPosition(row = 1, column = 2)])
        assertEquals(widget, lookup[GridPosition(row = 2, column = 1)])
        assertEquals(widget, lookup[GridPosition(row = 2, column = 2)])
        assertNull(lookup[GridPosition(row = 4, column = 4)])
    }

    @Test
    fun toPreviewHomeItem_mapsShortcutPayloadToAppShortcut() {
        val shortcut = HomeItem.AppShortcut(
            id = "shortcut:com.example.chat/new-message",
            packageName = "com.example.chat",
            shortcutId = "new-message",
            shortLabel = "New message",
            longLabel = "Start new message"
        )

        val preview = ExternalDragItem.Shortcut(shortcut).toPreviewHomeItem()

        assertEquals(shortcut, preview)
    }
}
