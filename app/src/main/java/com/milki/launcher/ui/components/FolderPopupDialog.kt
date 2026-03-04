/**
 * FolderPopupDialog.kt - Popup dialog for viewing and managing a folder's contents
 *
 * This composable is shown when the user taps a [HomeItem.FolderItem] on the home screen.
 * It overlays the home grid as a centered card and lets the user:
 *
 *   1. VIEW: See all icons inside the folder in a 3-column scrollable grid.
 *   2. TAP: Tap any icon to launch it (same as tapping it on the home screen).
 *   3. RENAME: Tap the folder name at the top to edit it inline.
 *   4. INTERNAL DRAG: Long-press + drag an icon to reorder it within the popup.
 *   5. DRAG-OUT: Long-press + drag an icon outside the popup bounds to move it
 *                back to the home screen grid.
 *   6. REMOVE: Long-press → context menu → "Remove from folder".
 *
 * ============================================================================
 * INTERNAL DRAG-AND-DROP REORDERING
 * ============================================================================
 *
 * Each icon in the 3-column grid supports the same long-press → drag gesture used
 * on the home screen ([DraggablePinnedItemsGrid]). During a drag inside the popup:
 *
 *   - The dragged icon becomes a ghost (mostly transparent at its original slot).
 *   - A floating preview follows the user's finger.
 *   - When the finger is released, the icon is placed at the closest slot.
 *   - [onReorderFolderItems] is called with the new order.
 *
 * ============================================================================
 * DRAG-OUT MECHANISM
 * ============================================================================
 *
 * "Drag-out" refers to dragging an icon outside the popup card onto the home grid:
 *
 *   1. User starts dragging an icon inside the popup.
 *   2. As the drag continues and the pointer exits the popup card's screen bounds,
 *      [onItemDraggedOutside] is called with the item and current screen offset.
 *   3. LauncherScreen receives this callback, stores `floatingPreviewItem` +
 *      `floatingPreviewOffset`, and renders a floating preview above the home grid.
 *   4. As the pointer moves, [onItemDragMovedOutside] is called to update the offset.
 *   5. When the pointer is released outside, [onItemDroppedOutside] is called.
 *      LauncherScreen converts the screen offset to a grid cell and calls
 *      `homeViewModel.extractItemFromFolder(...)`.
 *   6. If the drag re-enters the popup (user brought finger back), [onItemDragReturnedInside]
 *      is called and the drag continues as normal internal drag.
 *
 * Why the popup stays MOUNTED during drag-out (alpha=1, not removed):
 *   - Compose gesture handlers are bound to the composable that first received the
 *     pointer-down event. If the popup is removed while the drag is active, the
 *     gesture channel breaks and `onDragEnd` never fires.
 *   - Instead, the popup stays fully rendered but the dragged item is ghost-faded
 *     (alpha = 0.2f) to indicate it has "left" the popup.
 *
 * ============================================================================
 * COORDINATE SYSTEM
 * ============================================================================
 *
 * This popup uses window-relative pixel coordinates for drag tracking:
 *
 *   - [LayoutCoordinates.localToWindow(Offset.Zero)] gives each item's window-relative
 *     top-left corner. Stored in [itemWindowOffsets] map, updated by `onGloballyPositioned`.
 *   - Accumulated drag deltas from [detectDragGesture] are in the local coordinate space
 *     of the item's [pointerInput]. Since no transforms (scale/rotation) are applied to
 *     the popup items or popup card, window-relative position ≈ local position + item offset.
 *   - The popup card's window rect is similarly captured and stored in [popupWindowRect].
 *
 * ============================================================================
 * CALLBACKS
 * ============================================================================
 *
 * @param folder                    The folder to display. Passed down from the ViewModel's
 *                                  derived [HomeUiState.openFolderItem]. Changes here
 *                                  (e.g. item added by another drag) automatically recompose.
 * @param onClose                   Called when the user taps the scrim or back button.
 * @param onRenameFolder            Called when the user confirms a new folder name via
 *                                  the editable title at the top of the popup.
 * @param onItemClick               Called when the user taps an icon to launch it.
 * @param onReorderFolderItems      Called when internal drag-drop changes the children order.
 * @param onRemoveItemFromFolder    Called when the user uses the context menu to remove an icon.
 * @param onItemDraggedOutside      Called once when the drag first exits the popup bounds.
 * @param onItemDragMovedOutside    Called every drag update while the drag is outside bounds.
 * @param onItemDroppedOutside      Called when the drag ends outside the popup bounds.
 * @param onItemDragReturnedInside  Called when the drag re-enters the popup bounds.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ============================================================================
// PUBLIC COMPOSABLE
// ============================================================================

/**
 * FolderPopupDialog - The popup shown when the user opens a folder on the home screen.
 *
 * See the file-level documentation above for a full description of the features,
 * drag-out mechanism, and coordinate system used.
 */
