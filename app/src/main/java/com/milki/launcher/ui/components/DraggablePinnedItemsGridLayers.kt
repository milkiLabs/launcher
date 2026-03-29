package com.milki.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.dragdrop.AppDragDropController
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.AppDragDropResult
import com.milki.launcher.ui.components.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.animateDragVisuals
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.components.widget.HomeScreenWidgetView
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * InternalGridDragLayer owns on-grid item rendering and internal drag gesture handling.
 *
 * RESPONSIBILITIES:
 * - Empty-grid long-press handling
 * - Icon/widget rendering
 * - Internal drag move/folder routing decisions
 * - Item-level context menus
 */
@Composable
internal fun InternalGridDragLayer(
    items: List<HomeItem>,
    config: GridConfig,
    dragController: AppDragDropController<HomeItem>,
    layoutMetrics: AppDragDropLayoutMetrics,
    cellWidthPx: Float,
    cellHeightPx: Float,
    maxVisibleRows: Int,
    isExternalDragActive: Boolean,
    widgetHostManager: WidgetHostManager?,
    menuShownForItemId: String?,
    onMenuShownForItemIdChange: (String?) -> Unit,
    isMenuGestureActive: Boolean,
    onMenuGestureActiveChange: (Boolean) -> Unit,
    onResizeModeRequested: (String?) -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    onEmptyAreaLongPress: (Offset) -> Unit,
    onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit,
    onRemoveWidget: (widgetId: String, appWidgetId: Int) -> Unit,
    hapticLongPress: () -> Unit,
    hapticDragActivate: () -> Unit,
    hapticConfirm: () -> Unit,
    onItemBoundsMeasured: (itemId: String, boundsInWindow: Rect) -> Unit
) {
    val internalDropDispatcher = InternalHomeDropDispatcher(
        gridColumns = config.columns,
        gridRows = maxVisibleRows,
        callbacks = InternalDropRoutingCallbacks(
            onItemMove = onItemMove,
            onCreateFolder = onCreateFolder,
            onAddItemToFolder = onAddItemToFolder,
            onMergeFolders = onMergeFolders,
            onConfirmDrop = hapticConfirm
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(items, cellWidthPx, cellHeightPx, config.columns, maxVisibleRows) {
                detectTapGestures(
                    onLongPress = { longPressOffset ->
                        if (isExternalDragActive) return@detectTapGestures
                        if (dragController.session != null) return@detectTapGestures

                        val pressedCell = layoutMetrics.pixelToCell(longPressOffset)
                        val isCellOccupied = items.findOccupantAt(pressedCell) != null
                        if (!isCellOccupied) {
                            hapticLongPress()
                            onEmptyAreaLongPress(longPressOffset)
                        }
                    }
                )
            }
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap to search",
                    color = Color.White.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        items.forEach { item ->
            key(item.id, item.position.row, item.position.column) {
                val isBeingDragged = dragController.isDraggingItem(item.id)
                val visuals = animateDragVisuals(isBeingDragged, config)
                val basePosition = dragController.resolveBasePosition(item.id, item.position)
                val span = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (basePosition.column * cellWidthPx).roundToInt(),
                                y = (basePosition.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(
                            width = with(LocalDensity.current) { (cellWidthPx * span.columns).toDp() },
                            height = with(LocalDensity.current) { (cellHeightPx * span.rows).toDp() }
                        )
                        .padding(Spacing.extraSmall)
                        .zIndex(visuals.zIndex)
                        .graphicsLayer {
                            scaleX = visuals.scale
                            scaleY = visuals.scale
                            alpha = visuals.alpha
                        }
                        .onGloballyPositioned { coords ->
                            val topLeft = coords.localToWindow(Offset.Zero)
                            onItemBoundsMeasured(
                                item.id,
                                Rect(
                                    left = topLeft.x,
                                    top = topLeft.y,
                                    right = topLeft.x + coords.size.width,
                                    bottom = topLeft.y + coords.size.height
                                )
                            )
                        }
                        .detectDragGesture(
                            key = "${item.id}-${item.position.row}-${item.position.column}-${span.columns}-${span.rows}",
                            dragThreshold = config.dragThresholdPx,
                            onTap = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                if (dragController.session == null) {
                                    onItemClick(item)
                                }
                            },
                            onLongPress = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                if (dragController.session == null) {
                                    hapticLongPress()
                                    onMenuShownForItemIdChange(item.id)
                                    onMenuGestureActiveChange(true)
                                    onItemLongPress(item)
                                }
                            },
                            onLongPressRelease = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                onMenuGestureActiveChange(false)
                            },
                            onDragStart = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                hapticDragActivate()
                                onMenuShownForItemIdChange(null)
                                onMenuGestureActiveChange(false)
                                dragController.startDrag(
                                    item = item,
                                    itemId = item.id,
                                    startPosition = item.position
                                )
                            },
                            onDrag = { change, dragAmount ->
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                if (dragController.isDraggingItem(item.id)) {
                                    change.consume()
                                    dragController.updateDrag(dragAmount, layoutMetrics)
                                }
                            },
                            onDragEnd = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                val result = dragController.endDrag(layoutMetrics)
                                if (result is AppDragDropResult.Moved && result.itemId == item.id) {
                                    internalDropDispatcher.dispatch(
                                        draggedItem = item,
                                        dropPosition = result.to,
                                        items = items
                                    )
                                }
                            },
                            onDragCancel = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                dragController.cancelDrag()
                                onMenuGestureActiveChange(false)
                            }
                        )
                ) {
                    if (item is HomeItem.WidgetItem && widgetHostManager != null) {
                        HomeScreenWidgetView(
                            appWidgetId = item.appWidgetId,
                            widgetHostManager = widgetHostManager,
                            widthPx = (cellWidthPx * span.columns).toInt(),
                            heightPx = (cellHeightPx * span.rows).toInt(),
                            onWidgetLongPress = {
                                if (dragController.session == null) {
                                    hapticLongPress()
                                    onMenuShownForItemIdChange(item.id)
                                    onMenuGestureActiveChange(true)
                                    onItemLongPress(item)
                                }
                            },
                            onWidgetLongPressRelease = {
                                onMenuGestureActiveChange(false)
                            },
                            onWidgetDragStart = {
                                if (dragController.session == null) {
                                    hapticDragActivate()
                                    onMenuShownForItemIdChange(null)
                                    onMenuGestureActiveChange(false)
                                    dragController.startDrag(
                                        item = item,
                                        itemId = item.id,
                                        startPosition = item.position
                                    )
                                }
                            },
                            onWidgetDrag = { dragAmount ->
                                if (dragController.isDraggingItem(item.id)) {
                                    dragController.updateDrag(dragAmount, layoutMetrics)
                                }
                            },
                            onWidgetDragEnd = {
                                val result = dragController.endDrag(layoutMetrics)
                                if (result is AppDragDropResult.Moved && result.itemId == item.id) {
                                    internalDropDispatcher.dispatch(
                                        draggedItem = item,
                                        dropPosition = result.to,
                                        items = items
                                    )
                                }
                            },
                            onWidgetDragCancel = {
                                dragController.cancelDrag()
                                onMenuGestureActiveChange(false)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        WidgetContextMenu(
                            expanded = menuShownForItemId == item.id,
                            onDismiss = {
                                onMenuShownForItemIdChange(null)
                                onMenuGestureActiveChange(false)
                            },
                            focusable = !isMenuGestureActive,
                            onResize = {
                                onMenuShownForItemIdChange(null)
                                onMenuGestureActiveChange(false)
                                onResizeModeRequested(item.id)
                            },
                            onRemove = {
                                onMenuShownForItemIdChange(null)
                                onMenuGestureActiveChange(false)
                                onRemoveWidget(item.id, item.appWidgetId)
                            },
                            hapticConfirm = hapticConfirm
                        )
                    } else {
                        PinnedItem(
                            item = item,
                            onClick = {},
                            onLongClick = {},
                            handleLongPress = false,
                            showMenu = menuShownForItemId == item.id,
                            onMenuDismiss = {
                                onMenuShownForItemIdChange(null)
                                onMenuGestureActiveChange(false)
                            },
                            menuFocusable = !isMenuGestureActive
                        )
                    }
                }
            }
        }
    }
}

