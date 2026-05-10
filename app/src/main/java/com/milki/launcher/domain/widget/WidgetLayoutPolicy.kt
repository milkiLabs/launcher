package com.milki.launcher.domain.widget

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan

fun fitInlineWidgetSpanAtAnchor(
    anchor: GridPosition,
    preferredSpan: GridSpan,
    occupiedCells: Set<GridPosition>,
    gridColumns: Int,
    visibleRows: Int
): GridSpan? {
    if (anchor.row < 0 || anchor.column < 0 || anchor.column >= gridColumns) {
        return null
    }

    val maxColumnsAtAnchor = (gridColumns - anchor.column)
        .coerceAtMost(preferredSpan.columns)
        .coerceAtLeast(0)
    val maxRowsAtAnchor = (visibleRows - anchor.row)
        .coerceAtMost(preferredSpan.rows)
        .coerceAtLeast(0)

    if (maxColumnsAtAnchor < 1 || maxRowsAtAnchor < 1) {
        return null
    }

    return (1..maxColumnsAtAnchor)
        .asSequence()
        .flatMap { columns ->
            (1..maxRowsAtAnchor).asSequence().map { rows ->
                GridSpan(columns = columns, rows = rows)
            }
        }
        .filter { span ->
            span.occupiedPositions(anchor).none { cell -> cell in occupiedCells }
        }
        .maxWithOrNull(
            compareBy<GridSpan> { it.columns * it.rows }
                .thenBy { it.columns }
                .thenBy { it.rows }
        )
}