@Composable
fun FolderPopupDialog(
    folder: HomeItem.FolderItem,
    onClose: () -> Unit,
    onRenameFolder: (newName: String) -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onReorderFolderItems: (newChildren: List<HomeItem>) -> Unit,
    onRemoveItemFromFolder: (itemId: String) -> Unit,
    onItemDraggedOutside: (item: HomeItem, screenOffset: Offset) -> Unit = { _, _ -> },
    onItemDragMovedOutside: (item: HomeItem, screenOffset: Offset) -> Unit = { _, _ -> },
    onItemDroppedOutside: (item: HomeItem, screenOffset: Offset) -> Unit = { _, _ -> },
    onItemDragReturnedInside: () -> Unit = {}
) {

    // =========================================================================
    // LOCAL STATE
    // =========================================================================

    /**
     * Local (optimistic) copy of the folder's children list.
     *
     * WHY LOCAL COPY:
     * The [folder] parameter is updated from the ViewModel's StateFlow.
     * If we only used [folder.children] directly in the UI, a pending async
     * reorder write could cause the displayed order to briefly revert while the
     * DataStore write is in flight, producing a visual "blink".
     *
     * By maintaining a local copy and applying reorders to it immediately,
     * the UI stays stable. The [LaunchedEffect] below keeps it in sync whenever
     * the authoritative list changes (and no drag is active).
     */
    var localChildren by remember(folder.id) { mutableStateOf(folder.children) }

    /**
     * Sync the local children list with the repository's list whenever there is
     * no active drag (to avoid overwriting mid-drag state).
     */
    var isDraggingInternally by remember { mutableStateOf(false) }

    LaunchedEffect(folder.children) {
        if (!isDraggingInternally) {
            localChildren = folder.children
        }
    }

    /**
     * The current folder name as displayed in the editable title.
     * Starts with [folder.name] and is updated locally as the user types.
     */
    var editingName by remember(folder.id) { mutableStateOf(folder.name) }

    /**
     * Whether the folder name text field is currently in edit mode (showing cursor).
     * Toggled by tapping the title row.
     */
    var isEditingName by remember { mutableStateOf(false) }

    /** FocusRequester used to programmatically request focus on the name field. */
    val nameFocusRequester = remember { FocusRequester() }

    // -------------------------------------------------------------------------
    // Context menu state (long-press on item → remove from folder)
    // -------------------------------------------------------------------------

    /** The ID of the item whose context menu is currently shown, or null. */
    var menuShownForItemId by remember { mutableStateOf<String?>(null) }

    // -------------------------------------------------------------------------
    // Internal drag-and-drop state
    // -------------------------------------------------------------------------

    /**
     * The ID of the item currently being dragged (null when no drag is active).
     */
    var draggedItemId by remember { mutableStateOf<String?>(null) }

    /**
     * Accumulated drag offset from the start of the drag gesture.
     * Updated every frame inside [onDrag].
     */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    /**
     * The window-relative position of the dragged item's top-left corner at
     * the moment the drag started.  Set once per drag in [onDragStart].
     */
    var dragStartWindowPos by remember { mutableStateOf(Offset.Zero) }

    /**
     * Whether the current drag has exited the popup card's bounds.
     * When true, [onItemDragMovedOutside] is called instead of doing internal reorder.
     *
     * The dragged item rendering in the grid switches to ghost mode (alpha 0.2) while
     * this is true, signalling visually that the item has "escaped" the folder.
     */
    var isDraggingOut by remember { mutableStateOf(false) }

    /**
     * The item currently being dragged out (cached at drag-start to avoid null checks
     * inside the gesture callbacks).
     */
    var dragOutItem by remember { mutableStateOf<HomeItem?>(null) }

    // -------------------------------------------------------------------------
    // Layout coordinates (updated by onGloballyPositioned)
    // -------------------------------------------------------------------------

    /**
     * Bounding rect of the popup card in window-relative pixel coordinates.
     *
     * Used to determine whether the drag pointer has exited the popup.
     * Updated by [onGloballyPositioned] on the Card composable.
     */
    var popupWindowRect by remember { mutableStateOf(Rect.Zero) }

    /**
     * Window-relative top-left position of each home item's grid cell.
     * Key = [HomeItem.id], Value = [Offset] (window-relative top-left corner).
     *
     * Updated by [onGloballyPositioned] on each item's root Box inside the grid.
     *
     * WHY NOT UsE LayoutCoordinates DIRECTLY:
     * [LayoutCoordinates] instances from [onGloballyPositioned] may be stale by
     * the time a drag gesture callback fires. Converting to [Offset] immediately
     * (via [LayoutCoordinates.localToWindow]) ensures we use a snapshot at the
     * time of the most recent composition pass.
     */
    val itemWindowOffsets = remember { mutableStateMapOf<String, Offset>() }

    /** Approximate cell size in window pixels (used for drop-target computation). */
    var cellSizePx by remember { mutableStateOf(0f) }

    val hapticFeedback = LocalHapticFeedback.current

    // =========================================================================
    // ROOT LAYOUT: Full-screen scrim → centered card
    // =========================================================================

    /**
     * Full-screen Box that captures the scrim tap (closes the popup) and hosts
     * both the popup card and the drag-out floating preview.
     *
     * Z-ORDER:
     * 1. Scrim (bottom — entire screen)
     * 2. Popup card (center — fixed size)
     * 3. Drag floating preview (top — follows finger)
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ─── Scrim ───────────────────────────────────────────────────────────
        // Semi-transparent overlay that dims the home screen content behind the popup.
        // Tapping the scrim closes the folder.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        )

        // ─── Popup card ──────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                // Max width prevents the dialog from being too wide on large screens.
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.88f)
                // Capture the card's window-relative bounding rect for drag-out detection.
                .onGloballyPositioned { coords ->
                    popupWindowRect = coords.windowRect()
                }
                // Prevent tapping the card itself from closing the popup (the scrim
                // click listener is below this card in the Z-order, but the Box takes
                // up the full size — we block propagation with a no-op click handler).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks inside the card, don't close. */ }
                ),
            shape = RoundedCornerShape(CornerRadius.extraLarge),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Spacing.smallMedium)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ─── Editable folder name ─────────────────────────────────────
                FolderNameHeader(
                    name = editingName,
                    isEditing = isEditingName,
                    focusRequester = nameFocusRequester,
                    onNameChange = { editingName = it },
                    onEditingChanged = { newEditing ->
                        isEditingName = newEditing
                        if (!newEditing) {
                            // User stopped editing → persist the new name.
                            onRenameFolder(editingName)
                        }
                    },
                    onEditRequested = {
                        isEditingName = true
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.mediumLarge))

                // ─── Icon grid ────────────────────────────────────────────────
                // 3 columns, scrollable, max height = 70% of a typical screen.
                // Each cell is one-third of the popup card's width in size.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(FOLDER_POPUP_COLUMNS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    contentPadding = PaddingValues(Spacing.none),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.none),
                    verticalArrangement = Arrangement.spacedBy(Spacing.none)
                ) {
                    itemsIndexed(
                        items = localChildren,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        // The per-item box needs:
                        // 1. onGloballyPositioned: capture its window position
                        // 2. detectDragGesture: handle long-press + drag
                        // 3. Visual: ghost when dragged, normal otherwise
                        FolderPopupItem(
                            item = item,
                            itemIndex = index,
                            isDragged = item.id == draggedItemId,
                            isDraggingOut = isDraggingOut && item.id == draggedItemId,
                            showMenu = menuShownForItemId == item.id,
                            onMenuDismiss = { menuShownForItemId = null },
                            onTap = { onItemClick(item) },
                            onLongPress = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuShownForItemId = item.id
                            },
                            onRemoveFromFolder = {
                                menuShownForItemId = null
                                onRemoveItemFromFolder(item.id)
                            },
                            onCellSizeMeasured = { sizePx -> cellSizePx = sizePx },
                            onWindowPositionMeasured = { windowOffset ->
                                itemWindowOffsets[item.id] = windowOffset
                            },
                            onDragStart = {
                                isDraggingInternally = true
                                draggedItemId = item.id
                                dragOutItem = item
                                dragOffset = Offset.Zero
                                dragStartWindowPos = itemWindowOffsets[item.id] ?: Offset.Zero
                                isDraggingOut = false
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.GestureThresholdActivate
                                )
                            },
                            onDragDelta = { delta ->
                                dragOffset += delta

                                // ------------------------------------------------
                                // Drag-out detection
                                // ------------------------------------------------
                                // Compute the absolute pointer position by adding the
                                // item's starting window offset to the accumulated drag.
                                val absolutePointerPos = dragStartWindowPos + dragOffset

                                val wasOutside = isDraggingOut
                                val isNowOutside = !popupWindowRect.contains(absolutePointerPos)

                                when {
                                    isNowOutside && !wasOutside -> {
                                        // Just exited popup bounds for the first time.
                                        isDraggingOut = true
                                        dragOutItem?.let { escapedItem ->
                                            onItemDraggedOutside(escapedItem, absolutePointerPos)
                                        }
                                    }
                                    isNowOutside && wasOutside -> {
                                        // Still outside — update parent with latest position.
                                        dragOutItem?.let { escapedItem ->
                                            onItemDragMovedOutside(escapedItem, absolutePointerPos)
                                        }
                                    }
                                    !isNowOutside && wasOutside -> {
                                        // Re-entered popup bounds.
                                        isDraggingOut = false
                                        onItemDragReturnedInside()
                                    }
                                    // else: still inside, no-op
                                }
                            },
                            onDragEnd = {
                                val currentDraggedItem = localChildren.find { it.id == draggedItemId }

                                if (isDraggingOut && currentDraggedItem != null) {
                                    // ---- Drag released OUTSIDE popup ----
                                    val absoluteDropPos = dragStartWindowPos + dragOffset
                                    onItemDroppedOutside(currentDraggedItem, absoluteDropPos)
                                } else if (currentDraggedItem != null) {
                                    // ---- Drag released INSIDE popup: reorder ----
                                    val absoluteDropPos = dragStartWindowPos + dragOffset
                                    val targetIndex = findClosestItemIndex(
                                        dropWindowPos = absoluteDropPos,
                                        children = localChildren,
                                        itemWindowOffsets = itemWindowOffsets,
                                        cellSizePx = cellSizePx
                                    )
                                    if (targetIndex != null && targetIndex != index) {
                                        // Move the dragged item to the target index.
                                        val reordered = localChildren.toMutableList().apply {
                                            remove(currentDraggedItem)
                                            add(
                                                targetIndex.coerceIn(0, size),
                                                currentDraggedItem
                                            )
                                        }
                                        localChildren = reordered
                                        onReorderFolderItems(reordered)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                                }

                                // Reset drag state.
                                draggedItemId = null
                                dragOffset = Offset.Zero
                                isDraggingOut = false
                                dragOutItem = null
                                isDraggingInternally = false
                            },
                            onDragCancel = {
                                draggedItemId = null
                                dragOffset = Offset.Zero
                                isDraggingOut = false
                                dragOutItem = null
                                isDraggingInternally = false
                                onItemDragReturnedInside()
                            }
                        )
                    }
                }
            }
        }

        // ─── Internal floating drag preview ──────────────────────────────────
        // While dragging INSIDE the popup, a floating PinnedItem ghost follows
        // the user's finger to show where the icon will land.
        // This is suppressed during drag-out (the parent LauncherScreen shows
        // its own floating preview in that case).
        val draggedItem = localChildren.find { it.id == draggedItemId }
        if (draggedItem != null && draggedItemId != null && !isDraggingOut) {
            // The floating preview is positioned in window coordinates.
            // We offset it so the icon center sits under the user's finger.
            val previewOffset = dragStartWindowPos + dragOffset

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = previewOffset.x.roundToInt(),
                            y = previewOffset.y.roundToInt()
                        )
                    }
                    .size(with(androidx.compose.ui.platform.LocalDensity.current) {
                        cellSizePx.toDp()
                    })
                    .zIndex(FOLDER_DRAG_PREVIEW_Z_INDEX)
                    .alpha(FOLDER_PREVIEW_ALPHA)
            ) {
                PinnedItem(
                    item = draggedItem,
                    onClick = {},
                    onLongClick = {},
                    handleLongPress = false
                )
            }
        }
    }
}

