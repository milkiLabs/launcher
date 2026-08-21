package com.milki.launcher.domain.reorder

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridReorderEngineDeterminismTest {

    private val engine = GridReorderEngine()

    @Test
    fun nearest_fit_is_deterministic_for_same_input() {
        val a = HomeItem.PinnedApp(
            id = "a",
            packageName = "pkg.a",
            activityName = "Main",
            label = "A",
            position = GridPosition(0, 0)
        )
        val b = HomeItem.PinnedApp(
            id = "b",
            packageName = "pkg.b",
            activityName = "Main",
            label = "B",
            position = GridPosition(0, 1)
        )

        val input = ReorderInput(
            items = listOf(a, b),
            preferredCell = GridPosition(0, 0),
            draggedSpan = GridSpan.SINGLE,
            gridColumns = 4,
            gridRows = 4
        )

        val first = engine.compute(input)
        val second = engine.compute(input)

        assertEquals(first, second)
        assertEquals(GridPosition(1, 0), first)
    }

    @Test
    fun nearest_fit_falls_back_when_preferred_cell_is_occupied() {
        val blocker = HomeItem.PinnedApp(
            id = "blocker",
            packageName = "pkg.blocker",
            activityName = "Main",
            label = "Blocker",
            position = GridPosition(1, 1)
        )

        val input = ReorderInput(
            items = listOf(blocker),
            preferredCell = GridPosition(1, 1),
            draggedSpan = GridSpan.SINGLE,
            gridColumns = 4,
            gridRows = 4
        )

        val anchor = engine.compute(input)

        assertEquals(GridPosition(0, 1), anchor)
    }

    @Test
    fun compute_returns_null_when_grid_has_no_space() {
        val input = ReorderInput(
            items = listOf(
                pinnedApp("a", GridPosition(0, 0)),
                pinnedApp("b", GridPosition(0, 1)),
                pinnedApp("c", GridPosition(1, 0)),
                pinnedApp("d", GridPosition(1, 1))
            ),
            preferredCell = GridPosition(0, 0),
            draggedSpan = GridSpan.SINGLE,
            gridColumns = 2,
            gridRows = 2
        )

        assertNull(engine.compute(input))
    }

    @Test
    fun multi_cell_span_resolves_nearest_fitting_anchor() {
        val blocker = HomeItem.PinnedApp(
            id = "blocker",
            packageName = "pkg.blocker",
            activityName = "Main",
            label = "Blocker",
            position = GridPosition(0, 0)
        )

        val anchor = engine.compute(
            ReorderInput(
                items = listOf(blocker),
                preferredCell = GridPosition(0, 0),
                draggedSpan = GridSpan(columns = 2, rows = 2),
                gridColumns = 4,
                gridRows = 4
            )
        )

        assertEquals(GridPosition(0, 1), anchor)
    }

    private fun pinnedApp(
        id: String,
        position: GridPosition
    ): HomeItem.PinnedApp {
        return HomeItem.PinnedApp(
            id = id,
            packageName = "pkg.$id",
            activityName = "Main",
            label = id,
            position = position
        )
    }
}
