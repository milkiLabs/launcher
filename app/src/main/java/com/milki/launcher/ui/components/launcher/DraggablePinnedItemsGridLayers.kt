package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.milki.launcher.domain.reorder.GridReorderEngine
import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.model.homeGridSpan
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropController
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropResult
import com.milki.launcher.ui.interaction.grid.DoubleTapArbiter
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.interaction.grid.animateDragVisuals
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.interaction.grid.detectHomeBackgroundGestures
import com.milki.launcher.ui.components.launcher.widget.HomeScreenWidgetView
import com.milki.launcher.ui.components.launcher.widget.LocalWidgetHost
import com.milki.launcher.ui.components.launcher.widget.PopupWidgetView
import com.milki.launcher.ui.theme.Spacing
import com.milki.launcher.ui.util.windowRect
import kotlin.math.roundToInt
import com.milki.launcher.ui.components.common.buildHomeItemMenuActions
import com.milki.launcher.ui.components.common.launcherCellSemantics
import com.milki.launcher.ui.screens.launcher.FolderActions
import com.milki.launcher.ui.screens.launcher.HomeActions
import com.milki.launcher.ui.screens.launcher.WidgetActions

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
    reorderEngine: GridReorderEngine,
    occupancy: GridOccupancy,
    backgroundGestures: HomeBackgroundGestureBindings,
    home: HomeActions,
    folder: FolderActions,
    widget: WidgetActions,
    hapticLongPress: () -> Unit,
    hapticDragActivate: () -> Unit,
    hapticConfirm: () -> Unit,
    onItemBoundsMeasured: (itemId: String, boundsInWindow: Rect) -> Unit
) {
    val widgetHost = LocalWidgetHost.current
    val latestItems by rememberUpdatedState(items)
    val internalDropHandlers = InternalDropHandlers(
        onItemMove = home.onPinnedItemMove,
        onCreateFolder = folder.onCreateFolder,
        onAddItemToFolder = folder.onAddItemToFolder,
        onMergeFolders = folder.onMergeFolders,
        onConfirmDrop = hapticConfirm
    )

    val backgroundGesturePolicy = interactionController.backgroundGesturePolicy(backgroundGestures)

    // Double-tap state holder, remembered across recompositions AND detector
    // restarts: pointerInput restarts (grid mutations) must not discard a
    // pending tap mid-double-tap.
    val doubleTapArbiter = remember { DoubleTapArbiter() }

    // Structured restart key for the background gesture detector.
    //
    // The previous implementation built a sorted/joined string on every
    // recomposition of this layer even though pointerInput only compares keys
    // for restart eligibility. Computing it via remember means the (cheap)
    // equality comparison still happens each recomposition, but allocation and
    // trigger-set sorting only occur when one of the identity components
    // actually changes. Reading the interaction/drag state here preserves the
    // same composition subscriptions as before, so invalidation behavior is
    // unchanged.
    val backgroundGestureRestartKey = remember(
        items.size,
        interactionController.menuShownForItemId,
        interactionController.externalDragState.isActive,
        dragController.session?.itemId,
        interactionController.widgetTransformSession?.widgetId,
        backgroundGesturePolicy.enabledTriggers
    ) {
        BackgroundGestureRestartKey(
            itemCount = items.size,
            menuShownForItemId = interactionController.menuShownForItemId,
            isExternalDragActive = interactionController.externalDragState.isActive,
            internalDragItemId = dragController.session?.itemId,
            widgetTransformWidgetId = interactionController.widgetTransformSession?.widgetId,
            enabledTriggers = backgroundGesturePolicy.enabledTriggers
        )
    }

    fun showItemMenu(item: HomeItem) {
        if (!interactionController.showItemMenu(item.id)) return
        hapticLongPress()
        home.onPinnedItemLongPress(item)
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
            val action = resolveInternalDropAction(
                draggedItem = item,
                dropPosition = result.to,
                items = latestItems,
                gridColumns = config.columns,
                gridRows = maxVisibleRows,
                reorderEngine = reorderEngine,
                occupancy = occupancy
            )
            applyInternalDropAction(action, internalDropHandlers)
        }
    }

    fun cancelItemDrag() {
        interactionController.cancelInternalDrag()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .detectHomeBackgroundGestures(
                key = backgroundGestureRestartKey,
                items = items,
                occupancy = occupancy,
                layoutMetrics = layoutMetrics,
                policy = backgroundGesturePolicy,
                doubleTapArbiter = doubleTapArbiter,
                gestureThresholdPx = cellHeightPx,
                bindings = backgroundGestures.copy(
                    onEmptyAreaLongPress = { longPressOffset ->
                        hapticLongPress()
                        backgroundGestures.onEmptyAreaLongPress(longPressOffset)
                    }
                )
            )
    ) {
        items.forEach { item ->
            key(item.id, item.position.row, item.position.column, (item as? HomeItem.WidgetItem)?.displayMode) {
                val isBeingDragged = dragController.isDraggingItem(item.id)
                val visuals = animateDragVisuals(isBeingDragged, config)
                val basePosition = dragController.resolveBasePosition(item.id, item.position)
                val widgetItem = item as? HomeItem.WidgetItem
                val isInlineWidget = widgetItem?.displayMode == WidgetDisplayMode.Inline
                val isPopupWidget = widgetItem?.displayMode == WidgetDisplayMode.PopupIcon
                val interactions = resolveItemInteractions(
                    item = item,
                    widgetItem = widgetItem,
                    interactionController = interactionController,
                    home = home,
                    widget = widget,
                    isDragSessionIdle = { dragController.session == null }
                )
                val span = item.homeGridSpan

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
                            onItemBoundsMeasured(item.id, coords.windowRect())
                        }
                        .then(
                            // Inline widgets host live AppWidgetHostViews with
                            // their own accessibility tree - leave those alone.
                            if (!isInlineWidget && interactions.gridGesturesEnabled) {
                                Modifier.launcherCellSemantics(
                                    label = getItemLabel(item),
                                    onTap = interactions.tapAction,
                                    onLongPress = { showItemMenu(item) }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .detectDragGesture(
                            key = "${item.id}-${item.position.row}-${item.position.column}-${span.columns}-${span.rows}",
                            dragThreshold = config.dragThresholdPx,
                            onTap = {
                                if (interactions.gridGesturesEnabled) {
                                    interactions.tapAction?.invoke()
                                }
                            },
                            onSwipeUp = interactions.swipeUpAction?.takeIf { interactions.gridGesturesEnabled },
                            onLongPress = {
                                if (interactions.gridGesturesEnabled) showItemMenu(item)
                            },
                            onLongPressRelease = {
                                if (interactions.gridGesturesEnabled) {
                                    interactionController.updateMenuGestureState(false)
                                }
                            },
                            onDragStart = {
                                if (interactions.gridGesturesEnabled) startItemDrag(item)
                            },
                            onDrag = { change, dragAmount ->
                                if (interactions.gridGesturesEnabled) updateItemDrag(item, change, dragAmount)
                            },
                            onDragEnd = {
                                if (interactions.gridGesturesEnabled) finishItemDrag(item)
                            },
                            onDragCancel = {
                                if (interactions.gridGesturesEnabled) cancelItemDrag()
                            }
                        )
                ) {
                    if (isInlineWidget && widgetHost != null) {
                        HomeScreenWidgetView(
                            appWidgetId = widgetItem.appWidgetId,
                            widthPx = (cellWidthPx * widgetItem.span.columns).toInt(),
                            heightPx = (cellHeightPx * widgetItem.span.rows).toInt(),
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
                    } else {
                        PinnedItemView(item = item)

                        if (interactions.menuKind == ItemMenuKind.Item) {
                            ItemActionMenu(
                                actions = buildHomeItemMenuActions(item),
                                expanded = interactionController.menuShownForItemId == item.id,
                                onDismiss = { interactionController.dismissMenu() },
                                focusable = !interactionController.isMenuGestureActive,
                                onExternalDragStarted = { interactionController.dismissMenu() },
                            )
                        }

                        if (isPopupWidget && widgetHost != null) {
                            PopupWidgetView(
                                expanded = interactionController.widgetPopupShownForItemId == widgetItem.id,
                                appWidgetId = widgetItem.appWidgetId,
                                    widthPx = (cellWidthPx * widgetItem.span.columns).toInt(),
                                heightPx = (cellHeightPx * widgetItem.span.rows).toInt(),
                                width = with(LocalDensity.current) { (cellWidthPx * widgetItem.span.columns).toDp() },
                                height = with(LocalDensity.current) { (cellHeightPx * widgetItem.span.rows).toDp() },
                                onDismiss = interactionController::dismissWidgetPopup
                            )
                        }
                    }

                    if (widgetItem != null && interactions.menuKind == ItemMenuKind.Widget && widgetHost != null) {
                        val isPopupMode = isPopupWidget
                        WidgetContextMenu(
                            expanded = interactionController.menuShownForItemId == item.id,
                            onDismiss = {
                                interactionController.dismissMenu()
                            },
                            focusable = !interactionController.isMenuGestureActive,
                            displayMode = widgetItem.displayMode,
                            onEdit = {
                                if (isPopupMode) interactionController.dismissWidgetPopup()
                                interactionController.startWidgetTransform(widgetItem.id)
                            },
                            onModeAction = {
                                interactionController.dismissMenu()
                                if (isPopupMode) {
                                    interactionController.dismissWidgetPopup()
                                    widget.onExpandPopupWidget(widgetItem.id, maxVisibleRows)
                                } else {
                                    widget.onUpdateWidgetDisplayMode(widgetItem.id, WidgetDisplayMode.PopupIcon)
                                }
                            },
                            onRemove = {
                                interactionController.dismissMenu()
                                if (isPopupMode) interactionController.dismissWidgetPopup()
                                widget.onRemoveWidget(widgetItem.id, widgetItem.appWidgetId)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Identity of the background gesture detector's pointerInput session.
 *
 * Structured replacement for a hand-rolled concatenated string: data-class
 * equality gives identical restart semantics for detectHomeBackgroundGestures
 * without per-recomposition string building or trigger-set sorting.
 */
private data class BackgroundGestureRestartKey(
    val itemCount: Int,
    val menuShownForItemId: String?,
    val isExternalDragActive: Boolean,
    val internalDragItemId: String?,
    val widgetTransformWidgetId: String?,
    val enabledTriggers: Set<LauncherTrigger>
)

/**
 * Per-item gesture and menu strategy, resolved once per item instead of being
 * re-derived inside every gesture callback.
 */
private enum class ItemMenuKind { Item, Widget }

private class ItemInteractions(
    val menuKind: ItemMenuKind,
    val gridGesturesEnabled: Boolean,
    val tapAction: (() -> Unit)?,
    val swipeUpAction: (() -> Unit)?
)

private fun resolveItemInteractions(
    item: HomeItem,
    widgetItem: HomeItem.WidgetItem?,
    interactionController: HomeSurfaceInteractionController,
    home: HomeActions,
    widget: WidgetActions,
    isDragSessionIdle: () -> Boolean
): ItemInteractions {
    return when (widgetItem?.displayMode) {
        WidgetDisplayMode.Inline -> ItemInteractions(
            menuKind = ItemMenuKind.Widget,
            gridGesturesEnabled = false,
            tapAction = null,
            swipeUpAction = null
        )

        WidgetDisplayMode.PopupIcon -> ItemInteractions(
            menuKind = ItemMenuKind.Widget,
            gridGesturesEnabled = true,
            tapAction = { interactionController.showWidgetPopup(item.id) },
            swipeUpAction = { widget.onLaunchWidgetApp(widgetItem.providerPackage) }
        )

        null -> ItemInteractions(
            menuKind = ItemMenuKind.Item,
            gridGesturesEnabled = true,
            tapAction = { if (isDragSessionIdle()) home.onPinnedItemClick(item) },
            swipeUpAction = null
        )
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
    displayMode: WidgetDisplayMode,
    onEdit: () -> Unit,
    onModeAction: () -> Unit,
    onRemove: () -> Unit
) {
    val modeAction = when (displayMode) {
        WidgetDisplayMode.Inline -> MenuAction(
            label = "Show as icon",
            icon = Icons.Filled.Widgets,
            onClick = onModeAction
        )
        WidgetDisplayMode.PopupIcon -> MenuAction(
            label = "Show full widget",
            icon = Icons.Filled.AspectRatio,
            onClick = onModeAction
        )
    }
    val resizeAction = listOf(
        MenuAction(
            label = when (displayMode) {
                WidgetDisplayMode.Inline -> "Resize"
                WidgetDisplayMode.PopupIcon -> "Resize popup"
            },
            icon = Icons.Filled.AspectRatio,
            onClick = onEdit
        )
    )

    ItemActionMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        focusable = focusable,
        actions = resizeAction + listOf(
            modeAction,
            MenuAction(
                label = "Remove",
                icon = Icons.Filled.Delete,
                onClick = onRemove,
                isDestructive = true
            )
        )
    )
}
