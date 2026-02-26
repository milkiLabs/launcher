/**
 * GridCalculator.kt - Utility class for grid position calculations
 *
 * This file provides stateless utility functions for converting between
 * screen coordinates (pixels) and grid coordinates (row, column).
 * Separating these calculations enables:
 * - Reusability: Same calculations used across components
 * - Testability: Pure functions are easy to unit test
 * - Consistency: Single source of truth for coordinate conversion
 *
 * COORDINATE SYSTEMS:
 * 1. Screen coordinates: Pixels from top-left of the grid
 * 2. Grid coordinates: Row and column indices
 *
 * CONVERSIONS PROVIDED:
 * - Pixel position -> Grid cell
 * - Grid cell -> Pixel position
 * - Offset from start -> Target cell
 * - Cell bounds checking
 *
 * USAGE:
 * ```kotlin
 * val calculator = GridCalculator(cellWidthPx = 200f, cellHeightPx = 200f)
 * 
 * // Convert pixel position to grid cell
 * val cell = calculator.pixelToCell(Offset(450f, 250f))
 * // Result: GridCell(row=1, column=2)
 * ```
 */

package com.milki.launcher.ui.components.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.milki.launcher.domain.model.GridPosition
import kotlin.math.roundToInt

/**
 * Represents a grid cell with its pixel bounds.
 *
 * Used for hit-testing and rendering cell-specific UI elements.
 *
 * @property position The grid position (row, column)
 * @property x The left edge in pixels
 * @property y The top edge in pixels
 * @property width The cell width in pixels
 * @property height The cell height in pixels
 */
data class GridCellBounds(
    val position: GridPosition,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    /**
     * The right edge of the cell in pixels.
     */
    val right: Float
        get() = x + width
    
    /**
     * The bottom edge of the cell in pixels.
     */
    val bottom: Float
        get() = y + height
    
    /**
     * The center point of the cell in pixels.
     */
    val center: Offset
        get() = Offset(x + width / 2, y + height / 2)
    
    /**
     * Checks if a point is inside this cell.
     *
     * @param point The point to test
     * @return true if the point is within this cell's bounds
     */
    fun contains(point: Offset): Boolean {
        return point.x >= x && point.x < right &&
               point.y >= y && point.y < bottom
    }
}

/**
 * Utility class for grid coordinate calculations.
 *
 * This class is immutable and holds only the cell dimensions.
 * All methods are pure functions that don't modify state.
 *
 * THREAD SAFETY:
 * This class is thread-safe as it has no mutable state.
 *
 * @property cellWidthPx Width of each grid cell in pixels
 * @property cellHeightPx Height of each grid cell in pixels
 * @property columns Number of columns in the grid
 * @property rows Number of rows in the grid (used for bounds checking)
 */
data class GridCalculator(
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val columns: Int = 4,
    val rows: Int = 100
) {
    /**
     * Alternative constructor taking IntSize for cell dimensions.
     */
    constructor(
        cellSize: IntSize,
        columns: Int = 4,
        rows: Int = 100
    ) : this(
        cellWidthPx = cellSize.width.toFloat(),
        cellHeightPx = cellSize.height.toFloat(),
        columns = columns,
        rows = rows
    )
    
    /**
     * Converts a pixel position to a grid cell position.
     *
     * The position is clamped to valid grid bounds.
     *
     * @param pixelPosition The position in pixels from grid origin
     * @return The corresponding grid position
     */
    fun pixelToCell(pixelPosition: Offset): GridPosition {
        val column = (pixelPosition.x / cellWidthPx).toInt().coerceIn(0, columns - 1)
        val row = (pixelPosition.y / cellHeightPx).toInt().coerceIn(0, rows - 1)
        return GridPosition(row, column)
    }
    
    /**
     * Converts a grid position to its pixel position (top-left corner).
     *
     * @param position The grid position
     * @return The pixel offset from grid origin to cell's top-left
     */
    fun cellToPixel(position: GridPosition): Offset {
        return Offset(
            x = position.column * cellWidthPx,
            y = position.row * cellHeightPx
        )
    }
    
    /**
     * Converts a grid position to its center pixel position.
     *
     * Useful for positioning items at cell centers.
     *
     * @param position The grid position
     * @return The pixel offset to cell center
     */
    fun cellCenter(position: GridPosition): Offset {
        return Offset(
            x = position.column * cellWidthPx + cellWidthPx / 2,
            y = position.row * cellHeightPx + cellHeightPx / 2
        )
    }
    
    /**
     * Calculates a target grid position from a start position and pixel offset.
     *
     * This is the main function used during drag operations to determine
     * where an item will be dropped based on how far it was dragged.
     *
     * @param startPosition The grid position where drag started
     * @param offset The pixel offset from the start position
     * @return The target grid position (clamped to bounds)
     */
    fun calculateTargetPosition(
        startPosition: GridPosition,
        offset: Offset
    ): GridPosition {
        val targetColumn = startPosition.column + (offset.x / cellWidthPx).roundToInt()
        val targetRow = startPosition.row + (offset.y / cellHeightPx).roundToInt()
        
        return GridPosition(
            row = targetRow.coerceIn(0, rows - 1),
            column = targetColumn.coerceIn(0, columns - 1)
        )
    }
    
    /**
     * Gets the pixel bounds for a specific grid cell.
     *
     * Useful for hit-testing and rendering cell-specific UI.
     *
     * @param position The grid position
     * @return The pixel bounds of the cell
     */
    fun getCellBounds(position: GridPosition): GridCellBounds {
        return GridCellBounds(
            position = position,
            x = position.column * cellWidthPx,
            y = position.row * cellHeightPx,
            width = cellWidthPx,
            height = cellHeightPx
        )
    }
    
    /**
     * Checks if a grid position is within valid bounds.
     *
     * @param position The position to check
     * @return true if the position is within grid bounds
     */
    fun isValidPosition(position: GridPosition): Boolean {
        return position.row in 0 until rows && 
               position.column in 0 until columns
    }
    
    /**
     * Clamps a grid position to valid bounds.
     *
     * @param position The position to clamp
     * @return The clamped position
     */
    fun clampPosition(position: GridPosition): GridPosition {
        return GridPosition(
            row = position.row.coerceIn(0, rows - 1),
            column = position.column.coerceIn(0, columns - 1)
        )
    }
    
    /**
     * Calculates the pixel offset between two grid positions.
     *
     * Useful for calculating how far an item has moved between cells.
     *
     * @param from The starting grid position
     * @param to The ending grid position
     * @return The pixel offset between the positions
     */
    fun offsetBetween(from: GridPosition, to: GridPosition): Offset {
        return Offset(
            x = (to.column - from.column) * cellWidthPx,
            y = (to.row - from.row) * cellHeightPx
        )
    }
    
    /**
     * Returns the total grid width in pixels.
     */
    val totalWidthPx: Float
        get() = columns * cellWidthPx
    
    /**
     * Returns the total grid height in pixels.
     */
    val totalHeightPx: Float
        get() = rows * cellHeightPx
    
    companion object {
        /**
         * Creates a GridCalculator from cell size in pixels.
         */
        fun fromCellSize(
            cellWidthPx: Float,
            cellHeightPx: Float,
            columns: Int = 4,
            rows: Int = 100
        ): GridCalculator {
            return GridCalculator(cellWidthPx, cellHeightPx, columns, rows)
        }
    }
}
