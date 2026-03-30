package com.milki.launcher.ui.components.folder

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.ItemActionMenu
import com.milki.launcher.ui.components.MenuAction
import com.milki.launcher.ui.components.PinnedItem
import com.milki.launcher.ui.components.dragdrop.startExternalFolderItemDrag
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun FolderPopupDialog(
    folder: HomeItem.FolderItem,
    anchorBounds: Rect?,
    onClose: () -> Unit,
    onRenameFolder: (newName: String) -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onReorderFolderItems: (newChildren: List<HomeItem>) -> Unit,
    onRemoveItemFromFolder: (itemId: String) -> Unit
) {
    var localChildren by remember(folder.id) { mutableStateOf(folder.children) }
    var isDraggingInternally by remember { mutableStateOf(false) }
    var editingName by remember(folder.id) { mutableStateOf(folder.name) }
    var isEditingName by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    var menuShownForItemId by remember { mutableStateOf<String?>(null) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartWindowPos by remember { mutableStateOf(Offset.Zero) }
    var dragProbeOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOutItem by remember { mutableStateOf<HomeItem?>(null) }
    var isDraggingOut by remember { mutableStateOf(false) }
    var popupWindowRect by remember { mutableStateOf(Rect.Zero) }
    var gridWindowRect by remember { mutableStateOf(Rect.Zero) }
    var hoveredSlot by remember { mutableStateOf<FolderDropSlot?>(null) }
    var pendingAutoPage by remember { mutableStateOf<Int?>(null) }
    var isAutoPaging by remember { mutableStateOf(false) }
    var isPlatformDragActive by remember { mutableStateOf(false) }
    val itemWindowOffsets = remember { mutableStateMapOf<String, Offset>() }

    val hapticFeedback = LocalHapticFeedback.current
    val hostView = LocalView.current
    val density = LocalDensity.current

    LaunchedEffect(folder.children) {
        if (!isDraggingInternally) {
            localChildren = folder.children
        }
    }

    LaunchedEffect(folder.name) {
        if (!isEditingName) {
            editingName = folder.name
        }
    }

    val layout = remember(localChildren.size) {
        folderGridLayoutForItemCount(localChildren.size)
    }
    val pagerState = rememberPagerState(initialPage = 0) { layout.pageCount }

    LaunchedEffect(layout.pageCount) {
        val lastPage = (layout.pageCount - 1).coerceAtLeast(0)
        if (pagerState.currentPage > lastPage) {
            pagerState.scrollToPage(lastPage)
        }
    }

    LaunchedEffect(pendingAutoPage, draggedItemId) {
        val targetPage = pendingAutoPage ?: return@LaunchedEffect
        if (draggedItemId == null) return@LaunchedEffect

        kotlinx.coroutines.delay(FOLDER_AUTO_PAGE_DELAY_MS)
        if (pendingAutoPage == targetPage && pagerState.currentPage != targetPage) {
            isAutoPaging = true
            pagerState.animateScrollToPage(targetPage)
            pagerState.scrollToPage(targetPage)
            isAutoPaging = false
        }
        if (pendingAutoPage == targetPage) {
            pendingAutoPage = null
        }
    }

    var isEntering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isEntering = true
    }
    val openProgress by animateFloatAsState(
        targetValue = if (isEntering) 1f else 0f,
        animationSpec = tween(
            durationMillis = FOLDER_OPEN_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "folderOpenProgress"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = remember(
            localChildren.size,
            layout,
            maxWidth,
            maxHeight,
            density
        ) {
            FolderSurfaceMetrics.create(
                density = density,
                layout = layout,
                pageCount = layout.pageCount,
                maxWidth = maxWidth,
                maxHeight = maxHeight
                )
        }

        val targetBoundsPx = remember(
            anchorBounds,
            metrics.surfaceWidth,
            metrics.surfaceHeight,
            maxWidth,
            maxHeight,
            density
        ) {
            resolveFolderTargetBounds(
                density = density,
                surfaceWidth = metrics.surfaceWidth,
                surfaceHeight = metrics.surfaceHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                anchorBounds = anchorBounds
            )
        }

        val fallbackAnchorBounds = remember(targetBoundsPx) { targetBoundsPx }
        val resolvedAnchorBounds = anchorBounds ?: fallbackAnchorBounds
        val anchorCenter = resolvedAnchorBounds.center()
        val targetCenter = targetBoundsPx.center()

        val startScaleX = (resolvedAnchorBounds.width / targetBoundsPx.width)
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtLeast(FOLDER_MIN_START_SCALE)
            ?: 1f
        val startScaleY = (resolvedAnchorBounds.height / targetBoundsPx.height)
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtLeast(FOLDER_MIN_START_SCALE)
            ?: 1f

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f + (0.34f * openProgress)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            )

            Card(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = targetBoundsPx.left.roundToInt(),
                            y = targetBoundsPx.top.roundToInt()
                        )
                    }
                    .size(
                        width = metrics.surfaceWidth,
                        height = metrics.surfaceHeight
                    )
                    .graphicsLayer {
                        val startTranslation = anchorCenter - targetCenter
                        translationX = startTranslation.x * (1f - openProgress)
                        translationY = startTranslation.y * (1f - openProgress)
                        scaleX = lerp(startScaleX, 1f, openProgress)
                        scaleY = lerp(startScaleY, 1f, openProgress)
                        alpha = lerp(0.82f, 1f, openProgress)
                    }
                    .shadow(
                        elevation = Spacing.mediumLarge,
                        shape = RoundedCornerShape(CornerRadius.large),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                    .onGloballyPositioned { coords ->
                        popupWindowRect = coords.windowRect()
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                shape = RoundedCornerShape(CornerRadius.large),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = metrics.horizontalPadding,
                            vertical = metrics.verticalPadding
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FolderNameHeader(
                        name = editingName,
                        isEditing = isEditingName,
                        focusRequester = nameFocusRequester,
                        itemCount = localChildren.size,
                        onNameChange = { editingName = it },
                        onEditingChanged = { isEditing ->
                            val wasEditing = isEditingName
                            isEditingName = isEditing
                            if (wasEditing && !isEditing) {
                                onRenameFolder(editingName)
                            }
                        },
                        onEditRequested = { isEditingName = true }
                    )

                    Spacer(modifier = Modifier.height(metrics.headerBottomSpacing))

                    Box(
                        modifier = Modifier
                            .width(metrics.gridWidth)
                            .height(metrics.gridHeight)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(0.dp),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = draggedItemId == null && layout.pageCount > 1
                        ) { page ->
                            FolderGridPage(
                                page = page,
                                layout = layout,
                                localChildren = localChildren,
                                cellWidth = metrics.cellWidth,
                                cellHeight = metrics.cellHeight,
                                cellSpacing = metrics.cellSpacing,
                                draggedItemId = draggedItemId,
                                hoveredSlot = hoveredSlot,
                                menuShownForItemId = menuShownForItemId,
                                onGridBoundsMeasured = { pageBounds ->
                                    if (page == pagerState.currentPage) {
                                        gridWindowRect = pageBounds
                                    }
                                },
                                onMenuDismiss = { menuShownForItemId = null },
                                onTap = { item -> onItemClick(item) },
                                onLongPress = { itemId ->
                                    menuShownForItemId = itemId
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onRemoveFromFolder = { itemId ->
                                    menuShownForItemId = null
                                    onRemoveItemFromFolder(itemId)
                                },
                                onWindowPositionMeasured = { itemId, windowOffset ->
                                    itemWindowOffsets[itemId] = windowOffset
                                },
                                onDragStart = { item ->
                                    isDraggingInternally = true
                                    draggedItemId = item.id
                                    dragOutItem = item
                                    dragOffset = Offset.Zero
                                    dragStartWindowPos = itemWindowOffsets[item.id] ?: Offset.Zero
                                    dragProbeOffset = with(density) {
                                        Offset(
                                            x = metrics.cellWidth.toPx() * 0.5f,
                                            y = metrics.cellHeight.toPx() * 0.5f
                                        )
                                    }
                                    isDraggingOut = false
                                    isPlatformDragActive = false
                                    hoveredSlot = FolderDropSlot(
                                        page = page,
                                        slotIndex = slotIndexForPageItem(
                                            itemIndex = localChildren.indexOfFirst { it.id == item.id },
                                            pageSize = layout.pageSize
                                        )
                                    )
                                    menuShownForItemId = null
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragDelta = { delta ->
                                    dragOffset += delta

                                    if (isPlatformDragActive) return@FolderGridPage

                                    val dragProbePos = dragStartWindowPos + dragProbeOffset + dragOffset
                                    val isNowOutside = !popupWindowRect.contains(dragProbePos)

                                    if (isNowOutside) {
                                        isDraggingOut = true
                                        val escapedItem = dragOutItem
                                        if (escapedItem != null) {
                                            isPlatformDragActive = true
                                            startExternalFolderItemDrag(
                                                hostView = hostView,
                                                folderId = folder.id,
                                                item = escapedItem
                                            )
                                            onClose()
                                        }
                                        return@FolderGridPage
                                    }

                                    isDraggingOut = false
                                    hoveredSlot = resolveHoveredSlot(
                                        pointer = dragProbePos,
                                        currentPage = pagerState.currentPage,
                                        layout = layout,
                                        gridWindowRect = gridWindowRect,
                                        cellSpacingPx = with(density) { metrics.cellSpacing.toPx() }
                                    )
                                    if (!isAutoPaging) {
                                        pendingAutoPage = resolveAutoPageTarget(
                                            pointer = dragProbePos,
                                            popupBounds = popupWindowRect,
                                            currentPage = pagerState.currentPage,
                                            pageCount = layout.pageCount,
                                            edgeThresholdPx = with(density) {
                                                FOLDER_AUTO_PAGE_EDGE_THRESHOLD.toPx()
                                            }
                                        )
                                    }
                                },
                                onDragEnd = {
                                    val draggedItem = dragOutItem

                                    if (!isPlatformDragActive && draggedItem != null) {
                                        val dropSlot = hoveredSlot ?: resolveHoveredSlot(
                                            pointer = dragStartWindowPos + dragProbeOffset + dragOffset,
                                            currentPage = pagerState.currentPage,
                                            layout = layout,
                                            gridWindowRect = gridWindowRect,
                                            cellSpacingPx = with(density) { metrics.cellSpacing.toPx() }
                                        )
                                        val fromIndex = localChildren.indexOfFirst { it.id == draggedItem.id }

                                        if (dropSlot != null && fromIndex >= 0) {
                                            val targetIndex = resolveFolderDropIndex(
                                                targetPage = dropSlot.page,
                                                slotIndex = dropSlot.slotIndex,
                                                pageSize = layout.pageSize
                                            )
                                            val reordered = reorderFolderItemsForDrop(
                                                items = localChildren,
                                                fromIndex = fromIndex,
                                                targetIndex = targetIndex
                                            )

                                            if (reordered != localChildren) {
                                                localChildren = reordered
                                                onReorderFolderItems(reordered)
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.Confirm
                                                )
                                            }
                                        }
                                    }

                                    draggedItemId = null
                                    dragOffset = Offset.Zero
                                    dragProbeOffset = Offset.Zero
                                    dragOutItem = null
                                    isDraggingOut = false
                                    isDraggingInternally = false
                                    isPlatformDragActive = false
                                    hoveredSlot = null
                                    pendingAutoPage = null
                                    isAutoPaging = false
                                },
                                onDragCancel = {
                                    draggedItemId = null
                                    dragOffset = Offset.Zero
                                    dragProbeOffset = Offset.Zero
                                    dragOutItem = null
                                    isDraggingOut = false
                                    isDraggingInternally = false
                                    isPlatformDragActive = false
                                    hoveredSlot = null
                                    pendingAutoPage = null
                                    isAutoPaging = false
                                }
                            )
                        }
                    }

                    if (layout.pageCount > 1) {
                        Spacer(modifier = Modifier.height(metrics.indicatorTopSpacing))
                        FolderPagerIndicator(
                            pageCount = layout.pageCount,
                            currentPage = pagerState.currentPage
                        )
                    }
                }
            }

            val draggedItem = dragOutItem
            if (draggedItem != null && draggedItemId != null && !isDraggingOut) {
                val previewOffset = dragStartWindowPos + dragOffset
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = previewOffset.x.roundToInt(),
                                y = previewOffset.y.roundToInt()
                            )
                        }
                        .size(metrics.cellWidth, metrics.cellHeight)
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
}