/**
 * WidgetOverlayLayer is isolated so widget resize behavior can evolve without
 * touching the general grid drag/drop and external routing code.
 */
@Composable
internal fun WidgetOverlayLayer(
    items: List<HomeItem>,
    resizingWidgetId: String?,
    onResizeModeRequested: (String?) -> Unit,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    onResizeWidget: (widgetId: String, newSpan: GridSpan) -> Unit
) {
    resizingWidgetId?.let { widgetId ->
        val widgetItem = items.filterIsInstance<HomeItem.WidgetItem>()
            .find { it.id == widgetId }

        if (widgetItem != null) {
            WidgetResizeOverlay(
                widgetItem = widgetItem,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                gridColumns = gridColumns,
                items = items,
                onConfirmResize = { newSpan ->
                    onResizeModeRequested(null)
                    onResizeWidget(widgetItem.id, newSpan)
                }
            )
        } else {
            onResizeModeRequested(null)
        }
    }
}

/**
 * DropHighlightLayer renders both internal-drag and external-drag highlights.
 *
 * UNIFIED APPROACH:
 * Both internal (homescreen-to-homescreen) and external (folder/search/drawer)
 * drags use the same [DropTargetHighlightBox] composable for the blue-glow
 * highlight at the target cell, and the same [DropPreviewContent] for the
 * dimmed icon or widget-size text inside the box.
 *
 * SPLIT BENEFIT:
 * Rendering-heavy visual logic is decoupled from routing callbacks.
 */
