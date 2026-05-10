package com.milki.launcher.domain.drag.reorder

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan

data class OccupancySnapshot(
    val occupiedCells: Set<GridPosition>
) {
    fun isSpanFree(anchor: GridPosition, span: GridSpan, gridColumns: Int, gridRows: Int): Boolean {
        if (anchor.row < 0 || anchor.column < 0) return false
        if (anchor.column + span.columns > gridColumns) return false
        if (anchor.row + span.rows > gridRows) return false

        for (cell in span.occupiedPositions(anchor)) {
            if (cell in occupiedCells) return false
        }
        return true
    }
}