// ============================================================================
// FOLDER NAME HEADER
// ============================================================================

/**
 * FolderNameHeader - Editable text field showing the folder's name at the top of the popup.
 *
 * BEHAVIOR:
 * - In normal state: displays the name as plain text (no cursor).
 * - When [isEditing] is true: shows a cursor and allows the user to type.
 * - Tapping the name header switches to edit mode ([onEditRequested]).
 * - Losing focus ([onEditingChanged] with false) commits the change.
 *
 * A thin bottom border appears only when in edit mode to clearly indicate
 * the field is interactive while keeping the popup uncluttered when not.
 *
 * @param name Current folder name text.
 * @param isEditing Whether the field is in edit/cursor mode.
 * @param focusRequester Used to programmatically focus the field.
 * @param onNameChange Called on every keystroke.
 * @param onEditingChanged Called with true when focus is gained; false when focus is lost.
 * @param onEditRequested Called when the user taps the non-editing title to start editing.
 */
@Composable
private fun FolderNameHeader(
    name: String,
    isEditing: Boolean,
    focusRequester: FocusRequester,
    onNameChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onEditRequested: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wrapper that captures tap for entering edit mode.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEditRequested
                )
        ) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        onEditingChanged(focusState.isFocused)
                    },
                // Show a subtle cursor only in edit mode.
                readOnly = !isEditing
            )
        }

        // Thin underline hint that appears only when in edit mode.
        if (isEditing) {
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(Spacing.hairline)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            )
        }
    }

    // Request focus programmatically when edit mode is activated.
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
}

