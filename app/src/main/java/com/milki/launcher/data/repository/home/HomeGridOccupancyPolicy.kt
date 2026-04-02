package com.milki.launcher.data.repository.home

import com.milki.launcher.domain.homegraph.HomeGridDefaults
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Shared span-aware occupancy policy for home-grid placement decisions.
 */
internal class HomeGridOccupancyPolicy(
    private val defaultGridColumns: Int = HomeGridDefaults.COLUMNS
) {

    /**
     * Returns every occupied cell and the owning top-level item id.
     */
    fun buildOccupiedCells(
        items: List<HomeItem>,
        excludeItemId: String? = null
    ): Map<GridPosition, String> {
        val occupied = mutableMapOf<GridPosition, String>()

        for (item in items) {
            if (item.id == excludeItemId) continue

            if (item is HomeItem.WidgetItem) {
                for (position in item.span.occupiedPositions(item.position)) {
                    occupied[position] = item.id
                }
            } else {
                occupied[item.position] = item.id
            }
        }

        return occupied
    }

    /**
     * Returns true when all cells for [span] at [position] are free and in bounds.
     */
    fun isSpanFree(
        position: GridPosition,
        span: GridSpan,
        occupiedCells: Map<GridPosition, String>,
        gridColumns: Int = defaultGridColumns
    ): Boolean {
        if (position.row < 0 || position.column < 0) return false
        if (position.column + span.columns > gridColumns) return false

        for (cell in span.occupiedPositions(position)) {
            if (cell in occupiedCells) {
                return false
            }
        }

        return true
    }

    /**
     * Finds the first free single-cell slot scanning row-major order.
     */
    fun findFirstAvailableSingleCell(
        items: List<HomeItem>,
        columns: Int = defaultGridColumns,
        maxRows: Int = 100
    ): GridPosition {
        val occupied = buildOccupiedCells(items).keys

        for (row in 0 until maxRows) {
            for (column in 0 until columns) {
                val candidate = GridPosition(row, column)
                if (candidate !in occupied) {
                    return candidate
                }
            }
        }

        return GridPosition(maxRows, 0)
    }
}
