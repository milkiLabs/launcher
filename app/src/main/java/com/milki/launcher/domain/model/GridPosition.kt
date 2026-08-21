/**
 * GridPosition.kt - Represents a cell position in the home screen grid
 *
 * This data class is used to track where each pinned item is located on the
 * home screen grid. The grid is a 2D coordinate system where:
 * - row 0 is at the top
 * - column 0 is at the left
 *
 * GRID LAYOUT EXAMPLE:
 * ```
 * +--------+--------+--------+--------+
 * | (0,0)  | (0,1)  | (0,2)  | (0,3)  |  <- Row 0
 * +--------+--------+--------+--------+
 * | (1,0)  | (1,1)  | (1,2)  | (1,3)  |  <- Row 1
 * +--------+--------+--------+--------+
 * | (2,0)  | (2,1)  | (2,2)  | (2,3)  |  <- Row 2
 * +--------+--------+--------+--------+
 *    ^        ^        ^        ^
 *  Col 0   Col 1    Col 2    Col 3
 * ```
 *
 * WHY A SEPARATE CLASS?
 * - Type safety: Prevents mixing up row/column order
 * - Serialization: Single place for format handling
 * - Immutability: Positions are values, not mutable objects
 * - Computed properties: Can add bounds checking, neighbors, etc.
 *
 * SERIALIZATION:
 * Uses kotlinx.serialization for JSON serialization.
 * This provides automatic serialization/deserialization without manual parsing.
 * Example: GridPosition(2, 3) -> {"row":2,"column":3}
 */

package com.milki.launcher.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a cell position in the home screen grid.
 *
 * The grid uses a 2D coordinate system where:
 * - row: Vertical position (0 = top, increasing downward)
 * - column: Horizontal position (0 = left, increasing rightward)
 *
 * COORDINATE SYSTEM:
 * - Origin (0, 0) is at the top-left corner
 * - Positive row values go downward
 * - Positive column values go rightward
 *
 * SERIALIZATION:
 * The @Serializable annotation enables automatic JSON serialization.
 * The position is serialized as: {"row":2,"column":3}
 * This is safer than pipe-delimited formats because there's no risk of delimiter collision.
 *
 * @property row The row index (0-based, top to bottom)
 * @property column The column index (0-based, left to right)
 */
@Serializable
data class GridPosition(
    val row: Int,
    val column: Int
) {
    companion object {
        /**
         * Default position for newly pinned items.
         *
         * When a user pins a new item, it's placed at position (0, 0) by default.
         * The user can then drag it to their desired location.
         *
         * Note: If (0, 0) is occupied, the system will find the next available cell.
         */
        val DEFAULT = GridPosition(0, 0)
    }
}
