/**
 * GridBounds.kt - Pure span/bound math for the home grid.
 *
 * Consolidates the three previous bound-checking variants:
 *  - GridOccupancy.isSpanInBounds            (full span fit, rows bounded)
 *  - com.milki.launcher.domain.homegraph isWithinGrid (columns only, rows unbounded)
 *  - GridCalculator.isSpanInBounds           (full span fit) — dead code, removed
 *
 * and the three previous span clamps (clampSpanOrigin / clampWidgetDropPosition /
 * WidgetOverlayLayer preview). Rows being null means the grid grows infinitely
 * downward (the serialized home layout), which is the one place that differs.
 */

package com.milki.launcher.domain.model

data class GridBounds(
    val columns: Int,
    val rows: Int? = null
) {
    /** True when [span] anchored at [anchor] stays fully inside the bounds. */
    fun fits(anchor: GridPosition, span: GridSpan): Boolean {
        return anchor.row >= 0 &&
            anchor.column >= 0 &&
            anchor.column + span.columns <= columns &&
            (rows == null || anchor.row + span.rows <= rows)
    }

    /**
     * Clamps [origin] so the whole [span] stays inside the bounds.
     * When [rows] is null the origin row is only clamped to be non-negative.
     */
    fun clamp(origin: GridPosition, span: GridSpan): GridPosition {
        val maxRow = rows?.let { (it - span.rows).coerceAtLeast(0) } ?: Int.MAX_VALUE
        val maxColumn = (columns - span.columns).coerceAtLeast(0)
        return GridPosition(
            row = origin.row.coerceIn(0, maxRow),
            column = origin.column.coerceIn(0, maxColumn)
        )
    }
}