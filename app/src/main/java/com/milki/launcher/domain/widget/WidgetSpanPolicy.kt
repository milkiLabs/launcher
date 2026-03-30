package com.milki.launcher.domain.widget

import com.milki.launcher.domain.model.GridSpan
import kotlin.math.roundToInt

/**
 * Produces a launcher-friendly default size for newly placed widgets.
 *
 * The provider-reported span is a useful starting point, but some widgets report
 * very large defaults that make initial placement awkward. This policy keeps
 * compact widgets unchanged while shrinking oversized widgets into a practical
 * first placement that users can still manually enlarge later.
 */
fun recommendWidgetPlacementSpan(
    rawSpan: GridSpan,
    gridColumns: Int,
    maxDefaultRows: Int = 3,
    maxDefaultArea: Int = (gridColumns * 2) + 2
): GridSpan {
    require(gridColumns >= 1) { "gridColumns must be at least 1" }

    var columns = rawSpan.columns.coerceAtLeast(1)
    var rows = rawSpan.rows.coerceAtLeast(1)

    if (columns > gridColumns) {
        val widthScale = gridColumns.toFloat() / columns.toFloat()
        columns = gridColumns
        rows = (rows * widthScale).roundToInt().coerceAtLeast(1)
    }

    if (rows > maxDefaultRows) {
        val heightScale = maxDefaultRows.toFloat() / rows.toFloat()
        rows = maxDefaultRows
        columns = (columns * heightScale).roundToInt().coerceAtLeast(1)
    }

    val safeMaxArea = maxDefaultArea.coerceAtLeast(1)
    while (columns * rows > safeMaxArea && (columns > 1 || rows > 1)) {
        when {
            rows > 2 -> rows -= 1
            columns > 1 -> columns -= 1
            rows > 1 -> rows -= 1
        }
    }

    return GridSpan(
        columns = columns.coerceIn(1, gridColumns),
        rows = rows.coerceIn(1, maxDefaultRows)
    )
}
