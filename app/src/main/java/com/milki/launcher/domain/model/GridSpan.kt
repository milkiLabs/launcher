/**
 * GridSpan.kt - Represents the size of a multi-cell item on the home screen grid
 *
 * This data class is used to track how many grid cells a widget occupies,
 * both horizontally (columns) and vertically (rows).
 *
 * WHY A SEPARATE CLASS?
 * - Type safety: Prevents mixing up columns/rows order
 * - Serialization: Single place for format handling
 * - Utility methods: Can compute which cells are occupied by a multi-cell item
 *
 * GRID SPAN EXAMPLE (2 columns × 3 rows starting at position (1, 1)):
 * ```
 * +--------+--------+--------+--------+
 * | (0,0)  | (0,1)  | (0,2)  | (0,3)  |
 * +--------+--------+--------+--------+
 * | (1,0)  | ██████████████ | (1,3)  |  <- Widget starts here at (1,1)
 * +--------+ ██  2×3 Widget ██        |
 * | (2,0)  | ██████████████ | (2,3)  |
 * +--------+ ██████████████ +---------+
 * | (3,0)  | ██████████████ | (3,3)  |
 * +--------+--------+--------+--------+
 * ```
 *
 * SERIALIZATION:
 * Uses kotlinx.serialization for JSON serialization.
 * Example: GridSpan(2, 3) -> {"columns":2,"rows":3}
 *
 * @property columns How many grid columns this item spans (horizontal size). Minimum is 1.
 * @property rows How many grid rows this item spans (vertical size). Minimum is 1.
 */

package com.milki.launcher.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GridSpan(
    val columns: Int = 1,
    val rows: Int = 1
) {
    /**
     * Computes all grid positions that this span occupies when anchored at the given origin.
     *
     * The origin is the top-left cell of the multi-cell item. This method returns
     * every cell covered by the item, including the origin itself.
     *
     * EXAMPLE:
     * For a 2×2 span anchored at (1, 1), this returns:
     * [(1,1), (1,2), (2,1), (2,2)]
     *
     * @param origin The top-left GridPosition where this span is anchored.
     * @return A set of all GridPositions occupied by this span.
     */
    fun occupiedPositions(origin: GridPosition): Set<GridPosition> {
        val positions = mutableSetOf<GridPosition>()
        for (row in origin.row until origin.row + rows) {
            for (col in origin.column until origin.column + columns) {
                positions.add(GridPosition(row, col))
            }
        }
        return positions
    }

    companion object {
        /**
         * Default span for single-cell items (1×1).
         *
         * All existing HomeItem types (PinnedApp, PinnedFile, etc.) occupy
         * exactly one cell, so their span is always 1×1.
         */
        val SINGLE = GridSpan(1, 1)
    }
}
