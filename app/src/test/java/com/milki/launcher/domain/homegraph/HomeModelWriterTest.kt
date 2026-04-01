package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelWriterTest {

    private val writer = HomeModelWriter(gridColumns = 4)

    @Test
    fun move_top_level_item_updates_position_when_target_free() {
        val item = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition(0, 0))

        val result = writer.apply(
            currentItems = listOf(item),
            command = HomeModelWriter.Command.MoveTopLevelItem(
                itemId = item.id,
                newPosition = GridPosition(1, 1)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val applied = result as HomeModelWriter.Result.Applied
        assertEquals(GridPosition(1, 1), applied.items.first().position)
    }

    @Test
    fun move_top_level_item_rejects_when_target_occupied() {
        val a = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition(0, 0))
        val b = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main", null)
        ).withPosition(GridPosition(1, 1))

        val result = writer.apply(
            currentItems = listOf(a, b),
            command = HomeModelWriter.Command.MoveTopLevelItem(
                itemId = a.id,
                newPosition = GridPosition(1, 1)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Rejected)
    }

    @Test
    fun pin_or_move_evicts_from_folder_and_places_on_grid() {
        val child = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val folder = HomeItem.FolderItem.create(
            item1 = child,
            item2 = HomeItem.PinnedApp.fromAppInfo(
                AppInfo("B", "pkg.b", "Main", null)
            ),
            atPosition = GridPosition(0, 0)
        )

        val result = writer.apply(
            currentItems = listOf(folder),
            command = HomeModelWriter.Command.PinOrMoveToPosition(
                item = child,
                targetPosition = GridPosition(2, 2)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val applied = result as HomeModelWriter.Result.Applied
        val placed = applied.items.firstOrNull { it.id == child.id }
        assertEquals(GridPosition(2, 2), placed?.position)
    }

    @Test
    fun remove_item_by_id_removes_nested_folder_child_and_promotes_last_child() {
        val staleChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val healthyChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val folderPosition = GridPosition(0, 2)
        val folder = HomeItem.FolderItem.create(
            item1 = staleChild,
            item2 = healthyChild,
            atPosition = folderPosition
        )

        val result = writer.apply(
            currentItems = listOf(folder),
            command = HomeModelWriter.Command.RemoveItemById(itemId = staleChild.id)
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val applied = (result as HomeModelWriter.Result.Applied).items
        assertTrue(applied.none { it.id == staleChild.id })

        val promoted = applied.firstOrNull { it.id == healthyChild.id }
        assertTrue(promoted != null)
        assertEquals(folderPosition, promoted?.position)
    }

    @Test
    fun update_widget_frame_applies_position_and_span_together() {
        val widget = HomeItem.WidgetItem.create(
            appWidgetId = 7,
            providerPackage = "pkg.widget",
            providerClass = "WidgetProvider",
            label = "Widget",
            position = GridPosition(0, 0),
            span = GridSpan(columns = 2, rows = 2)
        )

        val result = writer.apply(
            currentItems = listOf(widget),
            command = HomeModelWriter.Command.UpdateWidgetFrame(
                widgetId = widget.id,
                newPosition = GridPosition(1, 1),
                newSpan = GridSpan(columns = 3, rows = 1)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val updated = (result as HomeModelWriter.Result.Applied).items.first() as HomeItem.WidgetItem
        assertEquals(GridPosition(1, 1), updated.position)
        assertEquals(GridSpan(columns = 3, rows = 1), updated.span)
    }

    @Test
    fun update_widget_frame_rejects_when_new_frame_collides() {
        val widget = HomeItem.WidgetItem.create(
            appWidgetId = 8,
            providerPackage = "pkg.widget",
            providerClass = "WidgetProvider",
            label = "Widget",
            position = GridPosition(0, 0),
            span = GridSpan(columns = 2, rows = 2)
        )
        val blocker = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main", null)
        ).withPosition(GridPosition(1, 2))

        val result = writer.apply(
            currentItems = listOf(widget, blocker),
            command = HomeModelWriter.Command.UpdateWidgetFrame(
                widgetId = widget.id,
                newPosition = GridPosition(0, 1),
                newSpan = GridSpan(columns = 3, rows = 2)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Rejected)
    }

    @Test
    fun remove_items_by_id_removes_top_level_and_folder_children() {
        val staleTopLevel = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition(0, 0))

        val staleChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val healthyChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("C", "pkg.c", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val folder = HomeItem.FolderItem.create(
            item1 = staleChild,
            item2 = healthyChild,
            atPosition = GridPosition(1, 1)
        )

        val result = writer.apply(
            currentItems = listOf(staleTopLevel, folder),
            command = HomeModelWriter.Command.RemoveItemsById(
                itemIds = setOf(staleTopLevel.id, staleChild.id)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val appliedItems = (result as HomeModelWriter.Result.Applied).items

        assertTrue(appliedItems.none { it.id == staleTopLevel.id })

        val promoted = appliedItems.firstOrNull { it.id == healthyChild.id }
        assertTrue(promoted != null)
        assertEquals(GridPosition(1, 1), promoted?.position)
    }

    @Test
    fun remove_items_by_id_rejects_when_nothing_removed() {
        val healthy = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition(0, 0))

        val result = writer.apply(
            currentItems = listOf(healthy),
            command = HomeModelWriter.Command.RemoveItemsById(
                itemIds = setOf("app:pkg.missing/Main")
            )
        )

        assertTrue(result is HomeModelWriter.Result.Rejected)
    }

    @Test
    fun remove_items_by_id_deletes_folder_when_all_children_removed() {
        val staleChildA = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("A", "pkg.a", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val staleChildB = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main", null)
        ).withPosition(GridPosition.DEFAULT)

        val healthyTopLevel = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("C", "pkg.c", "Main", null)
        ).withPosition(GridPosition(0, 0))

        val folder = HomeItem.FolderItem.create(
            item1 = staleChildA,
            item2 = staleChildB,
            atPosition = GridPosition(1, 1)
        )

        val result = writer.apply(
            currentItems = listOf(healthyTopLevel, folder),
            command = HomeModelWriter.Command.RemoveItemsById(
                itemIds = setOf(staleChildA.id, staleChildB.id)
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val appliedItems = (result as HomeModelWriter.Result.Applied).items
        assertTrue(appliedItems.none { it is HomeItem.FolderItem && it.id == folder.id })
        assertTrue(appliedItems.none { it.id == staleChildA.id || it.id == staleChildB.id })
        assertTrue(appliedItems.any { it.id == healthyTopLevel.id })
    }
}