// ============================================================================
// FOLDER POPUP ITEM
// ============================================================================

/**
 * FolderPopupItem - A single icon  cell inside the folder popup grid.
 *
 * Renders a [PinnedItem] for the given [HomeItem] with:
 * - A ghost appearance when it is the currently dragged item.
 * - A [detectDragGesture] modifier for long-press + drag interaction.
 * - A context menu (shown via long-press) with a "Remove from folder" option.
 * - A drop-target highlight (subtle border) when another item is being dragged
 *   close to this slot (indicated by being the closest target slot).
 *
 * GESTURE ROUTING:
 * The gesture lifecycle is:
 *   onTap         → [onTap] (launches the item)
 *   onLongPress   → [onLongPress] (shows context menu)
 *   onDragStart   → [onDragStart]
 *   onDrag        → [onDragDelta] (called with each incremental movement)
 *   onDragEnd     → [onDragEnd]
 *   onDragCancel  → [onDragCancel]
 *
 * @param item The home item displayed in this slot.
 * @param itemIndex The 0-based index of this item in [folder.children].
 * @param isDragged Whether THIS item is the one being dragged (ghost appearance).
 * @param isDraggingOut Whether the drag has exited popup bounds (more ghost = 0.2 alpha).
 * @param showMenu Whether the long-press context menu is currently visible.
 * @param onMenuDismiss Called when the context menu is dismissed.
 * @param onTap Called when the user taps.
 * @param onLongPress Called when the user long-presses without dragging.
 * @param onRemoveFromFolder Called from the context menu "Remove from folder" option.
 * @param onCellSizeMeasured Callback with the cell's pixel width (used for drop-target math).
 * @param onWindowPositionMeasured Callback with the cell's window-relative top-left Offset.
 * @param onDragStart Called when the drag threshold is exceeded.
 * @param onDragDelta Called with each incremental [Offset] during the drag.
 * @param onDragEnd Called when the drag gesture ends (pointer released).
 * @param onDragCancel Called when the drag is cancelled.
 */