@Composable
internal fun DropHighlightLayer(
    items: List<HomeItem>,
    config: GridConfig,
    dragController: AppDragDropController<HomeItem>,
    layoutMetrics: AppDragDropLayoutMetrics,
    cellWidthPx: Float,
    cellHeightPx: Float,
    maxVisibleRows: Int,
    dragTargetOccupant: HomeItem?,
    resolvedInternalPreviewPosition: GridPosition?,
    isExternalDragActive: Boolean,
    externalDragTargetPosition: GridPosition?,
    externalDragItem: ExternalDragDropItem?
) {
    // ── Internal drag highlight + floating preview ────────────────────────
    dragController.session?.let { activeSession ->
        val target = resolvedInternalPreviewPosition
            ?: dragController.targetPosition
            ?: activeSession.startPosition
        val previewBaseOffset = layoutMetrics.cellToPixel(activeSession.startPosition)
        val previewOffset = previewBaseOffset + activeSession.currentOffset
        val previewSpan = (activeSession.item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE

        val isDraggingFolder = activeSession.item is HomeItem.FolderItem
        val isInvalidDrop = isDraggingFolder && dragTargetOccupant != null && dragTargetOccupant !is HomeItem.FolderItem

        if (!isInvalidDrop) {
            val isFolderMerge = dragTargetOccupant != null
            val highlightColor = if (isFolderMerge) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }
            val highlightScale = when {
                activeSession.item is HomeItem.WidgetItem -> 1f
                isFolderMerge -> config.dropHighlightScale * 1.05f
                else -> config.dropHighlightScale
            }

            DropTargetHighlightBox(
                column = target.column,
                row = target.row,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                spanColumns = previewSpan.columns,
                spanRows = previewSpan.rows,
                highlightColor = highlightColor,
                highlightScale = highlightScale,
                zIndex = config.dragZIndex
            ) {
                val previewItem = if (activeSession.item is HomeItem.WidgetItem) null
                    else (dragTargetOccupant ?: activeSession.item)
                val widgetSpan = if (activeSession.item is HomeItem.WidgetItem) previewSpan else null

                DropPreviewContent(
                    item = previewItem,
                    highlightAlpha = config.dropHighlightAlpha,
                    widgetSpan = widgetSpan
                )
            }
        }

        // Floating preview that follows the finger (internal drag only;
        // external drags use the platform drag shadow instead).
        // For widgets, we do NOT show a floating preview — only the drop target highlight.
        if (activeSession.item !is HomeItem.WidgetItem) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = previewOffset.x.roundToInt(),
                            y = previewOffset.y.roundToInt()
                        )
                    }
                    .size(
                        width = with(LocalDensity.current) { (cellWidthPx * previewSpan.columns).toDp() },
                        height = with(LocalDensity.current) { (cellHeightPx * previewSpan.rows).toDp() }
                    )
                    .padding(Spacing.extraSmall)
                    .zIndex(config.previewZIndex)
                    .graphicsLayer {
                        scaleX = config.previewScale
                        scaleY = config.previewScale
                        alpha = config.previewAlpha
                        shadowElevation = config.shadowElevation
                    }
            ) {
                PinnedItem(
                    item = activeSession.item,
                    onClick = {},
                    onLongClick = {},
                    handleLongPress = false
                )
            }
        }
    }

    // ── External drag highlight ──────────────────────────────────────────
    if (isExternalDragActive) {
        externalDragTargetPosition?.let { targetPosition ->
            val currentExternalItem = externalDragItem
            val rawDragSpan = (currentExternalItem as? ExternalDragItem.Widget)?.span ?: GridSpan.SINGLE
            val dragSpan = normalizeWidgetSpanForHomeGrid(rawSpan = rawDragSpan, gridColumns = config.columns)

            val clampedTarget = GridPosition(
                row = targetPosition.row.coerceIn(0, (maxVisibleRows - dragSpan.rows).coerceAtLeast(0)),
                column = targetPosition.column.coerceIn(0, (config.columns - dragSpan.columns).coerceAtLeast(0))
            )

            val spanCells = dragSpan.occupiedPositions(clampedTarget)
            val hasCollision = items.any { existingItem ->
                val existingSpan = (existingItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                val existingCells = existingSpan.occupiedPositions(existingItem.position)
                existingCells.any { it in spanCells }
            }

            val highlightColor = if (hasCollision) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
            val highlightScale = if (currentExternalItem is ExternalDragItem.Widget) 1f else config.dropHighlightScale

            DropTargetHighlightBox(
                column = clampedTarget.column,
                row = clampedTarget.row,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                spanColumns = dragSpan.columns,
                spanRows = dragSpan.rows,
                highlightColor = highlightColor,
                highlightScale = highlightScale,
                zIndex = config.dragZIndex
            ) {
                val previewItem = currentExternalItem?.toPreviewHomeItem()
                val widgetSpan = if (currentExternalItem is ExternalDragItem.Widget) dragSpan else null

                DropPreviewContent(
                    item = previewItem,
                    highlightAlpha = config.dropHighlightAlpha,
                    widgetSpan = widgetSpan
                )
            }
        }
    }
}

