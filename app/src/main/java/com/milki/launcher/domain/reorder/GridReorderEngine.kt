package com.milki.launcher.domain.reorder

import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridPosition
import kotlin.math.abs

/**
 * Resolves the nearest placeable anchor for a dragged span.
 *
 * Candidates are scanned once, row-major over the valid anchor area, ranked by
 * (Chebyshev radius, Manhattan distance) from [ReorderInput.preferredCell].
 * Row-major visitation breaks remaining ties by row then column, so the scan
 * order is a deterministic total order: repeated runs on the same input always
 * resolve the same anchor.
 */
class GridReorderEngine {

    /**
     * Nearest free anchor for [ReorderInput.draggedSpan], or null when no space exists.
     *
     * The dragged item is excluded via [ReorderInput.excludeItemId]; occupancy is built
     * implicitly from [ReorderInput.items].
     */
    fun compute(input: ReorderInput): GridPosition? =
        compute(input, GridOccupancy.fromItems(input.items, excludeItemId = input.excludeItemId))

    /**
     * Nearest free anchor using a prebuilt [occupancy] index.
     *
     * [occupancy] is the sole source of truth: it must already exclude the dragged item.
     * [ReorderInput.items] and [ReorderInput.excludeItemId] are ignored on this path.
     */
    fun compute(input: ReorderInput, occupancy: GridOccupancy): GridPosition? {
        val preferred = input.preferredCell
        val span = input.draggedSpan

        var best: GridPosition? = null
        var bestRadius = Int.MAX_VALUE
        var bestDistance = Int.MAX_VALUE

        for (row in 0..input.gridRows - span.rows) {
            for (column in 0..input.gridColumns - span.columns) {
                val rowDelta = row - preferred.row
                val columnDelta = column - preferred.column
                val radius = maxOf(abs(rowDelta), abs(columnDelta))
                if (radius > bestRadius) continue
                val distance = abs(rowDelta) + abs(columnDelta)
                if (radius == bestRadius && distance >= bestDistance) continue

                if (!occupancy.canPlace(
                        anchor = GridPosition(row, column),
                        span = span,
                        gridColumns = input.gridColumns,
                        gridRows = input.gridRows
                    )
                ) {
                    continue
                }
                best = GridPosition(row, column)
                bestRadius = radius
                bestDistance = distance
            }
        }
        return best
    }
}
