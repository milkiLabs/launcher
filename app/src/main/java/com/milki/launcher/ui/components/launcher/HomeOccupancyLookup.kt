package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Builds a span-aware occupancy lookup map for home-grid items.
 */
fun buildHomeOccupancyLookup(items: List<HomeItem>): Map<GridPosition, HomeItem> {
    if (items.isEmpty()) return emptyMap()

    val lookup = HashMap<GridPosition, HomeItem>(items.size * 2)
    items.forEach { item ->
        val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        span.occupiedPositions(item.position).forEach { cell ->
            lookup.putIfAbsent(cell, item)
        }
    }

    return lookup
}