// ── Shared drop-target visual composables ─────────────────────────────────

/**
 * Shared blue-glow drop-target highlight box.
 *
 * Used by BOTH internal (homescreen-to-homescreen) and external (folder/search/drawer)
 * drag paths to ensure consistent visuals.
 *
 * Renders a rounded box at the given grid cell with:
 * - Colored shadow (glow effect)
 * - Semi-transparent background fill
 * - Colored border
 * - Scaled via graphicsLayer
 */
@Composable
private fun DropTargetHighlightBox(
    column: Int,
    row: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    spanColumns: Int,
    spanRows: Int,
    highlightColor: Color,
    highlightScale: Float,
    zIndex: Float,
    content: @Composable () -> Unit
) {
    val highlightShape = RoundedCornerShape(CornerRadius.medium)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (column * cellWidthPx).roundToInt(),
                    y = (row * cellHeightPx).roundToInt()
                )
            }
            .size(
                width = with(LocalDensity.current) { (cellWidthPx * spanColumns).toDp() },
                height = with(LocalDensity.current) { (cellHeightPx * spanRows).toDp() }
            )
            .padding(Spacing.extraSmall)
            .zIndex(zIndex)
            .shadow(
                elevation = Spacing.smallMedium,
                shape = highlightShape,
                ambientColor = highlightColor.copy(alpha = 0.6f),
                spotColor = highlightColor.copy(alpha = 0.6f)
            )
            .background(
                color = highlightColor.copy(alpha = 0.15f),
                shape = highlightShape
            )
            .border(
                width = Spacing.extraSmall,
                color = highlightColor.copy(alpha = 0.4f),
                shape = highlightShape
            )
            .graphicsLayer {
                scaleX = highlightScale
                scaleY = highlightScale
            }
    ) {
        content()
    }
}

/**
 * Shared drop-target preview content.
 *
 * Renders the appropriate content inside a [DropTargetHighlightBox]:
 * - Dimmed [PinnedItem] when a [HomeItem] preview is available
 * - Widget span text (e.g. "4 × 2") for widget drags
 * - Empty (just the glow box) when neither is available
 */
@Composable
private fun DropPreviewContent(
    item: HomeItem?,
    highlightAlpha: Float,
    widgetSpan: GridSpan? = null
) {
    when {
        item != null -> {
            Box(modifier = Modifier.alpha(highlightAlpha)) {
                PinnedItem(
                    item = item,
                    onClick = {},
                    onLongClick = {},
                    handleLongPress = false
                )
            }
        }
        widgetSpan != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${widgetSpan.columns} × ${widgetSpan.rows}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        // else: empty — just the glow box (e.g., when item is unknown)
    }
}

/**
 * ExternalDropRoutingLayer isolates platform drag callbacks and routing decisions.
 *
 * BEHAVIOR GUARANTEE:
 * All external payload branches (folder child, widget, regular home-item preview)
 * are still handled in one place, but no longer intermixed with internal drag UI.
 */
