package com.milki.launcher.domain.homegraph

import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.model.homeGridSpan
import com.milki.launcher.domain.widget.fitInlineWidgetSpanAtAnchor

internal fun HomeModelWriter.updateWidgetFrame(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.Command.UpdateWidgetFrame
): HomeModelWriter.Result = updateTopLevelItem(currentItems, command.widgetId) {
    if (command.newSpan.columns < 1 || command.newSpan.rows < 1) null
    else (it as? HomeItem.WidgetItem)?.withPosition(command.newPosition)?.withSpan(command.newSpan)
}

internal fun HomeModelWriter.updateWidgetDisplayMode(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.Command.UpdateWidgetDisplayMode
): HomeModelWriter.Result = updateTopLevelItem(currentItems, command.widgetId) {
    (it as? HomeItem.WidgetItem)?.withDisplayMode(command.displayMode)
}

internal fun HomeModelWriter.expandPopupWidget(
    currentItems: List<HomeItem>,
    command: HomeModelWriter.Command.ExpandPopupWidget
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val index = mutable.indexOfFirst { it.id == command.widgetId }
    val existing = mutable.getOrNull(index) as? HomeItem.WidgetItem
        ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

    val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = command.widgetId)
    val inlineSpan = fitInlineWidgetSpanAtAnchor(
        anchor = existing.position,
        preferredSpan = existing.span,
        occupiedCells = occupied.keys,
        gridColumns = gridColumns,
        visibleRows = command.visibleRows
    )

    return if (inlineSpan == null) {
        HomeModelWriter.Result.Rejected(HomeModelWriter.Error.TargetOccupied)
    } else {
        mutable[index] = existing
            .withDisplayMode(WidgetDisplayMode.Inline)
            .withSpan(inlineSpan)
        HomeModelWriter.Result.Applied(mutable)
    }
}

internal fun HomeModelWriter.updateTopLevelItem(
    currentItems: List<HomeItem>,
    itemId: String,
    modification: (HomeItem) -> HomeItem?
): HomeModelWriter.Result {
    val mutable = currentItems.toMutableList()
    val index = mutable.indexOfFirst { it.id == itemId }
    val existing = mutable.getOrNull(index) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.ItemNotFound)

    val updated = modification(existing) ?: return HomeModelWriter.Result.Rejected(HomeModelWriter.Error.InvalidWidgetOperation)
    val span = updated.homeGridSpan
    val occupied = HomeGraph.buildOccupiedCells(mutable, excludeItemId = itemId)

    val rejection = when {
        !isWithinGrid(updated.position, span, gridColumns) -> HomeModelWriter.Error.OutOfBounds
        !isSpanFree(updated.position, span, occupied) -> HomeModelWriter.Error.TargetOccupied
        else -> null
    }

    return if (rejection != null) {
        HomeModelWriter.Result.Rejected(rejection)
    } else {
        mutable[index] = updated
        HomeModelWriter.Result.Applied(mutable)
    }
}