@Composable
private fun FolderNameHeader(
    name: String,
    isEditing: Boolean,
    focusRequester: FocusRequester,
    itemCount: Int,
    onNameChange: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onEditRequested: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    fontWeight = FontWeight.SemiBold,
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
                readOnly = !isEditing
            )
        }

        Spacer(modifier = Modifier.height(Spacing.extraSmall))
        Text(
            text = if (itemCount == 1) "1 item" else "$itemCount items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        if (isEditing) {
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Box(
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .fillMaxWidth(0.45f)
                    .height(Spacing.hairline)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            )
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun FolderGridPage(
    page: Int,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    cellWidth: Dp,
    cellHeight: Dp,
    cellSpacing: Dp,
    draggedItemId: String?,
    hoveredSlot: FolderDropSlot?,
    menuShownForItemId: String?,
    onGridBoundsMeasured: (Rect) -> Unit,
    onMenuDismiss: () -> Unit,
    onTap: (HomeItem) -> Unit,
    onLongPress: (String) -> Unit,
    onRemoveFromFolder: (String) -> Unit,
    onWindowPositionMeasured: (String, Offset) -> Unit,
    onDragStart: (HomeItem) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val pageStart = page * layout.pageSize
    val pageItems = localChildren.drop(pageStart).take(layout.pageSize)
    val slots = List(layout.pageSize) { slotIndex ->
        pageItems.getOrNull(slotIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                onGridBoundsMeasured(coords.windowRect())
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(cellSpacing)
        ) {
            repeat(layout.rows) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    repeat(layout.columns) { column ->
                        val slotIndex = (row * layout.columns) + column
                        val item = slots.getOrNull(slotIndex)

                        Box(
                            modifier = Modifier
                                .size(width = cellWidth, height = cellHeight)
                                .then(
                                    if (hoveredSlot == FolderDropSlot(page, slotIndex)) {
                                        Modifier
                                            .clip(RoundedCornerShape(CornerRadius.medium))
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                width = Spacing.hairline,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(CornerRadius.medium)
                                            )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            if (item != null) {
                                FolderPopupItem(
                                    item = item,
                                    isDragged = item.id == draggedItemId,
                                    showMenu = menuShownForItemId == item.id,
                                    onMenuDismiss = onMenuDismiss,
                                    onTap = { onTap(item) },
                                    onLongPress = { onLongPress(item.id) },
                                    onRemoveFromFolder = { onRemoveFromFolder(item.id) },
                                    onWindowPositionMeasured = { offset ->
                                        onWindowPositionMeasured(item.id, offset)
                                    },
                                    onDragStart = { onDragStart(item) },
                                    onDragDelta = onDragDelta,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPagerIndicator(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            val isSelected = page == currentPage
            Box(
                modifier = Modifier
                    .size(
                        width = if (isSelected) 18.dp else 8.dp,
                        height = 8.dp
                    )
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun FolderPopupItem(
    item: HomeItem,
    isDragged: Boolean,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onWindowPositionMeasured: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLongPressGestureActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                onWindowPositionMeasured(coords.localToWindow(Offset.Zero))
            }
            .alpha(if (isDragged) FOLDER_DRAG_GHOST_ALPHA else 1f)
            .detectDragGesture(
                key = item.id,
                dragThreshold = FOLDER_DRAG_THRESHOLD_PX,
                onTap = { onTap() },
                onLongPress = {
                    isLongPressGestureActive = true
                    onLongPress()
                },
                onLongPressRelease = {
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
                onDragEnd = onDragEnd,
                onDragCancel = {
                    isLongPressGestureActive = false
                    onDragCancel()
                }
            )
    ) {
        PinnedItem(
            item = item,
            onClick = {},
            onLongClick = {},
            handleLongPress = false,
            showMenu = false
        )

        ItemActionMenu(
            expanded = showMenu,
            onDismiss = onMenuDismiss,
            focusable = !isLongPressGestureActive,
            actions = listOf(
                MenuAction(
                    label = "Remove from folder",
                    icon = Icons.Filled.Delete,
                    onClick = onRemoveFromFolder,
                    isDestructive = true
                )
            )
        )
    }
}

private data class FolderDropSlot(
    val page: Int,
    val slotIndex: Int
)

private data class FolderSurfaceMetrics(
    val cellWidth: Dp,
    val cellHeight: Dp,
    val cellSpacing: Dp,
    val gridWidth: Dp,
    val gridHeight: Dp,
    val surfaceWidth: Dp,
    val surfaceHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val headerBottomSpacing: Dp,
    val indicatorTopSpacing: Dp
) {
    companion object {
        fun create(
            density: Density,
            layout: FolderGridLayout,
            pageCount: Int,
            maxWidth: Dp,
            maxHeight: Dp
        ): FolderSurfaceMetrics {
            with(density) {
                val safeMaxWidthPx = maxWidth.toPx()
                val safeMaxHeightPx = maxHeight.toPx()
                val edgeMarginPx = FOLDER_EDGE_MARGIN.toPx()

                val baseGridWidthPx = (
                    (FOLDER_BASE_CELL_WIDTH * layout.columns) +
                        (FOLDER_CELL_SPACING * (layout.columns - 1).coerceAtLeast(0))
                    ).toPx()
                val baseGridHeightPx = (
                    (FOLDER_BASE_CELL_HEIGHT * layout.rows) +
                        (FOLDER_CELL_SPACING * (layout.rows - 1).coerceAtLeast(0))
                    ).toPx()
                val baseSurfaceWidthPx = (
                    baseGridWidthPx +
                        (FOLDER_HORIZONTAL_PADDING * 2).toPx()
                    )
                val baseSurfaceHeightPx = (
                    baseGridHeightPx +
                        (FOLDER_VERTICAL_PADDING * 2).toPx() +
                        FOLDER_HEADER_HEIGHT.toPx() +
                        FOLDER_HEADER_BOTTOM_SPACING.toPx() +
                        if (pageCount > 1) {
                            FOLDER_INDICATOR_TOP_SPACING.toPx() + FOLDER_INDICATOR_HEIGHT.toPx()
                        } else {
                            0f
                        }
                    )

                val fitScale = minOf(
                    1f,
                    (safeMaxWidthPx - (edgeMarginPx * 2)) / baseSurfaceWidthPx,
                    (safeMaxHeightPx - (edgeMarginPx * 2)) / baseSurfaceHeightPx
                ).coerceAtMost(1f)

                val cellWidth = FOLDER_BASE_CELL_WIDTH * fitScale
                val cellHeight = FOLDER_BASE_CELL_HEIGHT * fitScale
                val cellSpacing = FOLDER_CELL_SPACING * fitScale
                val horizontalPadding = FOLDER_HORIZONTAL_PADDING * fitScale
                val verticalPadding = FOLDER_VERTICAL_PADDING * fitScale
                val headerBottomSpacing = FOLDER_HEADER_BOTTOM_SPACING * fitScale
                val indicatorTopSpacing = FOLDER_INDICATOR_TOP_SPACING * fitScale
                val headerHeight = FOLDER_HEADER_HEIGHT * fitScale
                val indicatorHeight = FOLDER_INDICATOR_HEIGHT * fitScale

                val gridWidth = (cellWidth * layout.columns) + (cellSpacing * (layout.columns - 1).coerceAtLeast(0))
                val gridHeight = (cellHeight * layout.rows) + (cellSpacing * (layout.rows - 1).coerceAtLeast(0))
                val surfaceWidth = gridWidth + (horizontalPadding * 2)
                val surfaceHeight = gridHeight +
                    (verticalPadding * 2) +
                    headerHeight +
                    headerBottomSpacing +
                    if (pageCount > 1) indicatorTopSpacing + indicatorHeight else 0.dp

                return FolderSurfaceMetrics(
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    cellSpacing = cellSpacing,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    horizontalPadding = horizontalPadding,
                    verticalPadding = verticalPadding,
                    headerBottomSpacing = headerBottomSpacing,
                    indicatorTopSpacing = indicatorTopSpacing
                )
            }
        }
    }
}

private fun slotIndexForPageItem(
    itemIndex: Int,
    pageSize: Int
): Int {
    if (itemIndex < 0) return 0
    return itemIndex % pageSize.coerceAtLeast(1)
}

private fun resolveHoveredSlot(
    pointer: Offset,
    currentPage: Int,
    layout: FolderGridLayout,
    gridWindowRect: Rect,
    cellSpacingPx: Float
): FolderDropSlot? {
    if (gridWindowRect == Rect.Zero) return null

    var closestSlotIndex = 0
    var closestDistance = Float.MAX_VALUE
    val spacingX = if (layout.columns > 1) cellSpacingPx else 0f
    val spacingY = if (layout.rows > 1) cellSpacingPx else 0f
    val cellWidth = (
        gridWindowRect.width - (spacingX * (layout.columns - 1).coerceAtLeast(0))
        ) / layout.columns.coerceAtLeast(1)
    val cellHeight = (
        gridWindowRect.height - (spacingY * (layout.rows - 1).coerceAtLeast(0))
        ) / layout.rows.coerceAtLeast(1)

    repeat(layout.pageSize) { slotIndex ->
        val row = slotIndex / layout.columns
        val column = slotIndex % layout.columns
        val centerX = gridWindowRect.left + (column * (cellWidth + spacingX)) + (cellWidth * 0.5f)
        val centerY = gridWindowRect.top + (row * (cellHeight + spacingY)) + (cellHeight * 0.5f)
        val dx = pointer.x - centerX
        val dy = pointer.y - centerY
        val distance = (dx * dx) + (dy * dy)

        if (distance < closestDistance) {
            closestDistance = distance
            closestSlotIndex = slotIndex
        }
    }

    return FolderDropSlot(
        page = currentPage,
        slotIndex = closestSlotIndex
    )
}

private fun resolveFolderTargetBounds(
    density: Density,
    surfaceWidth: Dp,
    surfaceHeight: Dp,
    maxWidth: Dp,
    maxHeight: Dp,
    anchorBounds: Rect?
): Rect {
    with(density) {
        val surfaceWidthPx = surfaceWidth.toPx()
        val surfaceHeightPx = surfaceHeight.toPx()
        val maxWidthPx = maxWidth.toPx()
        val maxHeightPx = maxHeight.toPx()
        val edgeMarginPx = FOLDER_EDGE_MARGIN.toPx()

        val anchorCenter = anchorBounds?.center() ?: Offset(
            x = maxWidthPx * 0.5f,
            y = maxHeightPx * 0.5f
        )

        val unclampedLeft = anchorCenter.x - (surfaceWidthPx * 0.5f)
        val unclampedTop = anchorCenter.y - (surfaceHeightPx * 0.5f)
        val left = unclampedLeft.coerceIn(
            minimumValue = edgeMarginPx,
            maximumValue = (maxWidthPx - surfaceWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        )
        val top = unclampedTop.coerceIn(
            minimumValue = edgeMarginPx,
            maximumValue = (maxHeightPx - surfaceHeightPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        )

        return Rect(
            left = left,
            top = top,
            right = left + surfaceWidthPx,
            bottom = top + surfaceHeightPx
        )
    }
}

private fun resolveAutoPageTarget(
    pointer: Offset,
    popupBounds: Rect,
    currentPage: Int,
    pageCount: Int,
    edgeThresholdPx: Float
): Int? {
    if (pageCount <= 1 || popupBounds == Rect.Zero) return null

    return when {
        pointer.x <= popupBounds.left + edgeThresholdPx && currentPage > 0 -> currentPage - 1
        pointer.x >= popupBounds.right - edgeThresholdPx && currentPage < pageCount - 1 -> currentPage + 1
        else -> null
    }
}

private fun LayoutCoordinates.windowRect(): Rect {
    val topLeft = localToWindow(Offset.Zero)
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height
    )
}

private fun Rect.center(): Offset {
    return Offset(
        x = (left + right) * 0.5f,
        y = (top + bottom) * 0.5f
    )
}

private fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress)
}

private val FOLDER_BASE_CELL_WIDTH = 108.dp
private val FOLDER_BASE_CELL_HEIGHT = 112.dp
private val FOLDER_CELL_SPACING = Spacing.smallMedium
private val FOLDER_HORIZONTAL_PADDING = Spacing.mediumLarge
private val FOLDER_VERTICAL_PADDING = Spacing.mediumLarge
private val FOLDER_HEADER_HEIGHT = 44.dp
private val FOLDER_HEADER_BOTTOM_SPACING = Spacing.medium
private val FOLDER_INDICATOR_TOP_SPACING = Spacing.medium
private val FOLDER_INDICATOR_HEIGHT = 8.dp
private val FOLDER_EDGE_MARGIN = Spacing.mediumLarge
private val FOLDER_AUTO_PAGE_EDGE_THRESHOLD = 52.dp

private const val FOLDER_DRAG_THRESHOLD_PX = 20f
private const val FOLDER_DRAG_GHOST_ALPHA = 0.18f
private const val FOLDER_PREVIEW_ALPHA = 0.92f
private const val FOLDER_DRAG_PREVIEW_Z_INDEX = 12f
private const val FOLDER_MIN_START_SCALE = 0.28f
private const val FOLDER_OPEN_ANIMATION_MS = 240
private const val FOLDER_AUTO_PAGE_DELAY_MS = 170L
