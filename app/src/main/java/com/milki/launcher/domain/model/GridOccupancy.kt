/**
 * GridOccupancy.kt - Single span-aware occupancy index for the home grid.
 *
 * Replaces the previous four independent "span -> occupied-cells" builders:
 *  - domain/reorder/OccupancyBuilder.kt                (Set<GridPosition>)
 *  - domain/homegraph/HomeGraph.buildOccupiedCells     (Map<GridPosition, String>)
 *  - data/repository/home/GridOccupancyPolicy          (Map<GridPosition, String>)
 *  - ui/components/launcher/HomeOccupancyLookup        (Map<GridPosition, HomeItem>)
 *
 * Build once per item list change and reuse it for binary occupancy checks,
 * occupant lookups, first-free-slot scans, and prebuilt reorder inputs. All
 * span loops below iterate the span ranges in place to avoid per-call set
 * allocations on hot paths.
 *
 * Collision semantics: when two items share a cell (an invalid layout), the
 * first item in the input list wins, matching the drag-preview behavior of
 * the old HomeOccupancyLookup.
 */

package com.milki.launcher.domain.model

class GridOccupancy private constructor(
    val items: List<HomeItem>,
    private val itemById: Map<String, HomeItem>,
    private val cells: Map<GridPosition, String>
) {

    /** Id of the item owning [cell], or null. */
    fun occupantIdAt(cell: GridPosition, excludeItemId: String? = null): String? {
        val id = cells[cell] ?: return null
        return if (excludeItemId != null && id == excludeItemId) null else id
    }

    /** Item owning [cell], or null. */
    fun occupantAt(cell: GridPosition, excludeItemId: String? = null): HomeItem? {
        val id = occupantIdAt(cell, excludeItemId) ?: return null
        return itemById[id]
    }

    /** Every occupied cell, optionally excluding one owner. */
    fun occupiedCells(excludeItemId: String? = null): Set<GridPosition> {
        if (excludeItemId == null) return cells.keys
        return cells.keys.filterTo(mutableSetOf()) { cell -> occupantIdAt(cell, excludeItemId) != null }
    }

    /** True when every cell of [span] anchored at [anchor] is unoccupied by a non-excluded item. */
    fun isSpanFree(anchor: GridPosition, span: GridSpan, excludeItemId: String? = null): Boolean {
        for (row in anchor.row until anchor.row + span.rows) {
            for (column in anchor.column until anchor.column + span.columns) {
                if (occupantIdAt(GridPosition(row, column), excludeItemId) != null) return false
            }
        }
        return true
    }

    /** True when [span] anchored at [anchor] stays fully inside the grid bounds. */
    fun isSpanInBounds(anchor: GridPosition, span: GridSpan, gridColumns: Int, gridRows: Int): Boolean {
        return GridBounds(gridColumns, gridRows).fits(anchor, span)
    }

    /** True when [span] anchored at [anchor] is both in bounds and free. */
    fun canPlace(
        anchor: GridPosition,
        span: GridSpan,
        gridColumns: Int,
        gridRows: Int,
        excludeItemId: String? = null
    ): Boolean {
        return isSpanInBounds(anchor, span, gridColumns, gridRows) &&
            isSpanFree(anchor, span, excludeItemId)
    }

    /** First free single-cell slot scanning row-major order, or one row below the grid. */
    fun firstFreePosition(gridColumns: Int, maxRows: Int): GridPosition {
        for (row in 0 until maxRows) {
            for (column in 0 until gridColumns) {
                val candidate = GridPosition(row, column)
                if (candidate !in cells) return candidate
            }
        }
        return GridPosition(maxRows, 0)
    }

    /**
     * Every non-excluded item whose cells intersect a [span] anchored at [anchor].
     * Used for drop routing where the dragged span may cover multiple cells.
     */
    fun overlappingOccupants(
        anchor: GridPosition,
        span: GridSpan,
        excludeItemId: String? = null
    ): List<HomeItem> {
        val result = mutableListOf<HomeItem>()
        val seen = LinkedHashSet<String>()
        for (row in anchor.row until anchor.row + span.rows) {
            for (column in anchor.column until anchor.column + span.columns) {
                val id = occupantIdAt(GridPosition(row, column), excludeItemId) ?: continue
                if (seen.add(id)) itemById[id]?.let(result::add)
            }
        }
        return result
    }

    companion object {
        /** Builds the occupancy index for [items], optionally skipping one owner entirely. */
        fun fromItems(items: List<HomeItem>, excludeItemId: String? = null): GridOccupancy {
            val itemById = LinkedHashMap<String, HomeItem>(items.size * 2)
            val cells = LinkedHashMap<GridPosition, String>(items.size * 2)

            for (item in items) {
                if (excludeItemId != null && item.id == excludeItemId) continue
                itemById.putIfAbsent(item.id, item)
                val span = item.homeGridSpan
                for (row in item.position.row until item.position.row + span.rows) {
                    for (column in item.position.column until item.position.column + span.columns) {
                        cells.putIfAbsent(GridPosition(row, column), item.id)
                    }
                }
            }
            return GridOccupancy(items = items, itemById = itemById, cells = cells)
        }

        fun empty(): GridOccupancy = fromItems(emptyList())
    }
}