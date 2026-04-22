package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropController
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropResult
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.interaction.grid.animateDragVisuals
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.interaction.grid.detectHomeBackgroundGestures
import com.milki.launcher.ui.components.launcher.widget.HomeScreenWidgetView
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
                key = "background-${items.size}-${interactionController.menuShownForItemId ?: "none"}-${interactionController.externalDragState.isActive}-${dragController.session?.itemId ?: "idle"}-${interactionController.widgetTransformSession?.widgetId ?: "none"}-${
                    backgroundGesturePolicy.enabledTriggers.sortedBy { it.name }
                        .joinToString(separator = ",") { it.name }
                }",
                items = items,
                layoutMetrics = layoutMetrics,
                policy = backgroundGesturePolicy,
                bindings = backgroundGestures.copy(
                    onEmptyAreaLongPress = { longPressOffset ->
                        hapticLongPress()
                        backgroundGestures.onEmptyAreaLongPress(longPressOffset)
                    }
                ),
                gestureThresholdPx = cellHeightPx
            )
    ) {
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
                        .padding(Spacing.none)
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
                            handleLongPress = false,
                            compactLayout = true,
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
