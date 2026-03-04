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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.AppDragDropResult
import com.milki.launcher.ui.components.dragdrop.AppExternalDropTargetOverlay
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.components.dragdrop.rememberAppDragDropController
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.animateDragVisuals
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
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

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (basePosition.column * cellWidthPx).roundToInt(),
                                y = (basePosition.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .zIndex(visuals.zIndex)
                        .graphicsLayer {
                            scaleX = visuals.scale
                            scaleY = visuals.scale
                            alpha = visuals.alpha
                        }
                        .detectDragGesture(
                            key = "${item.id}-${item.position.row}-${item.position.column}",
                            dragThreshold = config.dragThresholdPx,
                            onTap = {
                                if (dragController.session == null) {
                                    onItemClick(item)
                                }
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
                                /**
                                 * Finger lifted after long-press without dragging.
                                 * Switch the menu popup to focusable so items become
                                 * tappable.
                                 */
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
                                    // -------------------------------------------------------
                                    // OCCUPANCY CHECK: see if the target cell is taken.
                                    // -------------------------------------------------------
                                    // Find any item occupying 'result.to' that is NOT the
                                    // item currently being dragged.
                                    val occupant = items.find { other ->
                                        other.id != item.id && other.position == result.to
                                    }

                                    if (occupant == null) {
                                        // ---- Empty cell: normal move ----
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onItemMove(result.itemId, result.to)
                                    } else {
                                        // ---- Occupied cell: folder routing ----
                                        when {
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
                                dragController.cancelDrag()
                                isMenuGestureActive = false
                            }
                        )
                ) {
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

        dragController.session?.let { activeSession ->
            val target = dragController.targetPosition ?: activeSession.startPosition
            val previewBaseOffset = layoutMetrics.cellToPixel(activeSession.startPosition)
            val previewOffset = previewBaseOffset + activeSession.currentOffset

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
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
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
                    .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                    .padding(Spacing.smallMedium)
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

        /**
         * External drag highlight — shows where the dragged app will land.
         *
         * This block is only rendered while an external drag (from the search
         * dialog) is active over the home grid. It draws:
         * 1. A rounded, semi-transparent background "glow" to mark the cell.
         * 2. The app icon preview (when the payload is known) so the user
         *    knows exactly which app will be pinned in that cell.
         *
         * WHY SHADOW + BACKGROUND:
         * The platform drag shadow follows the finger, but the user also
         * needs to know which *cell* will receive the drop. A subtle
         * background highlight plus an icon preview makes the target
         * unambiguous, especially on busy wallpapers.
         */
        if (isExternalDragActive) {
            externalDragTargetPosition?.let { targetPosition ->
                val highlightShape = RoundedCornerShape(CornerRadius.medium)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (targetPosition.column * cellWidthPx).roundToInt(),
                                y = (targetPosition.row * cellHeightPx).roundToInt()
                            )
                        }
                        .size(with(LocalDensity.current) { cellWidthPx.toDp() })
                        .padding(Spacing.smallMedium)
                        .zIndex(config.dragZIndex)
                        .shadow(
                            elevation = Spacing.smallMedium,
                            shape = highlightShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = highlightShape
                        )
                        .border(
                            width = Spacing.extraSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = highlightShape
                        )
                        .graphicsLayer {
                            scaleX = config.dropHighlightScale
                            scaleY = config.dropHighlightScale
                        }
                ) {
                    /**
                     * External target highlight mirrors internal drag style:
                     * - If payload app is known: show icon-shaped preview via PinnedItem
                     * - If payload is temporarily unavailable: the background/border
                     *   alone serves as a sufficient drop zone indicator.
                     */
                    val previewItem = externalDragPreviewItem
                    if (previewItem != null) {
                        Box(modifier = Modifier.alpha(config.dropHighlightAlpha)) {
                            PinnedItem(
                                item = previewItem,
                                onClick = {},
                                onLongClick = {},
                                handleLongPress = false
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
                val homeItem = item.toPreviewHomeItem() ?: return@AppExternalDropTargetOverlay false

                // Check if the target cell is occupied by a FolderItem.
                // If so, add the dropped item into the folder instead of pinning it.
                val occupantAtDrop = items.find { it.position == dropPosition }
                if (occupantAtDrop is HomeItem.FolderItem) {
                    // External drop onto a folder → add item to the folder.
                    onAddItemToFolder(occupantAtDrop.id, homeItem)
                } else {
                    // Normal empty-cell or unrecognised occupant → use standard pin logic.
                    onItemDroppedToHome(homeItem, dropPosition)
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
 */
private fun ExternalDragDropItem.toPreviewHomeItem(): HomeItem? {
    return when (this) {
        is ExternalDragItem.App -> HomeItem.PinnedApp.fromAppInfo(appInfo)
        is ExternalDragItem.File -> HomeItem.PinnedFile.fromFileDocument(fileDocument)
        is ExternalDragItem.Contact -> HomeItem.PinnedContact.fromContact(contact)
    }
}
