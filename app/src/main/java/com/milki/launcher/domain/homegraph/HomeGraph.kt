package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Canonical in-memory snapshot of home items with precomputed occupancy.
 */
data class HomeGraph(
    val items: List<HomeItem>,
    val occupiedCells: Map<GridPosition, String>
) {
    fun findTopLevelItem(itemId: String): HomeItem? = items.firstOrNull { it.id == itemId }

    fun occupiedByOther(position: GridPosition, excludeItemId: String? = null): Boolean {
        val occupant = occupiedCells[position] ?: return false
        return excludeItemId == null || occupant != excludeItemId
    }

    companion object {
        fun fromItems(items: List<HomeItem>): HomeGraph {
            return HomeGraph(items = items, occupiedCells = buildOccupiedCells(items))
        }

        fun buildOccupiedCells(
            items: List<HomeItem>,
            excludeItemId: String? = null
        ): Map<GridPosition, String> {
            val cells = mutableMapOf<GridPosition, String>()
            for (item in items) {
                if (item.id == excludeItemId) continue
                val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                for (cell in span.occupiedPositions(item.position)) {
                    cells[cell] = item.id
                }
            }
            return cells
        }
    }
}
