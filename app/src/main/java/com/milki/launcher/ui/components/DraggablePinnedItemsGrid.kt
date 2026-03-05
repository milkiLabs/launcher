/**
 * DraggablePinnedItemsGrid.kt - Home grid that uses the shared drag-drop core
 *
 * This component renders pinned items in a free-placement grid and delegates drag
 * state handling to the reusable controller in ui/components/dragdrop.
 *
 * WHY THIS VERSION IS MORE REUSABLE:
 * 1) Gesture wiring is delegated to detectDragGesture().
 * 2) Drag lifecycle (start/update/end/cancel) lives in AppDragDropController.
 * 3) Grid math (clamp, target resolution, offset bounds) lives in metrics data.
 *
 * This means other launcher surfaces can reuse the same primitives without
 * duplicating complex pointer + state logic.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.AppDragDropResult
import com.milki.launcher.ui.components.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.components.dragdrop.rememberAppDragDropController
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.animateDragVisuals
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.components.widget.HomeScreenWidgetView
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * DraggablePinnedItemsGrid - Grid that supports long-press menu and drag-reorder.
 *
 * FOLDER SYSTEM INTEGRATION:
 * When a drag ends, the grid checks if the target cell is occupied by another item.
 * Depending on the combination of dragged item and target item types, one of the
 * following folder-related callbacks is invoked instead of [onItemMove]:
 *
 * | Dragged      | Target       | Result                        | Callback                  |
 * |--------------|--------------|-------------------------------|---------------------------|
 * | non-folder   | empty cell   | Normal move                   | [onItemMove]              |
 * | non-folder   | non-folder   | Create new folder             | [onCreateFolder]          |
 * | non-folder   | FolderItem   | Add to folder                 | [onAddItemToFolder]       |
 * | FolderItem   | FolderItem   | Merge folders                 | [onMergeFolders]          |
 * | FolderItem   | non-folder   | Ignored (folder snaps back)   | (no callback)             |
 * | FolderItem   | empty cell   | Normal move                   | [onItemMove]              |
 *
 * @param items Current pinned home items.
 * @param config Grid behavior and visual configuration.
 * @param onItemClick Called when user taps an item.
 * @param onItemLongPress Called when user long-presses without dragging.
 * @param onItemMove Called when user drops an item into an empty new cell.
 * @param onEmptyAreaLongPress Called when user long-presses an unoccupied area of the grid.
 *                              Provides the local touch position so callers can anchor menus.
 * @param onItemDroppedToHome Called when an external drag payload is dropped into an empty cell.
 * @param onCreateFolder Called when a non-folder is dropped onto another non-folder.
 *                        Both items should be removed from the home grid and placed into a new folder.
 * @param onAddItemToFolder Called when a non-folder is dropped onto a FolderItem.
 * @param onMergeFolders Called when a FolderItem is dropped onto another FolderItem.
 * @param onFolderItemExtracted Called when a [ExternalDragItem.FolderChild] is dropped onto an
 *                               EMPTY (or non-folder) cell on the home grid.  The caller should
 *                               call [HomeRepository.extractItemFromFolder].
 *                               Parameters: folderId, childItemId, targetGridPosition.
 * @param onMoveFolderItemToFolder Called when a [ExternalDragItem.FolderChild] is dropped onto a
 *                                  DIFFERENT folder on the home grid.  The caller should call
 *                                  [HomeViewModel.moveItemBetweenFolders] — NOT extractItemFromFolder
 *                                  — to avoid placing the item on the grid at an occupied position.
 *                                  Parameters: sourceFolderId, childItemId, targetFolderId.
 * @param onFolderChildDroppedOnItem Called when a [ExternalDragItem.FolderChild] is dropped onto a
 *                                   NON-FOLDER home grid item (i.e., the drop cell is occupied by a
 *                                   regular icon, not a folder).  Just like dragging two normal
 *                                   icons together, the two items should be combined into a NEW
 *                                   folder at that position.  The source folder's cleanup policy
 *                                   is applied: if it becomes empty it is deleted; if only one
 *                                   child remains the folder is unwrapped.
 *                                   Parameters: sourceFolderId, childItem, occupantItem, atPosition.
 * @param modifier Optional modifier for parent layout.
 */
