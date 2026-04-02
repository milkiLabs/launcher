package com.milki.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.widget.WidgetFrame
import com.milki.launcher.domain.widget.WidgetTransformHandle
import com.milki.launcher.domain.widget.applyWidgetTransformHandle
import com.milki.launcher.ui.components.dragdrop.AppDragDropController
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.AppDragDropResult
import com.milki.launcher.ui.components.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.animateDragVisuals
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.components.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.components.grid.detectHomeBackgroundGestures
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
    interactionController: HomeSurfaceInteractionController,
    dragController: AppDragDropController<HomeItem>,
    layoutMetrics: AppDragDropLayoutMetrics,
    cellWidthPx: Float,
    cellHeightPx: Float,
    maxVisibleRows: Int,
    widgetHostManager: WidgetHostManager?,
    backgroundGestures: HomeBackgroundGestureBindings,
    onItemClick: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit,
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit,
    onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit,
    onRemoveWidget: (widgetId: String, appWidgetId: Int) -> Unit,
    hapticLongPress: () -> Unit,
    hapticDragActivate: () -> Unit,
    hapticConfirm: () -> Unit,
    onItemBoundsMeasured: (itemId: String, boundsInWindow: Rect) -> Unit
) {
    val latestItems by rememberUpdatedState(items)

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

    val backgroundGesturePolicy = interactionController.backgroundGesturePolicy(backgroundGestures)

    fun showItemMenu(item: HomeItem) {
        if (!interactionController.showItemMenu(item.id)) return
        hapticLongPress()
        onItemLongPress(item)
    }

    fun startItemDrag(item: HomeItem) {
        if (!interactionController.startInternalDrag(item)) return
        hapticDragActivate()
    }

    fun updateItemDrag(item: HomeItem, change: PointerInputChange?, dragAmount: Offset) {
        interactionController.updateInternalDrag(
            itemId = item.id,
            change = change,
            dragAmount = dragAmount,
            layoutMetrics = layoutMetrics
        )
    }

    fun finishItemDrag(item: HomeItem) {
        val result = interactionController.finishInternalDrag(item, layoutMetrics) ?: return
        if (result is AppDragDropResult.Moved && result.itemId == item.id) {
            internalDropDispatcher.dispatch(
                draggedItem = item,
                dropPosition = result.to,
                items = latestItems
            )
        }
    }

    fun cancelItemDrag() {
        interactionController.cancelInternalDrag()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .detectHomeBackgroundGestures(
                key = "background-${items.size}-${interactionController.menuShownForItemId ?: "none"}-${interactionController.externalDragState.isActive}-${dragController.session?.itemId ?: "idle"}-${interactionController.widgetTransformSession?.widgetId ?: "none"}-${backgroundGesturePolicy.canSwipeUp}-${backgroundGesturePolicy.canSwipeDown}",
                items = items,
                layoutMetrics = layoutMetrics,
                policy = backgroundGesturePolicy,
                bindings = backgroundGestures.copy(
                    onEmptyAreaLongPress = { longPressOffset ->
                        hapticLongPress()
                        backgroundGestures.onEmptyAreaLongPress(longPressOffset)
                    }
                ),
                swipeUpThresholdPx = cellHeightPx
            )
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
                                if (dragController.session == null) onItemClick(item)
                            },
                            onLongPress = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                showItemMenu(item)
                            },
                            onLongPressRelease = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                interactionController.updateMenuGestureState(false)
                            },
                            onDragStart = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                startItemDrag(item)
                            },
                            onDrag = { change, dragAmount ->
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                updateItemDrag(item, change, dragAmount)
                            },
                            onDragEnd = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                finishItemDrag(item)
                            },
                            onDragCancel = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                cancelItemDrag()
                            }
                        )
                ) {
                    if (item is HomeItem.WidgetItem && widgetHostManager != null) {
                        HomeScreenWidgetView(
                            appWidgetId = item.appWidgetId,
                            widgetHostManager = widgetHostManager,
                            widthPx = (cellWidthPx * span.columns).toInt(),
                            heightPx = (cellHeightPx * span.rows).toInt(),
                            dragStartThresholdPx = config.dragThresholdPx,
                            onWidgetLongPress = {
                                showItemMenu(item)
                            },
                            onWidgetLongPressRelease = {
                                interactionController.updateMenuGestureState(false)
                            },
                            onWidgetDragStart = {
                                startItemDrag(item)
                            },
                            onWidgetDrag = { dragAmount ->
                                updateItemDrag(item, change = null, dragAmount = dragAmount)
                            },
                            onWidgetDragEnd = {
                                finishItemDrag(item)
                            },
                            onWidgetDragCancel = {
                                cancelItemDrag()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        WidgetContextMenu(
                            expanded = interactionController.menuShownForItemId == item.id,
                            onDismiss = {
                                interactionController.dismissMenu()
                            },
                            focusable = !interactionController.isMenuGestureActive,
                            onEdit = {
                                interactionController.startWidgetTransform(item.id)
                            },
                            onRemove = {
                                interactionController.dismissMenu()
                                onRemoveWidget(item.id, item.appWidgetId)
                            }
                        )
                    } else {
                        PinnedItem(
                            item = item,
                            onClick = {},
                            onLongClick = {},
                            handleLongPress = false,
                            showMenu = interactionController.menuShownForItemId == item.id,
                            onMenuDismiss = {
                                interactionController.dismissMenu()
                            },
                            menuFocusable = !interactionController.isMenuGestureActive
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
    widgetTransformSession: HomeWidgetTransformSession?,
    onFinishTransform: () -> Unit,
    onCancelTransform: () -> Unit,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    maxVisibleRows: Int,
    onUpdateWidgetFrame: (widgetId: String, newPosition: GridPosition, newSpan: GridSpan) -> Unit
) {
    widgetTransformSession?.let { session ->
        val widgetItem = items.filterIsInstance<HomeItem.WidgetItem>()
            .find { it.id == session.widgetId }

        if (widgetItem != null) {
            WidgetResizeOverlay(
                widgetItem = widgetItem,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                gridColumns = gridColumns,
                maxVisibleRows = maxVisibleRows,
                items = items,
                onConfirmTransform = { frame ->
                    onFinishTransform()
                    onUpdateWidgetFrame(widgetItem.id, frame.position, frame.span)
                },
                onCancelTransform = onCancelTransform
            )
        } else {
            onCancelTransform()
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
    externalDragState: HomeSurfaceExternalDragState
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
    if (externalDragState.isActive) {
        externalDragState.targetPosition?.let { targetPosition ->
            val currentExternalItem = externalDragState.item
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

/**
 * Context menu shown when a widget is long-pressed.
 */
@Composable
private fun WidgetContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    ItemActionMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        focusable = focusable,
        actions = listOf(
            MenuAction(
                label = "Edit",
                icon = Icons.Filled.AspectRatio,
                onClick = onEdit
            ),
            MenuAction(
                label = "Remove",
                icon = Icons.Filled.Delete,
                onClick = onRemove,
                isDestructive = true
            )
        )
    )
}

/**
 * Widget transform overlay used by WidgetOverlayLayer.
 */
@Composable
private fun WidgetResizeOverlay(
    widgetItem: HomeItem.WidgetItem,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    maxVisibleRows: Int,
    items: List<HomeItem>,
    onConfirmTransform: (WidgetFrame) -> Unit,
    onCancelTransform: () -> Unit
) {
    BackHandler(onBack = onCancelTransform)

    val originalFrame = remember(widgetItem.id, widgetItem.position, widgetItem.span) {
        WidgetFrame(position = widgetItem.position, span = widgetItem.span)
    }
    var draftFrame by remember(widgetItem.id, widgetItem.position, widgetItem.span) {
        mutableStateOf(originalFrame)
    }
    var lastValidFrame by remember(widgetItem.id, widgetItem.position, widgetItem.span) {
        mutableStateOf(originalFrame)
    }
    var isDraftValid by remember(widgetItem.id, widgetItem.position, widgetItem.span) {
        mutableStateOf(true)
    }

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

    fun isFrameFree(frame: WidgetFrame): Boolean {
        val occupiedByCandidate = frame.span.occupiedPositions(frame.position)
        return occupiedByCandidate.none { it in occupiedCells }
    }

    fun updateDraft(frame: WidgetFrame) {
        draftFrame = frame
        isDraftValid = isFrameFree(frame)
        if (isDraftValid) {
            lastValidFrame = frame
        }
    }

    fun settleDraftAfterGesture() {
        if (!isDraftValid) {
            draftFrame = lastValidFrame
            isDraftValid = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures {
                    onConfirmTransform(draftFrame)
                }
            }
    )

    val originX = (draftFrame.position.column * cellWidthPx).roundToInt()
    val originY = (draftFrame.position.row * cellHeightPx).roundToInt()
    val frameWidth = (draftFrame.span.columns * cellWidthPx).roundToInt()
    val frameHeight = (draftFrame.span.rows * cellHeightPx).roundToInt()
    val frameColor = if (isDraftValid) MaterialTheme.colorScheme.primary else Color(0xFFFF6B6B)

    Box(
        modifier = Modifier
            .offset { IntOffset(originX, originY) }
            .size(
                width = with(LocalDensity.current) { frameWidth.toFloat().toDp() },
                height = with(LocalDensity.current) { frameHeight.toFloat().toDp() }
            )
            .zIndex(51f)
            .background(
                color = frameColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(CornerRadius.small)
            )
            .border(
                width = Spacing.extraSmall,
                color = frameColor,
                shape = RoundedCornerShape(CornerRadius.small)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(52f)
                .pointerInput(widgetItem.id) {
                    var accumulatedDragX = 0f
                    var accumulatedDragY = 0f
                    var gestureStartFrame = draftFrame

                    detectDragGestures(
                        onDragStart = {
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f
                            gestureStartFrame = draftFrame
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDragX += dragAmount.x
                            accumulatedDragY += dragAmount.y
                            updateDraft(
                                applyWidgetTransformHandle(
                                    startFrame = gestureStartFrame,
                                    handle = WidgetTransformHandle.Body,
                                    columnDelta = (accumulatedDragX / cellWidthPx).roundToInt(),
                                    rowDelta = (accumulatedDragY / cellHeightPx).roundToInt(),
                                    maxColumns = gridColumns,
                                    maxRows = maxVisibleRows
                                )
                            )
                        },
                        onDragEnd = { settleDraftAfterGesture() },
                        onDragCancel = { settleDraftAfterGesture() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${draftFrame.span.columns} x ${draftFrame.span.rows}",
                color = frameColor,
                style = MaterialTheme.typography.titleSmall
            )
        }

        WidgetTransformHandleNode(
            alignment = Alignment.TopStart,
            handle = WidgetTransformHandle.TopLeft,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.TopCenter,
            handle = WidgetTransformHandle.Top,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.TopEnd,
            handle = WidgetTransformHandle.TopRight,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.CenterStart,
            handle = WidgetTransformHandle.Left,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.CenterEnd,
            handle = WidgetTransformHandle.Right,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.BottomStart,
            handle = WidgetTransformHandle.BottomLeft,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.BottomCenter,
            handle = WidgetTransformHandle.Bottom,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
        WidgetTransformHandleNode(
            alignment = Alignment.BottomEnd,
            handle = WidgetTransformHandle.BottomRight,
            frameColor = frameColor,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            gridColumns = gridColumns,
            maxVisibleRows = maxVisibleRows,
            draftFrame = draftFrame,
            updateDraft = ::updateDraft,
            settleDraftAfterGesture = ::settleDraftAfterGesture
        )
    }
}

@Composable
private fun BoxScope.WidgetTransformHandleNode(
    alignment: Alignment,
    handle: WidgetTransformHandle,
    frameColor: Color,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    maxVisibleRows: Int,
    draftFrame: WidgetFrame,
    updateDraft: (WidgetFrame) -> Unit,
    settleDraftAfterGesture: () -> Unit
) {
    val latestDraftFrame by rememberUpdatedState(draftFrame)
    val latestUpdateDraft by rememberUpdatedState(updateDraft)
    val latestSettleDraftAfterGesture by rememberUpdatedState(settleDraftAfterGesture)

    Box(
        modifier = Modifier
            .align(alignment)
            .offset(
                x = when (alignment) {
                    Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart -> -Spacing.smallMedium
                    Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd -> Spacing.smallMedium
                    else -> Spacing.none
                },
                y = when (alignment) {
                    Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> -Spacing.smallMedium
                    Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> Spacing.smallMedium
                    else -> Spacing.none
                }
            )
            .size(IconSize.standard)
            .background(
                color = frameColor,
                shape = CircleShape
            )
            .border(
                width = Spacing.hairline,
                color = Color.White.copy(alpha = 0.7f),
                shape = CircleShape
            )
            .zIndex(53f)
            .pointerInput(handle, cellWidthPx, cellHeightPx, gridColumns, maxVisibleRows) {
                var accumulatedDragX = 0f
                var accumulatedDragY = 0f
                var gestureStartFrame = latestDraftFrame

                detectDragGestures(
                    onDragStart = {
                        accumulatedDragX = 0f
                        accumulatedDragY = 0f
                        gestureStartFrame = latestDraftFrame
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount.x
                        accumulatedDragY += dragAmount.y
                        latestUpdateDraft(
                            applyWidgetTransformHandle(
                                startFrame = gestureStartFrame,
                                handle = handle,
                                columnDelta = (accumulatedDragX / cellWidthPx).roundToInt(),
                                rowDelta = (accumulatedDragY / cellHeightPx).roundToInt(),
                                maxColumns = gridColumns,
                                maxRows = maxVisibleRows
                            )
                        )
                    },
                    onDragEnd = { latestSettleDraftAfterGesture() },
                    onDragCancel = { latestSettleDraftAfterGesture() }
                )
            }
    )
}
