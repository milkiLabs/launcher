package com.milki.launcher.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.reorder.GridReorderEngine
import com.milki.launcher.domain.reorder.ReorderInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GridReorderBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val engine = GridReorderEngine()

    private val denseGrid = ReorderInput(
        items = filledGrid(columns = 6, rows = 10, skipRows = 8..9),
        preferredCell = GridPosition(0, 0),
        draggedSpan = GridSpan(1, 1),
        gridColumns = 6,
        gridRows = 10,
        excludeItemId = "app-0",
    )

    private val fullGridNoFreeSpace = ReorderInput(
        items = filledGrid(columns = 6, rows = 10, skipRows = emptyRange()),
        preferredCell = GridPosition(5, 9),
        draggedSpan = GridSpan(2, 2),
        gridColumns = 6,
        gridRows = 10,
        excludeItemId = "app-59",
    )

    @Test
    fun findAnchorInDenseGrid() {
        benchmarkRule.measureRepeated {
            engine.compute(denseGrid)
        }
    }

    @Test
    fun exhaustFullGridWithoutPlacement() {
        benchmarkRule.measureRepeated {
            engine.compute(fullGridNoFreeSpace)
        }
    }

    private fun filledGrid(columns: Int, rows: Int, skipRows: IntRange): List<HomeItem> {
        val items = ArrayList<HomeItem>(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (row in skipRows) continue
                val index = row * columns + column
                items.add(
                    HomeItem.PinnedApp(
                        id = "app-$index",
                        packageName = "com.benchmark.app$index",
                        activityName = "com.benchmark.app$index/.Main",
                        label = "App $index",
                        position = GridPosition(row, column),
                    ),
                )
            }
        }
        return items
    }

    private fun emptyRange(): IntRange = IntRange.EMPTY
}
