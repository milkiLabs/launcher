package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.model.homeGridSpan

internal fun HomeModelWriter.addPinnedItem(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.AddPinnedItem
): HomeModelWriter.Result {
    if (containsItemIdAnywhere(currentItems, command.item.id)) {
        return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.DuplicateItem)
    }

    val mutable = currentItems.toMutableList()
    val position = findAvailablePosition(mutable, command.maxRows, gridColumns)
    mutable.add(command.item.withPosition(position))
    return HomeModelWriter.Result.Applied(mutable)
}

internal fun HomeModelWriter.moveTopLevelItem(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.MoveTopLevelItem
): HomeModelWriter.Result = updateTopLevelItem(currentItems, command.itemId) {
    it.withPosition(command.newPosition)
}

internal fun HomeModelWriter.pinOrMove(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.PinOrMoveToPosition
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val existingIndex = mutable.indexOfFirst { it.id == command.item.id }
    val existingItem = if (existingIndex >= 0) mutable[existingIndex] else null

    evictItemEverywhere(mutable, command.item.id)

    val canonicalItem = if (existingItem != null) {
        if (existingItem is HomeItem.WidgetItem && command.item is HomeItem.WidgetItem) {
            existingItem.withDisplayMode(command.item.displayMode).withSpan(command.item.span)
        } else {
            existingItem
        }
    } else {
        command.item
    }
    val span = canonicalItem.homeGridSpan
    val occupied = HomeGraph.buildOccupiedCells(mutable)
    val rejection = when {
        !isWithinGrid(command.targetPosition, span, gridColumns) -> HomeModelWriter.Error.OutOfBounds
        !isSpanFree(command.targetPosition, span, occupied) -> HomeModelWriter.Error.TargetOccupied
        else -> null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        val placed = canonicalItem.withPosition(command.targetPosition)
        val insertionIndex = if (existingIndex in 0..mutable.size) existingIndex else mutable.size
        mutable.add(insertionIndex, placed)
        HomeModelWriter.Result.Applied(mutable)
    }
    return result
}

internal fun HomeModelWriter.removeItemsById(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.RemoveItemsById
): HomeModelWriter.Result {
    val existingIds = command.itemIds.filterTo(mutableSetOf()) { itemId ->
        containsItemIdAnywhere(currentItems, itemId)
    }

    val rejection = if (command.itemIds.isEmpty() || existingIds.isEmpty()) {
        HomeModelWriter.Error.ItemNotFound
    } else {
        null
    }

    val result = if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        val mutable = currentItems.toMutableList()
        existingIds.forEach { itemId ->
            evictItemEverywhere(mutable, itemId)
        }
        HomeModelWriter.Result.Applied(mutable)
    }
    return result
}