@Composable
fun DraggablePinnedItemsGrid(
    items: List<HomeItem>,
    config: GridConfig = GridConfig.Default,
    onItemClick: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    onItemMove: (itemId: String, newPosition: GridPosition) -> Unit,
    onEmptyAreaLongPress: (Offset) -> Unit = {},
    onItemDroppedToHome: (item: HomeItem, position: GridPosition) -> Unit = { _, _ -> },
    onCreateFolder: (item1: HomeItem, item2: HomeItem, position: GridPosition) -> Unit = { _, _, _ -> },
    onAddItemToFolder: (folderId: String, item: HomeItem) -> Unit = { _, _ -> },
    onMergeFolders: (sourceFolderId: String, targetFolderId: String) -> Unit = { _, _ -> },
    onFolderItemExtracted: (folderId: String, itemId: String, targetPosition: GridPosition) -> Unit = { _, _, _ -> },
    onMoveFolderItemToFolder: (sourceFolderId: String, itemId: String, targetFolderId: String) -> Unit = { _, _, _ -> },
    /**
     * Called when the user drags a folder child and drops it onto a NON-FOLDER icon in the grid.
     * The two items should be merged into a brand new folder at [atPosition].
     *
     * Parameters:
     *   sourceFolderId – the folder the child was dragged out of.
     *   childItem      – the item that was dragged (the folder child).
     *   occupantItem   – the existing grid icon that was dropped onto.
     *   atPosition     – the grid cell where the new folder should appear (same as occupantItem's position).
     */
    onFolderChildDroppedOnItem: (sourceFolderId: String, childItem: HomeItem, occupantItem: HomeItem, atPosition: GridPosition) -> Unit = { _, _, _, _ -> },
    /** The WidgetHostManager for rendering AppWidgetHostViews inside widget items. Null = widgets won't render. */
    widgetHostManager: WidgetHostManager? = null,
    /** Called when a widget should be removed via context menu. */
    onRemoveWidget: (widgetId: String, appWidgetId: Int) -> Unit = { _, _ -> },
    /** Called when a widget should be resized. newSpan is the updated span. */
    onResizeWidget: (widgetId: String, newSpan: GridSpan) -> Unit = { _, _ -> },
    /**
     * Called when a widget is dragged from the Widget Picker BottomSheet and dropped
     * onto an empty cell on the home grid.  The caller should begin the
     * bind \u2192 configure \u2192 place flow at the given [GridPosition].
     *
     * @param providerInfo  The widget provider that was dropped.
     * @param span          The default grid span.
     * @param dropPosition  The grid cell where the user dropped the widget.
     */
    onWidgetDroppedToHome: (providerInfo: android.appwidget.AppWidgetProviderInfo, span: GridSpan, dropPosition: GridPosition) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dragController = rememberAppDragDropController<HomeItem>(config)

    var menuShownForItemId by remember { mutableStateOf<String?>(null) }

    /**
     * Tracks whether a long-press gesture is actively in progress (finger still
     * down after long-press detected). While true, the dropdown menu popup is
     * rendered with focusable=false so it doesn't steal touch events from the
     * gesture detector — allowing drag detection to continue working even when
     * the popup visually covers the finger position.
     */
    var isMenuGestureActive by remember { mutableStateOf(false) }

    /**
     * When non-null, the widget with this ID is in "resize mode":
     * resize handles are shown around the widget and the user can drag
     * them to change the widget's span.
     */
    var resizingWidgetId by remember { mutableStateOf<String?>(null) }

    var externalDragTargetPosition by remember { mutableStateOf<GridPosition?>(null) }
    var isExternalDragActive by remember { mutableStateOf(false) }
    var externalDragItem by remember { mutableStateOf<ExternalDragDropItem?>(null) }

    /**
     * Stable synthetic home item used only for target highlight visuals.
     *
     * WHY SYNTHETIC ITEM:
     * PinnedItem already defines the launcher's visual language for icons.
     * Reusing it keeps external-drop highlight behavior consistent with
     * internal drag preview/highlight without introducing duplicate UI code.
     */
    val externalDragPreviewItem by remember(externalDragItem) {
        derivedStateOf {
            externalDragItem?.toPreviewHomeItem()
        }
    }

    /**
     * The item currently occupying the internal drag's target cell (if any).
     *
     * WHAT IT IS USED FOR:
     * When the user drags an item over an occupied cell, the drop will trigger
     * a folder operation (create / add-to / merge) rather than a normal move.
     * This derived value lets the drop-highlight code switch between:
     *   - Normal move highlight (target empty)
     *   - Folder-join highlight (target occupied by a compatible item)
     *   - No highlight (invalid drop — folder dragged onto non-folder)
     *
     * WHY derivedStateOf:
     * [dragController.targetPosition] and [items] are both Compose state.
     * derivedStateOf() caches the result and only recomputes when either
     * dependency changes, avoiding unnecessary recompositions.
     */
    val dragTargetOccupant by remember {
        derivedStateOf {
            val session = dragController.session ?: return@derivedStateOf null
            val target = dragController.targetPosition ?: return@derivedStateOf null
            // Find a different item sitting at the current drag target cell.
            items.find { it.id != session.itemId && it.position == target }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val cellWidthPx = with(LocalDensity.current) { maxWidth.toPx() / config.columns }
        val cellHeightPx = cellWidthPx
        val gridHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        val maxVisibleRows = (gridHeightPx / cellHeightPx).toInt().coerceAtLeast(1)

        val layoutMetrics = remember(
            cellWidthPx,
            cellHeightPx,
            config.columns,
            maxVisibleRows
        ) {
            AppDragDropLayoutMetrics(
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                columns = config.columns,
                rows = maxVisibleRows
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(items, cellWidthPx, cellHeightPx, config.columns, maxVisibleRows) {
                    detectTapGestures(
                        onLongPress = { longPressOffset ->
                            if (isExternalDragActive) return@detectTapGestures
                            if (dragController.session != null) return@detectTapGestures

                            val pressedCell = layoutMetrics.pixelToCell(longPressOffset)
                            val isCellOccupied = items.any { it.position == pressedCell }

                            if (!isCellOccupied) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
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

                // Determine the span — widgets occupy multiple cells; everything else is 1×1.
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
                        .padding(Spacing.smallMedium)
                        .zIndex(visuals.zIndex)
                        .graphicsLayer {
                            scaleX = visuals.scale
                            scaleY = visuals.scale
                            alpha = visuals.alpha
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
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuShownForItemId = item.id
                                    isMenuGestureActive = true
                                    onItemLongPress(item)
                                }
                            },
                            onLongPressRelease = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                /**
                                 * Finger lifted after long-press without dragging.
                                 * Switch the menu popup to focusable so items become
                                 * tappable.
                                 */
                                isMenuGestureActive = false
                            },
                            onDragStart = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                menuShownForItemId = null
                                isMenuGestureActive = false
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
                                    // -------------------------------------------------------
                                    // SPAN-AWARE OCCUPANCY CHECK
                                    // -------------------------------------------------------
                                    // Build the set of cells the dragged item would occupy
                                    // at the target position, then check for overlap with
                                    // any other item's occupied cells.
                                    val draggedSpan = (item as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                                    val draggedTargetCells = draggedSpan.occupiedPositions(result.to)

                                    // Find the first other item whose cells overlap any of
                                    // the dragged item's target cells.
                                    val occupant = items.find { other ->
                                        if (other.id == item.id) return@find false
                                        val otherSpan = (other as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                                        val otherCells = otherSpan.occupiedPositions(other.position)
                                        otherCells.any { it in draggedTargetCells }
                                    }

                                    if (occupant == null) {
                                        // ---- Empty cell: normal move ----
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onItemMove(result.itemId, result.to)
                                    } else {
                                        // ---- Occupied cell: folder routing ----
                                        // Widgets cannot participate in folder operations.
                                        // If either the dragged item or the occupant is a
                                        // widget, treat the drop as invalid (snap back).
                                        when {
                                            item is HomeItem.WidgetItem || occupant is HomeItem.WidgetItem -> {
                                                // Widget on anything, or anything on widget → invalid drop.
                                            }
                                            item is HomeItem.FolderItem && occupant is HomeItem.FolderItem -> {
                                                // Two folders dropped on each other → merge them.
                                                // The dragged folder (source) is absorbed into the target.
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                onMergeFolders(item.id, occupant.id)
                                            }
                                            item is HomeItem.FolderItem -> {
                                                // A folder was dragged onto a non-folder item.
                                                // This is an INVALID drop — doing nothing causes
                                                // the folder to snap back to its original position
                                                // naturally (AppDragDropController is already reset
                                                // and we haven't called onItemMove, so the grid
                                                // renders the folder at its stored position).
                                            }
                                            occupant is HomeItem.FolderItem -> {
                                                // A non-folder icon was dropped onto a folder.
                                                // Add the dragged item into the folder's children.
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                onAddItemToFolder(occupant.id, item)
                                            }
                                            else -> {
                                                // Two non-folder icons dropped on top of each other.
                                                // Create a new folder containing both items.
                                                // The new folder appears at the target item's position.
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                onCreateFolder(item, occupant, occupant.position)
                                            }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (item is HomeItem.WidgetItem) return@detectDragGesture
                                dragController.cancelDrag()
                                isMenuGestureActive = false
                            }
                        )
                ) {
                    // Widget items render the actual AppWidgetHostView;
                    // all other item types render the standard PinnedItem icon+label.
                    if (item is HomeItem.WidgetItem && widgetHostManager != null) {
                        HomeScreenWidgetView(
                            appWidgetId = item.appWidgetId,
                            widgetHostManager = widgetHostManager,
                            widthPx = (cellWidthPx * span.columns).toInt(),
                            heightPx = (cellHeightPx * span.rows).toInt(),
                            modifier = Modifier.fillMaxSize()
                        )

                        // Gesture layer above the Android widget view.
                        //
                        // WHY THIS EXISTS:
                        // We want widget interactions to follow the same pattern as
                        // normal home icons:
                        // 1) long-press shows dropdown menu
                        // 2) long-press + drag closes menu and starts drag
                        //
                        // The underlying AppWidgetHostView can consume touch events,
                        // so we place a transparent Compose gesture surface on top to
                        // guarantee consistent drag/menu behavior.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .detectDragGesture(
                                    key = "widget-gesture-${item.id}-${item.position.row}-${item.position.column}-${span.columns}-${span.rows}",
                                    dragThreshold = config.dragThresholdPx,
                                    onTap = {
                                        // Widgets do not launch via launcher tap.
                                        // Their internal click behavior (if needed)
                                        // comes from RemoteViews PendingIntents.
                                    },
                                    onLongPress = {
                                        if (dragController.session == null) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuShownForItemId = item.id
                                            isMenuGestureActive = true
                                            onItemLongPress(item)
                                        }
                                    },
                                    onLongPressRelease = {
                                        isMenuGestureActive = false
                                    },
                                    onDragStart = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        menuShownForItemId = null
                                        isMenuGestureActive = false
                                        dragController.startDrag(
                                            item = item,
                                            itemId = item.id,
                                            startPosition = item.position
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (dragController.isDraggingItem(item.id)) {
                                            change.consume()
                                            dragController.updateDrag(dragAmount, layoutMetrics)
                                        }
                                    },
                                    onDragEnd = {
                                        val result = dragController.endDrag(layoutMetrics)
                                        if (result is AppDragDropResult.Moved && result.itemId == item.id) {
                                            // Span-aware occupancy check for widget moves.
                                            val draggedSpan = item.span
                                            val draggedTargetCells = draggedSpan.occupiedPositions(result.to)

                                            val occupant = items.find { other ->
                                                if (other.id == item.id) return@find false
                                                val otherSpan = (other as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                                                val otherCells = otherSpan.occupiedPositions(other.position)
                                                otherCells.any { it in draggedTargetCells }
                                            }

                                            if (occupant == null) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                                onItemMove(result.itemId, result.to)
                                            }
                                            // If occupied, widget drop is invalid and snaps back.
                                        }
                                    },
                                    onDragCancel = {
                                        dragController.cancelDrag()
                                        isMenuGestureActive = false
                                    }
                                )
                        )

                        // Widget-specific context menu (shown on long-press).
                        WidgetContextMenu(
                            expanded = menuShownForItemId == item.id,
                            onDismiss = {
                                menuShownForItemId = null
                                isMenuGestureActive = false
                            },
                            focusable = !isMenuGestureActive,
                            onResize = {
                                menuShownForItemId = null
                                isMenuGestureActive = false
                                resizingWidgetId = item.id
                            },
                            onRemove = {
                                menuShownForItemId = null
                                isMenuGestureActive = false
                                onRemoveWidget(item.id, item.appWidgetId)
                            }
                        )
                    } else {
                        PinnedItem(
                            item = item,
                            onClick = {},
                            onLongClick = {},
                            handleLongPress = false,
                            showMenu = menuShownForItemId == item.id,
                            onMenuDismiss = { menuShownForItemId = null; isMenuGestureActive = false },
                            menuFocusable = !isMenuGestureActive
                        )
                    }
                }
            }
        }

        // ────────────────────────────────────────────────────────────────────
        // WIDGET RESIZE OVERLAY
        // ────────────────────────────────────────────────────────────────────
        // When a widget's "Resize" context menu action is tapped, we enter resize
        // mode for that widget. A scrim covers the grid and a resize frame with
        // drag handles appears around the widget.
        resizingWidgetId?.let { widgetId ->
            val widgetItem = items.filterIsInstance<HomeItem.WidgetItem>()
                .find { it.id == widgetId }

            if (widgetItem != null) {
                WidgetResizeOverlay(
                    widgetItem = widgetItem,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    gridColumns = config.columns,
                    items = items,
                    onConfirmResize = { newSpan ->
                        resizingWidgetId = null
                        onResizeWidget(widgetItem.id, newSpan)
                    },
                    onDismiss = { resizingWidgetId = null }
                )
            } else {
                // Widget no longer exists — exit resize mode.
                resizingWidgetId = null
            }
        }

        dragController.session?.let { activeSession ->
            val target = dragController.targetPosition ?: activeSession.startPosition
            val previewBaseOffset = layoutMetrics.cellToPixel(activeSession.startPosition)
            val previewOffset = previewBaseOffset + activeSession.currentOffset
            val draggedWidgetSpan = (activeSession.item as? HomeItem.WidgetItem)?.span
            val previewSpan = draggedWidgetSpan ?: GridSpan.SINGLE

            // ----------------------------------------------------------------
            // DROP TARGET HIGHLIGHT
            //
            // Three visual states depending on what occupies the target cell:
            //
            // 1. Empty cell (dragTargetOccupant == null):
            //    A semi-transparent ghost of the dragged item, indicating that
            //    releasing here will MOVE the item to this cell.
            //
            // 2. Compatible occupied cell (two non-folders OR folder+folder OR
            //    non-folder+folder): a pulsing ring highlight that shows the
            //    two items will be joined. The ghost of the dragged item is
            //    replaced by a scaled-up version of the target item with a
            //    primary-colored ring border — visually communicating "merge".
            //
            // 3. Invalid drop (folder dragged onto non-folder):
            //    No highlight is rendered at the target cell, leaving the
            //    target item's appearance unchanged. This signals "nothing
            //    will happen" to the user.
            // ----------------------------------------------------------------

            val currentDraggedItem = activeSession.item
            val isDraggingFolder = currentDraggedItem is HomeItem.FolderItem
            val isInvalidDrop = isDraggingFolder && dragTargetOccupant != null
                && dragTargetOccupant !is HomeItem.FolderItem

            if (!isInvalidDrop) {
                // Show a highlight at the target cell (empty move OR folder-join).
                val isFolderMerge = dragTargetOccupant != null
                val highlightColor = if (isFolderMerge) {
                    // Folder-join highlight uses the secondary color to distinguish
                    // it from the primary-color empty-cell move highlight.
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val highlightShape = RoundedCornerShape(CornerRadius.medium)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (target.column * cellWidthPx).roundToInt(),
                                y = (target.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(
                            width = with(LocalDensity.current) { (cellWidthPx * previewSpan.columns).toDp() },
                            height = with(LocalDensity.current) { (cellHeightPx * previewSpan.rows).toDp() }
                        )
                        .padding(Spacing.smallMedium)
                        .alpha(config.dropHighlightAlpha)
                        .graphicsLayer {
                            // For folder-join, scale up slightly more than a normal
                            // hover to reinforce the "the two will combine" metaphor.
                            val scale = if (isFolderMerge) config.dropHighlightScale * 1.05f
                                else config.dropHighlightScale
                            scaleX = scale
                            scaleY = scale
                        }
                        // Draw a subtle ring for folder-join to make the target
                        // cell stand out even when alpha is low.
                        .then(
                            if (isFolderMerge) Modifier.border(
                                width = Spacing.extraSmall,
                                color = highlightColor.copy(alpha = 0.5f),
                                shape = highlightShape
                            ) else Modifier
                        )
                ) {
                    if (activeSession.item is HomeItem.WidgetItem) {
                        // Widget internal drag highlight should reflect the full span.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${previewSpan.columns} × ${previewSpan.rows}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        PinnedItem(
                            // For the ghost: show the occupant item (folder-join context)
                            // or the dragged item itself (empty-cell move context).
                            item = dragTargetOccupant ?: activeSession.item,
                            onClick = {},
                            onLongClick = {},
                            handleLongPress = false
                        )
                    }
                }
            }

            // The floating preview always follows the finger, regardless of
            // whether the drop is valid or not, so the user always has visual
            // feedback on what they are dragging.
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
                    .padding(Spacing.smallMedium)
                    .zIndex(config.previewZIndex)
                    .graphicsLayer {
                        scaleX = config.previewScale
                        scaleY = config.previewScale
                        alpha = config.previewAlpha
                        shadowElevation = config.shadowElevation
                    }
            ) {
                if (activeSession.item is HomeItem.WidgetItem) {
                    // Floating internal drag preview for widgets uses span label so
                    // users see the real occupied size while moving.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(CornerRadius.medium)
                            )
                            .border(
                                width = Spacing.extraSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(CornerRadius.medium)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${previewSpan.columns} × ${previewSpan.rows}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                } else {
                    PinnedItem(
                        item = activeSession.item,
                        onClick = {},
                        onLongClick = {},
                        handleLongPress = false
                    )
                }
            }
        }

        /**
         * External drag highlight — shows where the dragged item will land.
         *
         * This block is only rendered while an external drag (from the search
         * dialog or widget picker) is active over the home grid. It draws:
         * 1. A rounded, semi-transparent background "glow" to mark the cells.
         * 2. An icon preview (when the payload is known) so the user
         *    knows exactly which item will be pinned in those cells.
         *
         * SPAN-AWARE:
         * For widgets, the highlight covers the full span (e.g. 4×2 cells).
         * For regular items (apps, files, contacts) the highlight is 1×1.
         *
         * COLLISION FEEDBACK:
         * When a widget's span would overlap existing items, the highlight
         * border turns red to signal an invalid drop location.
         *
         * WHY SHADOW + BACKGROUND:
         * The platform drag shadow follows the finger, but the user also
         * needs to know which *cells* will receive the drop. A subtle
         * background highlight plus an icon preview makes the target
         * unambiguous, especially on busy wallpapers.
         */
        if (isExternalDragActive) {
            externalDragTargetPosition?.let { targetPosition ->
                // Wait until payload resolves; rendering before that would show
                // a misleading 1×1 fallback highlight for large widgets.
                val currentExternalItem = externalDragItem ?: return@let

                // Determine the span of the dragged item.
                // Widgets carry their span in the ExternalDragItem.Widget payload;
                // everything else is a 1×1 single cell item.
                val rawDragSpan = (currentExternalItem as? ExternalDragItem.Widget)?.span
                    ?: GridSpan.SINGLE

                // Use the exact same default-size heuristic as final drop so
                // hover preview matches placement size.
                val dragSpan = normalizeWidgetSpanForHomeGrid(
                    rawSpan = rawDragSpan,
                    gridColumns = config.columns
                )

                // Clamp the target position so that the full span stays within
                // the grid bounds. Without this, a 4×2 widget dragged to the
                // right edge would render partially off-screen.
                val clampedTarget = GridPosition(
                    row = targetPosition.row.coerceIn(0, (maxVisibleRows - dragSpan.rows).coerceAtLeast(0)),
                    column = targetPosition.column.coerceIn(0, (config.columns - dragSpan.columns).coerceAtLeast(0))
                )

                // Check whether the span area collides with existing items.
                // This determines the highlight color (green-ish vs red).
                val spanCells = dragSpan.occupiedPositions(clampedTarget)
                val hasCollision = items.any { existingItem ->
                    val existingSpan = (existingItem as? HomeItem.WidgetItem)?.span ?: GridSpan.SINGLE
                    val existingCells = existingSpan.occupiedPositions(existingItem.position)
                    existingCells.any { it in spanCells }
                }

                val highlightColor = if (hasCollision) {
                    // Red highlight to indicate an invalid (overlapping) drop position.
                    Color(0xFFFF5252)
                } else {
                    MaterialTheme.colorScheme.primary
                }

                val highlightShape = RoundedCornerShape(CornerRadius.medium)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (clampedTarget.column * cellWidthPx).roundToInt(),
                                y = (clampedTarget.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(
                            width = with(LocalDensity.current) { (cellWidthPx * dragSpan.columns).toDp() },
                            height = with(LocalDensity.current) { (cellHeightPx * dragSpan.rows).toDp() }
                        )
                        .padding(Spacing.smallMedium)
                        .zIndex(config.dragZIndex)
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
                            scaleX = config.dropHighlightScale
                            scaleY = config.dropHighlightScale
                        }
                ) {
                    /**
                     * External target highlight mirrors internal drag style:
                     * - If payload is a regular item with a known icon: show preview via PinnedItem
                     * - If payload is a widget: show a centered size label (e.g. "4×2") since
                     *   we don't have a HomeItem yet and the widget preview is already in
                     *   the platform drag shadow following the finger.
                     * - If payload is temporarily unavailable: the background/border
                     *   alone serves as a sufficient drop zone indicator.
                     */
                    val previewItem = currentExternalItem.toPreviewHomeItem()
                    if (previewItem != null) {
                        Box(modifier = Modifier.alpha(config.dropHighlightAlpha)) {
                            PinnedItem(
                                item = previewItem,
                                onClick = {},
                                onLongClick = {},
                                handleLongPress = false
                            )
                        }
                    } else if (currentExternalItem is ExternalDragItem.Widget) {
                        // For widgets, show the span dimensions as a centered label
                        // inside the multi-cell highlight rectangle.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${dragSpan.columns} × ${dragSpan.rows}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        AppExternalDropTargetOverlay(
            onDragStarted = {
                isExternalDragActive = true
                externalDragTargetPosition = null
                externalDragItem = null
            },
            onDragMoved = { localOffset, item ->
                externalDragTargetPosition = layoutMetrics.pixelToCell(localOffset)
                if (item != null) {
                    externalDragItem = item
                }
            },
            onDragEnded = {
                isExternalDragActive = false
                externalDragTargetPosition = null
                externalDragItem = null
            },
            onItemDropped = { item, localOffset ->
                /**
                 * IMPORTANT DROP-TARGET RULE:
                 * Prefer the last hovered target position when available.
                 *
                 * WHY:
                 * During cross-window drag (dialog -> activity), some Android
                 * variants can report less-stable ACTION_DROP coordinates than
                 * ACTION_DRAG_LOCATION. The hover highlight is driven by the
                 * location stream, so using that as primary source ensures the
                 * final pinned position matches what user saw highlighted.
                 *
                 * Fallback to ACTION_DROP coordinates when no hover sample
                 * exists (for extremely quick drop interactions).
                 */
                val dropPosition = if (isExternalDragActive && externalDragTargetPosition != null) {
                    externalDragTargetPosition!!
                } else {
                    layoutMetrics.pixelToCell(localOffset)
                }

                externalDragTargetPosition = dropPosition
                externalDragItem = item
                val homeItem = item.toPreviewHomeItem()

                // ----------------------------------------------------------------
                // FOLDER-CHILD DROP: item dragged out of a folder popup
                // ----------------------------------------------------------------
                // FolderChild payloads require special routing depending on what
                // occupies the drop cell.  Handle this BEFORE the toPreviewHomeItem
                // null-check so we can route even when toPreviewHomeItem would
                // return the raw childItem.
                if (item is ExternalDragItem.FolderChild) {
                    val occupantAtDrop = items.find { it.position == dropPosition }

                    when {
                        occupantAtDrop is HomeItem.FolderItem
                                && occupantAtDrop.id == item.folderId -> {
                            // ---- Dropped back onto the SAME folder it came from ----
                            // The item is still inside the folder (extraction only happens
                            // on successful drop to a different location).  Ignore this
                            // drop entirely so no duplicate or out-of-place icon appears.
                            // Return true to mark the event as consumed.
                            return@AppExternalDropTargetOverlay true
                        }
                        occupantAtDrop is HomeItem.FolderItem -> {
                            // ---- Dropped onto a DIFFERENT folder ----
                            // Use the dedicated move-between-folders path (NOT extract+add)
                            // because extractItemFromFolder would try to place the item on
                            // the grid at dropPosition, which is already occupied by the
                            // target folder, causing a position collision / duplication.
                            // moveItemBetweenFolders removes from source and adds to target
                            // entirely within folder children lists — never touches the grid.
                            onMoveFolderItemToFolder(
                                item.folderId,
                                item.childItem.id,
                                occupantAtDrop.id
                            )
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            return@AppExternalDropTargetOverlay true
                        }
                        else -> {
                            if (occupantAtDrop != null) {
                                // ---- Dropped onto a NON-FOLDER occupied cell ----
                                // The user dragged a folder child and released it directly
                                // on top of another home icon that is NOT a folder.
                                //
                                // Desired behaviour (same as dragging two normal grid icons
                                // together): combine the two items into a brand new folder
                                // that appears at the drop position.
                                //
                                // [onFolderChildDroppedOnItem] is responsible for:
                                //   1. Removing childItem from its source folder
                                //      (with cleanup policy — delete/unwrap if needed).
                                //   2. Creating a new folder at dropPosition that contains
                                //      both childItem and occupantItem.
                                onFolderChildDroppedOnItem(
                                    item.folderId,    // source folder the child came from
                                    item.childItem,   // the dragged item (folder child)
                                    occupantAtDrop,   // the existing grid icon that was dropped on
                                    dropPosition      // cell where the new folder should appear
                                )
                            } else {
                                // ---- Dropped onto an EMPTY cell ----
                                // Standard extract: remove the child from its folder and place
                                // it as a standalone icon at the empty dropPosition.
                                onFolderItemExtracted(item.folderId, item.childItem.id, dropPosition)
                            }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            return@AppExternalDropTargetOverlay true
                        }
                    }
                }

                // ----------------------------------------------------------------
                // WIDGET DROP: widget dragged from the Widget Picker BottomSheet
                // ----------------------------------------------------------------
                // Widget payloads carry AppWidgetProviderInfo + GridSpan but don't
                // have a HomeItem representation yet (that's created after the
                // bind → configure flow). Handle this before the toPreviewHomeItem
                // null-check because toPreviewHomeItem returns null for widgets.
                if (item is ExternalDragItem.Widget) {
                    // ----------------------------------------------------------------
                    // SPAN NORMALIZATION FOR GRID CONSTRAINTS
                    // ----------------------------------------------------------------
                    // Some widget providers report very large default spans (for example
                    // 7×7). Our launcher grid currently has a fixed column count
                    // (GridConfig.columns = 4 by default), so any span wider than the
                    // grid cannot be placed as-is.
                    //
                    // Without normalization, HomeRepository.addWidget() rejects placement
                    // because isSpanFree() correctly detects that `column + span.columns`
                    // exceeds grid width. That manifests as "drop does nothing".
                    //
                    // We normalize here so the drop pipeline stays robust:
                    // - columns: clamp into [1, config.columns]
                    // - rows: keep at least 1 (rows can extend vertically in this grid)
                    //
                    // EXAMPLE on 4-column grid:
                    // - provider span 7×7  -> normalized to 4×7
                    // - provider span 5×2  -> normalized to 4×2
                    // - provider span 2×6  -> stays 2×6
                    //
                    // This keeps widget placement predictable for oversized providers
                    // instead of failing silently.
                    val normalizedSpan = normalizeWidgetSpanForHomeGrid(
                        rawSpan = item.span,
                        gridColumns = config.columns
                    )

                    // Clamp the drop position so the full span fits within the grid,
                    // matching the clamping applied to the visual highlight above.
                    val clampedDrop = GridPosition(
                        row = dropPosition.row.coerceIn(0, (maxVisibleRows - normalizedSpan.rows).coerceAtLeast(0)),
                        column = dropPosition.column.coerceIn(0, (config.columns - normalizedSpan.columns).coerceAtLeast(0))
                    )

                    // Check that the drop cell and the widget's full span area are
                    // not occupied by existing items.
                    val occupiedCells = mutableSetOf<GridPosition>()
                    for (existingItem in items) {
                        if (existingItem is HomeItem.WidgetItem) {
                            occupiedCells.addAll(existingItem.span.occupiedPositions(existingItem.position))
                        } else {
                            occupiedCells.add(existingItem.position)
                        }
                    }
                    val spanCells = normalizedSpan.occupiedPositions(clampedDrop)
                    val hasCollision = spanCells.any { it in occupiedCells }

                    if (!hasCollision) {
                        // All cells free — resolve provider info and proceed with placement.
                        //
                        // WHY RESOLUTION IS NEEDED:
                        // On some drag paths (especially cross-window/global drags),
                        // DragEvent.localState may be unavailable on the drop target.
                        // In that case, ExternalDragPayloadCodec decodes a Widget payload
                        // from ClipData with providerInfo = null plus a stable
                        // providerComponent. We resolve providerInfo here from
                        // WidgetHostManager's installed providers.
                        val resolvedProviderInfo = item.providerInfo
                            ?: widgetHostManager
                                ?.getInstalledProviders()
                                ?.firstOrNull { provider -> provider.provider == item.providerComponent }

                        if (resolvedProviderInfo != null) {
                            onWidgetDroppedToHome(resolvedProviderInfo, normalizedSpan, clampedDrop)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    }
                    // Whether or not placement succeeded, consume the drop event.
                    return@AppExternalDropTargetOverlay true
                }

                if (homeItem == null) return@AppExternalDropTargetOverlay false

                // Check if the target cell is occupied.
                val occupantAtDrop = items.find { it.position == dropPosition }
                when {
                    occupantAtDrop is HomeItem.FolderItem -> {
                        // Dropped onto an existing folder → add item into the folder.
                        onAddItemToFolder(occupantAtDrop.id, homeItem)
                    }
                    occupantAtDrop != null -> {
                        // ---- Dropped onto a NON-FOLDER occupied cell ----
                        // The search-dialog item is being dropped directly on top of an
                        // existing home icon.  This should behave exactly the same as
                        // dragging two home icons together: combine them into a new folder
                        // at that grid cell.
                        //
                        // [onCreateFolder] removes both items from the flat pinnedItems
                        // list and creates a new FolderItem containing them both at
                        // [dropPosition].
                        onCreateFolder(homeItem, occupantAtDrop, dropPosition)
                    }
                    else -> {
                        // Empty cell → standard pin / move logic.
                        onItemDroppedToHome(homeItem, dropPosition)
                    }
                }
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                true
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(config.previewZIndex + 1f)
        )
        }
    }
}

/**
 * Maps an external drag payload to the equivalent HomeItem preview/persisted model.
 *
 * [ExternalDragItem.FolderChild] carries the raw [HomeItem] inside it, so we
 * return [ExternalDragItem.FolderChild.childItem] directly.  This makes the
 * external-drag visual highlight for a folder-child drag show the correct icon
 * while it hovers over the home grid.
 */
private fun ExternalDragDropItem.toPreviewHomeItem(): HomeItem? {
    return when (this) {
        is ExternalDragItem.App     -> HomeItem.PinnedApp.fromAppInfo(appInfo)
        is ExternalDragItem.File    -> HomeItem.PinnedFile.fromFileDocument(fileDocument)
        is ExternalDragItem.Contact -> HomeItem.PinnedContact.fromContact(contact)
        is ExternalDragItem.FolderChild -> childItem
        // Widgets don't have a HomeItem representation until they go through the
        // bind/configure flow, so we return null. The drop handler checks for the
        // Widget type separately before this null check.
        is ExternalDragItem.Widget  -> null
    }
}

/**
 * Normalizes provider-reported widget span to a practical default for this launcher.
 *
 * WHAT LAUNCHERS TYPICALLY DO:
 * - Never exceed workspace column count.
 * - Avoid extremely tall default widgets that consume most of the screen.
 * - Keep aspect ratio *roughly* consistent when width must be reduced.
 *
 * HEURISTIC USED HERE:
 * 1) Clamp columns to grid width.
 * 2) If columns were reduced, scale rows proportionally.
 * 3) Cap rows to a reasonable phone default.
 *
 * EXAMPLES on a 4-column grid:
 * - 7×7 -> 4×4
 * - 5×3 -> 4×2
 * - 2×7 -> 2×4
 */
private fun normalizeWidgetSpanForHomeGrid(
    rawSpan: GridSpan,
    gridColumns: Int,
    maxDefaultRows: Int = 4
): GridSpan {
    val safeRawColumns = rawSpan.columns.coerceAtLeast(1)
    val safeRawRows = rawSpan.rows.coerceAtLeast(1)

    val normalizedColumns = safeRawColumns.coerceIn(1, gridColumns)

    // If we had to shrink width, shrink height proportionally to keep a
    // reasonable default aspect ratio for first placement.
    val widthScale = normalizedColumns.toFloat() / safeRawColumns.toFloat()
    val scaledRows = (safeRawRows * widthScale).roundToInt().coerceAtLeast(1)

    val normalizedRows = scaledRows.coerceIn(1, maxDefaultRows)

    return GridSpan(columns = normalizedColumns, rows = normalizedRows)
}

// ========================================================================
// WIDGET CONTEXT MENU
// ========================================================================

/**
 * Context menu shown on long-press of a widget item.
 *
 * Provides two widget-specific actions:
 * - "Resize" — enters resize mode with drag handles
 * - "Remove" — deletes the widget from the home screen
 *
 * @param expanded   Whether the menu is currently visible
 * @param onDismiss  Called when the menu should close (back press, outside tap)
 * @param focusable  Whether the menu should be focusable (false during gesture to
 *                   prevent stealing touch events from the drag detector)
 * @param onResize   Called when "Resize" is tapped
 * @param onRemove   Called when "Remove" is tapped
 */
@Composable
private fun WidgetContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean,
    onResize: () -> Unit,
    onRemove: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

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
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
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
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onRemove()
            }
        )
    }
}