@Composable
internal fun ExternalDropRoutingLayer(
    items: List<HomeItem>,
    config: GridConfig,
    layoutMetrics: AppDragDropLayoutMetrics,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager?,
    isExternalDragActive: Boolean,
    externalDragTargetPosition: GridPosition?,
    onExternalDragActiveChange: (Boolean) -> Unit,
    onExternalTargetPositionChange: (GridPosition?) -> Unit,
    onExternalDragItemChange: (ExternalDragDropItem?) -> Unit,
    onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit,
    onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    onFolderItemExtracted: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit,
    onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit,
    onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit,
    onWidgetDroppedToHome: (providerInfo: android.appwidget.AppWidgetProviderInfo, span: GridSpan, dropPosition: GridPosition) -> Unit,
    hapticConfirm: () -> Unit
) {
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
            onExternalDragActiveChange(true)
            onExternalTargetPositionChange(null)
            onExternalDragItemChange(null)
        },
        onDragMoved = { localOffset, item ->
            onExternalTargetPositionChange(layoutMetrics.pixelToCell(localOffset))
            if (item != null) {
                onExternalDragItemChange(item)
            }
        },
        onDragEnded = {
            onExternalDragActiveChange(false)
            onExternalTargetPositionChange(null)
            onExternalDragItemChange(null)
        },
        onItemDropped = { item, localOffset ->
            val dropPosition = if (isExternalDragActive && externalDragTargetPosition != null) {
                externalDragTargetPosition
            } else {
                layoutMetrics.pixelToCell(localOffset)
            }

            onExternalTargetPositionChange(dropPosition)
            onExternalDragItemChange(item)

            dropDispatcher.dispatch(
                item = item,
                dropPosition = dropPosition,
                items = items
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(config.previewZIndex + 1f)
    )
}

/**
 * Context menu shown when a widget is long-pressed.
 */
@Composable
private fun WidgetContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    hapticConfirm: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = focusable)
    ) {
        DropdownMenuItem(
            text = { Text("Resize") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = null
                )
            },
            onClick = {
                hapticConfirm()
                onResize()
            }
        )
        DropdownMenuItem(
            text = { Text("Remove") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null
                )
            },
            onClick = {
                hapticConfirm()
                onRemove()
            }
        )
    }
}

/**
 * Widget resize overlay used by WidgetOverlayLayer.
 */
@Composable
private fun WidgetResizeOverlay(
    widgetItem: HomeItem.WidgetItem,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    items: List<HomeItem>,
    onConfirmResize: (GridSpan) -> Unit
) {
    var previewSpan by remember(widgetItem.id) { mutableStateOf(widgetItem.span) }

    val occupiedCells = remember(items, widgetItem.id) {
        val cells = mutableSetOf<GridPosition>()
        for (item in items) {
            if (item.id == widgetItem.id) continue
            if (item is HomeItem.WidgetItem) {
                cells.addAll(item.span.occupiedPositions(item.position))
            } else {
                cells.add(item.position)
            }
        }
        cells
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures {
                    onConfirmResize(previewSpan)
                }
            }
    )

    val originX = (widgetItem.position.column * cellWidthPx).roundToInt()
    val originY = (widgetItem.position.row * cellHeightPx).roundToInt()
    val frameWidth = (previewSpan.columns * cellWidthPx).roundToInt()
    val frameHeight = (previewSpan.rows * cellHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(originX, originY) }
            .size(
                width = with(LocalDensity.current) { frameWidth.toFloat().toDp() },
                height = with(LocalDensity.current) { frameHeight.toFloat().toDp() }
            )
            .zIndex(51f)
            .border(
                width = Spacing.extraSmall,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(CornerRadius.small)
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = Spacing.smallMedium, y = Spacing.smallMedium)
                .size(IconSize.standard)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .zIndex(52f)
                .pointerInput(widgetItem.id) {
                    var accumulatedDragX = 0f
                    var accumulatedDragY = 0f

                    detectDragGestures(
                        onDragStart = {
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDragX += dragAmount.x
                            accumulatedDragY += dragAmount.y

                            val newCols = (widgetItem.span.columns + (accumulatedDragX / cellWidthPx).roundToInt())
                                .coerceIn(1, gridColumns - widgetItem.position.column)
                            val newRows = (widgetItem.span.rows + (accumulatedDragY / cellHeightPx).roundToInt())
                                .coerceAtLeast(1)

                            val candidateSpan = GridSpan(columns = newCols, rows = newRows)
                            val candidateCells = candidateSpan.occupiedPositions(widgetItem.position)
                            val hasCollision = candidateCells.any { it in occupiedCells }

                            if (!hasCollision) {
                                previewSpan = candidateSpan
                            }
                        },
                        onDragEnd = {
                            // Confirmation happens on scrim tap.
                        }
                    )
                }
        )
    }
}
