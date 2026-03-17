package com.milki.launcher.domain.drag.reorder

import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

object OccupancyBuilder {
    fun fromItems(items: List<HomeItem>, excludeItemId: String? = null): OccupancySnapshot {
        val occupied = linkedSetOf<com.milki.launcher.domain.model.GridPosition>()
        for (item in items) {
            if (item.id == excludeItemId) continue
            val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
            occupied.addAll(span.occupiedPositions(item.position))
        }
        return OccupancySnapshot(occupiedCells = occupied)
    }
}