// ========================================================================
// WIDGET RESIZE OVERLAY
// ========================================================================

/**
 * Full-grid overlay that allows the user to resize a widget by dragging
 * an edge/corner handle.
 *
 * VISUAL DESIGN:
 * - A dark scrim covers the entire grid to dim non-widget content.
 * - The widget's current bounds are outlined with a primary-colored border.
 * - A small circular drag handle sits at the bottom-right corner.
 * - Dragging the handle snaps to cell boundaries, changing the widget's span.
 * - Tapping the scrim (outside the widget) confirms and exits resize mode.
 *
 * CONSTRAINTS:
 * - Minimum span: 1×1
 * - Maximum span: limited by grid edges and other items' occupied cells
 *
 * @param widgetItem    The widget being resized
 * @param cellWidthPx   Pixel width of one grid cell
 * @param cellHeightPx  Pixel height of one grid cell
 * @param gridColumns   Number of columns in the grid
 * @param items         All current home items (for collision detection)
 * @param onConfirmResize Called with the new span when resize is confirmed
 * @param onDismiss     Called to exit resize mode without change
 */
@Composable
private fun WidgetResizeOverlay(
    widgetItem: HomeItem.WidgetItem,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    items: List<HomeItem>,
    onConfirmResize: (GridSpan) -> Unit,
    onDismiss: () -> Unit
) {
    // Track the current span being previewed while drag handle is moved.
    var previewSpan by remember(widgetItem.id) { mutableStateOf(widgetItem.span) }

    // Build the occupancy map excluding the widget being resized.
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

    // Scrim — tapping it confirms the resize and exits.
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

    // Resize frame — positioned at the widget's origin, sized to previewSpan.
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
        // Drag handle at bottom-right corner.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = with(LocalDensity.current) { Spacing.smallMedium },
                    y = with(LocalDensity.current) { Spacing.smallMedium }
                )
                .size(IconSize.standard)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .zIndex(52f)
                .pointerInput(widgetItem.id) {
                    // Detect drag on the handle to resize.
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

                            // Snap to cell boundaries.
                            val newCols = (widgetItem.span.columns + (accumulatedDragX / cellWidthPx).roundToInt())
                                .coerceIn(1, gridColumns - widgetItem.position.column)
                            val newRows = (widgetItem.span.rows + (accumulatedDragY / cellHeightPx).roundToInt())
                                .coerceAtLeast(1)

                            val candidateSpan = GridSpan(columns = newCols, rows = newRows)

                            // Check that the new span doesn't overlap any other items.
                            val candidateCells = candidateSpan.occupiedPositions(widgetItem.position)
                            val hasCollision = candidateCells.any { it in occupiedCells }

                            if (!hasCollision) {
                                previewSpan = candidateSpan
                            }
                        },
                        onDragEnd = {
                            // Drag released — keep the previewed span, user taps scrim to confirm.
                        }
                    )
                }
        )
    }
}
