package com.milki.launcher.domain.drag.reorder

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            gridRows = 4,
            mode = ReorderMode.Commit
        )

        val first = engine.compute(input)
        val second = engine.compute(input)

        assertTrue(first.isValid)
        assertEquals(first.anchorCell, second.anchorCell)
        assertEquals(first.strategyId, second.strategyId)
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
            gridRows = 4,
            mode = ReorderMode.Preview
        )

        val plan = engine.compute(input)

        assertTrue(plan.isValid)
        assertEquals(ReorderStrategyId.NEAREST_FIT, plan.strategyId)
        assertEquals(GridPosition(0, 1), plan.anchorCell)
    }
}
