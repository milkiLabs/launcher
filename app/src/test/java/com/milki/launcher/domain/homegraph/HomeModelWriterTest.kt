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
            AppInfo("A", "pkg.a", "Main")
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
            AppInfo("A", "pkg.a", "Main")
        ).withPosition(GridPosition(0, 0))
        val b = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main")
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
            AppInfo("A", "pkg.a", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val folder = HomeItem.FolderItem.create(
            item1 = child,
            item2 = HomeItem.PinnedApp.fromAppInfo(
                AppInfo("B", "pkg.b", "Main")
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
            AppInfo("A", "pkg.a", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val healthyChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main")
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
            AppInfo("B", "pkg.b", "Main")
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
            AppInfo("A", "pkg.a", "Main")
        ).withPosition(GridPosition(0, 0))

        val staleChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val healthyChild = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("C", "pkg.c", "Main")
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
            AppInfo("A", "pkg.a", "Main")
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
            AppInfo("A", "pkg.a", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val staleChildB = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("B", "pkg.b", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val healthyTopLevel = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("C", "pkg.c", "Main")
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

    @Test
    fun create_folder_rejects_when_target_occupant_is_not_live() {
        val dragged = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Dragged", "pkg.dragged", "Main")
        ).withPosition(GridPosition(0, 0))

        val staleRemovedOccupant = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Removed", "pkg.removed", "Main")
        ).withPosition(GridPosition(0, 1))

        val result = writer.apply(
            currentItems = listOf(dragged),
            command = HomeModelWriter.Command.CreateFolder(
                draggedItem = dragged,
                targetItemId = staleRemovedOccupant.id,
                atPosition = staleRemovedOccupant.position
            )
        )

        assertTrue(result is HomeModelWriter.Result.Rejected)
        val rejected = result as HomeModelWriter.Result.Rejected
        assertEquals(HomeModelWriter.Error.ItemNotFound, rejected.error)
    }

    @Test
    fun create_folder_allows_external_item_when_occupant_is_live() {
        val occupant = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Occupant", "pkg.occupant", "Main")
        ).withPosition(GridPosition(1, 1))

        val externalItem = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("External", "pkg.external", "Main")
        )

        val result = writer.apply(
            currentItems = listOf(occupant),
            command = HomeModelWriter.Command.CreateFolder(
                draggedItem = externalItem,
                targetItemId = occupant.id,
                atPosition = occupant.position
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val applied = (result as HomeModelWriter.Result.Applied).items
        val folder = applied.singleOrNull() as? HomeItem.FolderItem
        assertTrue(folder != null)
        assertEquals(occupant.position, folder?.position)
        assertEquals(
            setOf(externalItem.id, occupant.id),
            folder?.children?.map { it.id }?.toSet()
        )
    }

    @Test
    fun extract_folder_child_onto_item_rejects_when_target_not_live() {
        val child = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Child", "pkg.child", "Main")
        ).withPosition(GridPosition.DEFAULT)
        val sibling = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Sibling", "pkg.sibling", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val sourceFolder = HomeItem.FolderItem.create(
            item1 = child,
            item2 = sibling,
            atPosition = GridPosition(0, 0)
        )
        val staleTarget = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Removed", "pkg.removed", "Main")
        ).withPosition(GridPosition(0, 2))

        val result = writer.apply(
            currentItems = listOf(sourceFolder),
            command = HomeModelWriter.Command.ExtractFolderChildOntoItem(
                sourceFolderId = sourceFolder.id,
                childItemId = child.id,
                targetItemId = staleTarget.id,
                atPosition = staleTarget.position
            )
        )

        assertTrue(result is HomeModelWriter.Result.Rejected)
        val rejected = result as HomeModelWriter.Result.Rejected
        assertEquals(HomeModelWriter.Error.ItemNotFound, rejected.error)
    }

    @Test
    fun extract_folder_child_onto_item_creates_folder_with_live_target() {
        val child = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Child", "pkg.child", "Main")
        ).withPosition(GridPosition.DEFAULT)
        val sibling = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Sibling", "pkg.sibling", "Main")
        ).withPosition(GridPosition.DEFAULT)

        val sourceFolderPosition = GridPosition(0, 0)
        val sourceFolder = HomeItem.FolderItem.create(
            item1 = child,
            item2 = sibling,
            atPosition = sourceFolderPosition
        )
        val target = HomeItem.PinnedApp.fromAppInfo(
            AppInfo("Target", "pkg.target", "Main")
        ).withPosition(GridPosition(0, 2))

        val result = writer.apply(
            currentItems = listOf(sourceFolder, target),
            command = HomeModelWriter.Command.ExtractFolderChildOntoItem(
                sourceFolderId = sourceFolder.id,
                childItemId = child.id,
                targetItemId = target.id,
                atPosition = target.position
            )
        )

        assertTrue(result is HomeModelWriter.Result.Applied)
        val applied = (result as HomeModelWriter.Result.Applied).items

        val promotedSibling = applied.firstOrNull { it.id == sibling.id }
        assertTrue(promotedSibling != null)
        assertEquals(sourceFolderPosition, promotedSibling?.position)

        val createdFolder = applied.firstOrNull { it is HomeItem.FolderItem } as? HomeItem.FolderItem
        assertTrue(createdFolder != null)
        assertEquals(target.position, createdFolder?.position)
        assertEquals(
            setOf(child.id, target.id),
            createdFolder?.children?.map { it.id }?.toSet()
        )
    }
}
