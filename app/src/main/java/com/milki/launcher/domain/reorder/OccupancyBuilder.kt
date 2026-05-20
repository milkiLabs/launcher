package com.milki.launcher.domain.reorder

import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.homeGridSpan

object OccupancyBuilder {
    fun fromItems(items: List<HomeItem>, excludeItemId: String? = null): OccupancySnapshot {
        val occupied = linkedSetOf<com.milki.launcher.domain.model.GridPosition>()
        for (item in items) {
            if (item.id == excludeItemId) continue
            val span = item.homeGridSpan
            occupied.addAll(span.occupiedPositions(item.position))
        }
        return OccupancySnapshot(occupiedCells = occupied)
    }
}
