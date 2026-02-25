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
 * Positions are serialized as "row,column" strings for storage in DataStore.
 * Example: GridPosition(2, 3) -> "2,3"
 */

package com.milki.launcher.domain.model

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
 * @property row The row index (0-based, top to bottom)
 * @property column The column index (0-based, left to right)
 */
data class GridPosition(
    val row: Int,
    val column: Int
) {
    /**
     * Converts the position to a storage-friendly string format.
     *
     * FORMAT: "row,column"
     * Example: GridPosition(2, 3) -> "2,3"
     *
     * This format is used when storing the position in DataStore alongside
     * the HomeItem data.
     *
     * @return String representation of this position
     */
    fun toStorageString(): String = "$row,$column"

    companion object {
        /**
         * Parses a storage string back into a GridPosition.
         *
         * EXPECTED FORMAT: "row,column"
         * Example: "2,3" -> GridPosition(2, 3)
         *
         * ERROR HANDLING:
         * Returns null if the string is malformed or cannot be parsed.
         * This prevents crashes when loading corrupted data.
         *
         * @param str The storage string to parse
         * @return GridPosition if parsing succeeds, null otherwise
         */
        fun fromStorageString(str: String): GridPosition? {
            // Split the string by comma
            val parts = str.split(",")

            // We expect exactly 2 parts: row and column
            if (parts.size != 2) return null

            // Try to parse each part as an integer
            val row = parts[0].toIntOrNull() ?: return null
            val column = parts[1].toIntOrNull() ?: return null

            return GridPosition(row, column)
        }

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
