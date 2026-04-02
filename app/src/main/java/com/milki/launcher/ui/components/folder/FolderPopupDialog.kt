package com.milki.launcher.ui.components.folder

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.launcher.PinnedItem
import com.milki.launcher.ui.components.dragdrop.startExternalFolderItemDrag
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

    val nameFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current
    val hostView = LocalView.current
    val density = LocalDensity.current

    fun resetDragSessionState() {
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

        Box(modifier = Modifier.fillMaxSize()) {
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

                                    resetDragSessionState()
                                },
                                onDragCancel = {
                                    resetDragSessionState()
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
