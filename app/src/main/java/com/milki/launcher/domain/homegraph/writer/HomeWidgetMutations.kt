package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.HomeItem

/**
 * Handles widget frame updates in the writer pipeline.
 */
internal class HomeWidgetMutations(
    private val context: HomeModelMutationContext
) {

    fun updateWidgetFrame(
        currentItems: List<HomeItem>,
        command: HomeModelWriter.Command.UpdateWidgetFrame
    ): HomeModelWriter.Result {
        if (command.newSpan.columns < 1 || command.newSpan.rows < 1) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidWidgetOperation)
        }

        val mutable = currentItems.toMutableList()
        val widgetIndex = mutable.indexOfFirst { it.id == command.widgetId }
        if (widgetIndex == -1) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)
        }

        val widget = mutable[widgetIndex] as? HomeItem.WidgetItem
            ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidWidgetOperation)

        if (!context.isWithinGrid(command.newPosition, command.newSpan)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.OutOfBounds)
        }

        val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.widgetId)
        if (!context.isSpanFree(command.newPosition, command.newSpan, occupied)) {
            return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.TargetOccupied)
        }

        mutable[widgetIndex] = widget
            .withPosition(command.newPosition)
            .withSpan(command.newSpan)
        return HomeModelWriter.Result.Applied(mutable)
    }
}