@Composable
private fun FolderPopupItem(
    item: HomeItem,
    itemIndex: Int,
    isDragged: Boolean,
    isDraggingOut: Boolean,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onCellSizeMeasured: (sizePx: Float) -> Unit,
    onWindowPositionMeasured: (windowOffset: Offset) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    // Visual alpha: dragged items are shown as ghosts.
    // - isDraggingOut=true  → very faint (0.2) to signal the item has left the popup.
    // - isDragged=true       → semi-transparent (0.4) while dragging inside.
    // - normal               → fully opaque (1.0).
    val alpha = when {
        isDragged && isDraggingOut -> FOLDER_DRAG_OUT_GHOST_ALPHA
        isDragged -> FOLDER_DRAG_IN_GHOST_ALPHA
        else -> 1f
    }

    // Whether a context menu gesture is in progress (finger still down after long-press).
    // When true we set menuFocusable=false on the PinnedItem so touches still
    // reach the gesture detector for potential drag start.
    var isLongPressGestureActive by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            // Capture this cell's pixel size and window-relative position.
            .onGloballyPositioned { coords ->
                onCellSizeMeasured(coords.size.width.toFloat())
                onWindowPositionMeasured(coords.localToWindow(Offset.Zero))
            }
            // Ghost appearance while dragging.
            .alpha(alpha)
            // Attach the long-press → drag gesture detector.
            .detectDragGesture(
                // Use the item id + index as the key so the detector is invalidated
                // when the item moves to a different slot (after a reorder).
                key = "${item.id}-$itemIndex",
                dragThreshold = FOLDER_DRAG_THRESHOLD_PX,
                onTap = { onTap() },
                onLongPress = {
                    isLongPressGestureActive = true
                    onLongPress()
                },
                onLongPressRelease = {
                    // Finger lifted after long-press without dragging.
                    // Restore the menu to focusable so its items are tappable.
                    isLongPressGestureActive = false
                },
                onDragStart = {
                    isLongPressGestureActive = false
                    onDragStart()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = {
                    isLongPressGestureActive = false
                    onDragCancel()
                }
            )
    ) {
        // ── Render the icon ──────────────────────────────────────────────────
        PinnedItem(
            item = item,
            onClick = {},          // Gestures are handled above by detectDragGesture.
            onLongClick = {},
            handleLongPress = false,
            showMenu = showMenu,
            onMenuDismiss = onMenuDismiss,
            menuFocusable = !isLongPressGestureActive
        )

        // ── Context menu ─────────────────────────────────────────────────────
        // A minimal DropdownMenu providing only the "Remove from folder" action.
        // (The standard ItemActionMenu is not used here because UnpinItem would
        //  remove the item from the home screen entirely rather than from the folder.)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onMenuDismiss
        ) {
            DropdownMenuItem(
                text = { Text("Remove from folder") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null
                    )
                },
                onClick = onRemoveFromFolder
            )
        }
    }
}

