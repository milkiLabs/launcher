package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Handles top-level grid operations (pin, move, remove).
 */
internal class HomeTopLevelMutations(
    private val context: HomeModelMutationContext
) {

    fun addPinnedItem(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.AddPinnedItem
    ): HomeModelWriter.Result {
        if (context.containsItemIdAnywhere(currentItems, command.item.id)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.DuplicateItem)
        }

        val mutable = currentItems.toMutableList()
        val position = context.findAvailablePosition(mutable, command.maxRows)
        mutable.add(command.item.withPosition(position))
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun moveTopLevelItem(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.MoveTopLevelItem
    ): HomeModelWriter.Result {
        val index = currentItems.indexOfFirst { it.id == command.itemId }
        if (index == -1) return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

        val existing = currentItems[index]
        val span = (existing as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        if (!context.isWithinGrid(command.newPosition, span)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.OutOfBounds)
        }

        val occupied = HomeGraph.buildOccupiedCells(
            items = currentItems,
            excludeItemId = command.itemId
        )
        if (!context.isSpanFree(command.newPosition, span, occupied)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.TargetOccupied)
        }

        val updated = currentItems.toMutableList()
        updated[index] = existing.withPosition(command.newPosition)
        return HomeModelWriter.Result.Applied(updated)
    }

    fun pinOrMove(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.PinOrMoveToPosition
    ): HomeModelWriter.Result {
        val mutable = currentItems.toMutableList()

        val existingIndex = mutable.indexOfFirst { it.id == command.item.id }
        val existingItem = if (existingIndex >= 0) mutable[existingIndex] else null

        context.evictItemEverywhere(mutable, command.item.id)

        val canonicalItem = existingItem ?: command.item
        val span = (canonicalItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
        if (!context.isWithinGrid(command.targetPosition, span)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.OutOfBounds)
        }

        val occupied = HomeGraph.buildOccupiedCells(mutable)
        if (!context.isSpanFree(command.targetPosition, span, occupied)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.TargetOccupied)
        }

        val placed = canonicalItem.withPosition(command.targetPosition)
        val insertionIndex = if (existingIndex in 0..mutable.size) existingIndex else mutable.size
        mutable.add(insertionIndex, placed)
        return HomeModelWriter.Result.Applied(mutable)
    }

    fun removeItemById(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.RemoveItemById
    ): HomeModelWriter.Result {
        return removeItemsById(
            currentItems = currentItems,
            command = HomeModelWriter.Command.RemoveItemsById(itemIds = setOf(command.itemId))
        )
    }

    fun removeItemsById(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.RemoveItemsById
    ): HomeModelWriter.Result {
        if (command.itemIds.isEmpty()) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
        }

        val existingIds = command.itemIds.filterTo(mutableSetOf()) { itemId ->
            context.containsItemIdAnywhere(currentItems, itemId)
        }
        if (existingIds.isEmpty()) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
        }

        val mutable = currentItems.toMutableList()
        existingIds.forEach { itemId ->
            context.evictItemEverywhere(mutable, itemId)
        }
        return HomeModelWriter.Result.Applied(mutable)
    }
}