package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.GridPosition
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
}