// ============================================================================
// DRAG-AND-DROP MATH UTILITIES
// ============================================================================

/**
 * Finds the index in [children] whose icon center is closest to [dropWindowPos].
 *
 * Used by [FolderPopupDialog]'s drag-end handler to determine where to insert
 * the dragged item. The resulting index is the slot to which the dragged item
 * should be moved.
 *
 * DISTANCE METRIC:
 * Euclidean distance between the center of each item's window-relative rect and
 * [dropWindowPos]. The item with the smallest distance wins.
 *
 * @param dropWindowPos    The window-relative position where the pointer was released.
 * @param children         The current ordered children list.
 * @param itemWindowOffsets Map of item ID → window-relative top-left Offset.
 * @param cellSizePx       Width (= height) of each cell in pixels.
 * @return The index of the closest item, or null if no positions are tracked yet.
 */
private fun findClosestItemIndex(
    dropWindowPos: Offset,
    children: List<HomeItem>,
    itemWindowOffsets: Map<String, Offset>,
    cellSizePx: Float
): Int? {
    if (cellSizePx <= 0f) return null

    var bestIndex: Int? = null
    var bestDistanceSq = Float.MAX_VALUE

    children.forEachIndexed { index, item ->
        val topLeft = itemWindowOffsets[item.id] ?: return@forEachIndexed
        // Compute the center of this cell.
        val centerX = topLeft.x + cellSizePx * 0.5f
        val centerY = topLeft.y + cellSizePx * 0.5f

        // Squared Euclidean distance (no sqrt needed for comparison).
        val dx = dropWindowPos.x - centerX
        val dy = dropWindowPos.y - centerY
        val distSq = dx * dx + dy * dy

        if (distSq < bestDistanceSq) {
            bestDistanceSq = distSq
            bestIndex = index
        }
    }

    return bestIndex
}

