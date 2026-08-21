package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.reorder.GridReorderEngine
import com.milki.launcher.ui.components.launcher.widget.LocalWidgetHost
import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.screens.launcher.FolderActions
import com.milki.launcher.ui.screens.launcher.HomeActions
import com.milki.launcher.ui.screens.launcher.WidgetActions

/**
 * ExternalDropRoutingLayer isolates platform drag callbacks and routing decisions.
 */
@Composable
internal fun ExternalDropRoutingLayer(
    items: List<HomeItem>,
    config: GridConfig,
    interactionController: HomeSurfaceInteractionController,
    layoutMetrics: AppDragDropLayoutMetrics,
    maxVisibleRows: Int,
    reorderEngine: GridReorderEngine,
    occupancy: GridOccupancy,
    home: HomeActions,
    folder: FolderActions,
    widget: WidgetActions,
    hapticConfirm: () -> Unit
) {
    val widgetHost = LocalWidgetHost.current
    val latestItems by rememberUpdatedState(items)
    val handlers = ExternalDropHandlers(
        onItemDroppedToHome = home.onItemDroppedToHome,
        onCreateFolder = folder.onCreateFolder,
        onAddItemToFolder = folder.onAddItemToFolder,
        onFolderItemExtracted = folder.onExtractItemFromFolder,
        onMoveFolderItemToFolder = folder.onMoveFolderItemToFolder,
        onFolderChildDroppedOnItem = folder.onFolderChildDroppedOnItem,
        onWidgetDroppedToHome = widget.onWidgetDroppedToHome,
        onConfirmDrop = hapticConfirm
    )

    AppExternalDropTargetOverlay(
        onDragStarted = {
            interactionController.onExternalDragStarted()
        },
        onDragMoved = { localOffset, item ->
            interactionController.onExternalDragMoved(
                targetPosition = layoutMetrics.pixelToCell(localOffset),
                item = item
            )
        },
        onDragEnded = {
            interactionController.onExternalDragEnded()
        },
        onItemDropped = { item, localOffset ->
            val externalDragState = interactionController.externalDragState
            val resolvedDropPosition = if (
                externalDragState.isActive &&
                externalDragState.targetPosition != null
            ) {
                externalDragState.targetPosition
            } else {
                layoutMetrics.pixelToCell(localOffset)
            }

            interactionController.onExternalDropCommitted(
                targetPosition = resolvedDropPosition,
                item = item
            )

            val action = resolveExternalDropAction(
                item = item,
                dropPosition = resolvedDropPosition,
                items = latestItems,
                gridColumns = config.columns,
                maxVisibleRows = maxVisibleRows,
                widgetHost = widgetHost,
                reorderEngine = reorderEngine,
                occupancy = occupancy
            ) ?: return@AppExternalDropTargetOverlay false

            return@AppExternalDropTargetOverlay applyExternalDropAction(action, handlers)
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(config.previewZIndex + 1f)
    )
}
