package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.interaction.grid.GridConfig

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
    widgetHostManager: WidgetHostManager?,
    onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit,
    onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    onFolderItemExtracted: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit,
    onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit,
    onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit,
    onWidgetDroppedToHome: (providerInfo: android.appwidget.AppWidgetProviderInfo, span: GridSpan, dropPosition: GridPosition) -> Unit,
    hapticConfirm: () -> Unit
) {
    val latestItems by rememberUpdatedState(items)

    val dropDispatcher = ExternalHomeDropDispatcher(
        gridColumns = config.columns,
        maxVisibleRows = maxVisibleRows,
        widgetHostManager = widgetHostManager,
        callbacks = ExternalDropRoutingCallbacks(
            onItemDroppedToHome = onItemDroppedToHome,
            onCreateFolder = onCreateFolder,
            onAddItemToFolder = onAddItemToFolder,
            onFolderItemExtracted = onFolderItemExtracted,
            onMoveFolderItemToFolder = onMoveFolderItemToFolder,
            onFolderChildDroppedOnItem = onFolderChildDroppedOnItem,
            onWidgetDroppedToHome = onWidgetDroppedToHome,
            onConfirmDrop = hapticConfirm
        )
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
            val dropPosition = if (
                externalDragState.isActive &&
                externalDragState.targetPosition != null
            ) {
                externalDragState.targetPosition
            } else {
                layoutMetrics.pixelToCell(localOffset)
            }

            val resolvedDropPosition = requireNotNull(dropPosition) {
                "External drop routing requires a resolved grid position."
            }

            interactionController.onExternalDropCommitted(
                targetPosition = resolvedDropPosition,
                item = item
            )

            dropDispatcher.dispatch(
                item = item,
                dropPosition = resolvedDropPosition,
                items = latestItems
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(config.previewZIndex + 1f)
    )
}