// ============================================================================
// LAYOUT COORDINATES HELPER
// ============================================================================

/**
 * Computes a [Rect] in window-relative pixel coordinates from a [LayoutCoordinates].
 *
 * The Rect's bounds are:
 *   left   = window-relative X of the composable's top-left corner
 *   top    = window-relative Y of the composable's top-left corner
 *   right  = left + composable width in pixels
 *   bottom = top  + composable height in pixels
 *
 * @receiver LayoutCoordinates captured by an `onGloballyPositioned` modifier.
 * @return A [Rect] in window-pixel coordinates.
 */
private fun LayoutCoordinates.windowRect(): Rect {
    val topLeft = this.localToWindow(Offset.Zero)
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + this.size.width,
        bottom = topLeft.y + this.size.height
    )
}

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Number of columns in the folder popup grid.
 * Chosen by the user in the initial design discussion.
 */
private const val FOLDER_POPUP_COLUMNS = 3

/**
 * Minimum drag distance (in pixels) to initiate a drag gesture inside the folder popup.
 *
 * This intentionally matches [GridConfig.dragThresholdPx] (20f) so the UX feel is
 * the same as dragging on the home screen.
 */
private const val FOLDER_DRAG_THRESHOLD_PX = 20f

/**
 * Alpha applied to the dragged item while dragging INSIDE the popup.
 * Low enough to indicate "this is moving" without making it invisible.
 */
private const val FOLDER_DRAG_IN_GHOST_ALPHA = 0.35f

/**
 * Alpha applied to the dragged item while dragging OUTSIDE the popup bounds.
 * Very low to communicate "this item has left the folder".
 */
private const val FOLDER_DRAG_OUT_GHOST_ALPHA = 0.15f

/**
 * Alpha for the floating preview that follows the user's finger during an internal drag.
 */
private const val FOLDER_PREVIEW_ALPHA = 0.88f

/**
 * Z-index for the floating preview so it is rendered above all grid items.
 */
private const val FOLDER_DRAG_PREVIEW_Z_INDEX = 100f
