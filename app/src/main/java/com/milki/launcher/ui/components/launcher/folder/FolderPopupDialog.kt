package com.milki.launcher.ui.components.launcher.folder

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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.launcher.PinnedItem
import com.milki.launcher.ui.interaction.dragdrop.startExternalFolderItemDrag
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val FOLDER_SCRIM_BASE_ALPHA = 0.18f
private const val FOLDER_SCRIM_PROGRESS_ALPHA = 0.34f
private const val FOLDER_SURFACE_BASE_ALPHA = 0.82f
private const val FOLDER_SURFACE_FINAL_ALPHA = 1f
private const val FOLDER_CELL_CENTER_RATIO = 0.5f

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

    val dragState = rememberFolderDialogDragState()
    val itemWindowOffsets = remember { mutableStateMapOf<String, Offset>() }

    val nameFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current
    val hostView = LocalView.current
    val density = LocalDensity.current

    SyncFolderDialogState(
        folder = folder,
        isDraggingInternally = isDraggingInternally,
        isEditingName = isEditingName,
        updateChildren = { localChildren = it },
        updateName = { editingName = it }
    )

    val layout = remember(localChildren.size) {
        folderGridLayoutForItemCount(localChildren.size)
    }
    val pagerState = rememberPagerState(initialPage = 0) { layout.pageCount }

    HandleFolderPagerBounds(
        layout = layout,
        pagerState = pagerState
    )
    HandleFolderAutoPaging(
        dragState = dragState,
        pagerState = pagerState
    )

    val openProgress = rememberFolderOpenProgress()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = rememberFolderSurfaceMetrics(
            localChildrenSize = localChildren.size,
            layout = layout,
            density = density,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        val targetBoundsPx = rememberFolderTargetBounds(
            anchorBounds = anchorBounds,
            metrics = metrics,
            density = density,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        val transform = rememberFolderSurfaceTransform(
            anchorBounds = anchorBounds,
            targetBoundsPx = targetBoundsPx
        )

        Box(modifier = Modifier.fillMaxSize()) {
            FolderPopupScrim(
                openProgress = openProgress,
                onClose = onClose
            )

            FolderPopupSurface(
                metrics = metrics,
                targetBoundsPx = targetBoundsPx,
                transform = transform,
                openProgress = openProgress,
                onPopupBoundsMeasured = { dragState.popupWindowRect = it }
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

                    FolderPagerContent(
                        folder = folder,
                        layout = layout,
                        localChildren = localChildren,
                        metrics = metrics,
                        pagerState = pagerState,
                        dragState = dragState,
                        menuShownForItemId = menuShownForItemId,
                        itemWindowOffsets = itemWindowOffsets,
                        density = density,
                        hapticFeedback = hapticFeedback,
                        hostView = hostView,
                        onClose = onClose,
                        onItemClick = onItemClick,
                        onRemoveItemFromFolder = onRemoveItemFromFolder,
                        onReorderFolderItems = { reordered ->
                            localChildren = reordered
                            onReorderFolderItems(reordered)
                        },
                        onMenuShownForItemIdChange = { menuShownForItemId = it },
                        onDraggingInternallyChange = { isDraggingInternally = it }
                    )

                    if (layout.pageCount > 1) {
                        Spacer(modifier = Modifier.height(metrics.indicatorTopSpacing))
                        FolderPagerIndicator(
                            pageCount = layout.pageCount,
                            currentPage = pagerState.currentPage
                        )
                    }
                }
            }

            FolderDragPreview(
                dragState = dragState,
                metrics = metrics
            )
        }
    }
}

@Composable
private fun SyncFolderDialogState(
    folder: HomeItem.FolderItem,
    isDraggingInternally: Boolean,
    isEditingName: Boolean,
    updateChildren: (List<HomeItem>) -> Unit,
    updateName: (String) -> Unit
) {
    LaunchedEffect(folder.children) {
        if (!isDraggingInternally) {
            updateChildren(folder.children)
        }
    }

    LaunchedEffect(folder.name) {
        if (!isEditingName) {
            updateName(folder.name)
        }
    }
}

@Composable
private fun HandleFolderPagerBounds(
    layout: FolderGridLayout,
    pagerState: PagerState
) {
    LaunchedEffect(layout.pageCount) {
        val lastPage = (layout.pageCount - 1).coerceAtLeast(0)
        if (pagerState.currentPage > lastPage) {
            pagerState.scrollToPage(lastPage)
        }
    }
}

@Composable
private fun HandleFolderAutoPaging(
    dragState: FolderDialogDragState,
    pagerState: PagerState
) {
    LaunchedEffect(dragState.pendingAutoPage, dragState.draggedItemId) {
        val targetPage = dragState.pendingAutoPage ?: return@LaunchedEffect
        if (dragState.draggedItemId == null) return@LaunchedEffect

        delay(FOLDER_AUTO_PAGE_DELAY_MS)
        if (dragState.pendingAutoPage == targetPage && pagerState.currentPage != targetPage) {
            dragState.isAutoPaging = true
            pagerState.animateScrollToPage(targetPage)
            pagerState.scrollToPage(targetPage)
            dragState.isAutoPaging = false
        }
        if (dragState.pendingAutoPage == targetPage) {
            dragState.pendingAutoPage = null
        }
    }
}

@Composable
private fun rememberFolderOpenProgress(): Float {
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
    return openProgress
}

@Composable
private fun rememberFolderSurfaceMetrics(
    localChildrenSize: Int,
    layout: FolderGridLayout,
    density: Density,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp
): FolderSurfaceMetrics {
    return remember(
        localChildrenSize,
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
}

@Composable
private fun rememberFolderTargetBounds(
    anchorBounds: Rect?,
    metrics: FolderSurfaceMetrics,
    density: Density,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp
): Rect {
    return remember(
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
}

@Composable
private fun rememberFolderSurfaceTransform(
    anchorBounds: Rect?,
    targetBoundsPx: Rect
): FolderSurfaceTransform {
    return remember(anchorBounds, targetBoundsPx) {
        val fallbackAnchorBounds = targetBoundsPx
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

        FolderSurfaceTransform(
            anchorCenter = anchorCenter,
            targetCenter = targetCenter,
            startScaleX = startScaleX,
            startScaleY = startScaleY
        )
    }
}

@Composable
private fun FolderPopupScrim(
    openProgress: Float,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = FOLDER_SCRIM_BASE_ALPHA + (FOLDER_SCRIM_PROGRESS_ALPHA * openProgress)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
    )
}

@Composable
private fun FolderPopupSurface(
    metrics: FolderSurfaceMetrics,
    targetBoundsPx: Rect,
    transform: FolderSurfaceTransform,
    openProgress: Float,
    onPopupBoundsMeasured: (Rect) -> Unit,
    content: @Composable () -> Unit
) {
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
                val startTranslation = transform.anchorCenter - transform.targetCenter
                translationX = startTranslation.x * (1f - openProgress)
                translationY = startTranslation.y * (1f - openProgress)
                scaleX = lerp(transform.startScaleX, 1f, openProgress)
                scaleY = lerp(transform.startScaleY, 1f, openProgress)
                alpha = lerp(FOLDER_SURFACE_BASE_ALPHA, FOLDER_SURFACE_FINAL_ALPHA, openProgress)
            }
            .shadow(
                elevation = Spacing.mediumLarge,
                shape = RoundedCornerShape(CornerRadius.large),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            .onGloballyPositioned { coords ->
                onPopupBoundsMeasured(coords.windowRect())
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        shape = RoundedCornerShape(CornerRadius.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none)
    ) {
        content()
    }
}

@Composable
private fun FolderPagerContent(
    folder: HomeItem.FolderItem,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    metrics: FolderSurfaceMetrics,
    pagerState: PagerState,
    dragState: FolderDialogDragState,
    menuShownForItemId: String?,
    itemWindowOffsets: MutableMap<String, Offset>,
    density: Density,
    hapticFeedback: HapticFeedback,
    hostView: android.view.View,
    onClose: () -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onRemoveItemFromFolder: (String) -> Unit,
    onReorderFolderItems: (List<HomeItem>) -> Unit,
    onMenuShownForItemIdChange: (String?) -> Unit,
    onDraggingInternallyChange: (Boolean) -> Unit
) {
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
            userScrollEnabled = dragState.draggedItemId == null && layout.pageCount > 1
        ) { page ->
            FolderPagerPage(
                page = page,
                folder = folder,
                layout = layout,
                localChildren = localChildren,
                metrics = metrics,
                pagerState = pagerState,
                dragState = dragState,
                menuShownForItemId = menuShownForItemId,
                itemWindowOffsets = itemWindowOffsets,
                density = density,
                hapticFeedback = hapticFeedback,
                hostView = hostView,
                onClose = onClose,
                onItemClick = onItemClick,
                onRemoveItemFromFolder = onRemoveItemFromFolder,
                onReorderFolderItems = onReorderFolderItems,
                onMenuShownForItemIdChange = onMenuShownForItemIdChange,
                onDraggingInternallyChange = onDraggingInternallyChange
            )
        }
    }
}

@Composable
private fun FolderPagerPage(
    page: Int,
    folder: HomeItem.FolderItem,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    metrics: FolderSurfaceMetrics,
    pagerState: PagerState,
    dragState: FolderDialogDragState,
    menuShownForItemId: String?,
    itemWindowOffsets: MutableMap<String, Offset>,
    density: Density,
    hapticFeedback: HapticFeedback,
    hostView: android.view.View,
    onClose: () -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onRemoveItemFromFolder: (String) -> Unit,
    onReorderFolderItems: (List<HomeItem>) -> Unit,
    onMenuShownForItemIdChange: (String?) -> Unit,
    onDraggingInternallyChange: (Boolean) -> Unit
) {
    val callbacks = rememberFolderPagerPageCallbacks(
        page = page,
        folder = folder,
        layout = layout,
        localChildren = localChildren,
        metrics = metrics,
        pagerState = pagerState,
        dragState = dragState,
        itemWindowOffsets = itemWindowOffsets,
        density = density,
        hapticFeedback = hapticFeedback,
        hostView = hostView,
        onClose = onClose,
        onRemoveItemFromFolder = onRemoveItemFromFolder,
        onReorderFolderItems = onReorderFolderItems,
        onMenuShownForItemIdChange = onMenuShownForItemIdChange,
        onDraggingInternallyChange = onDraggingInternallyChange
    )

    FolderGridPage(
        page = page,
        layout = layout,
        localChildren = localChildren,
        cellWidth = metrics.cellWidth,
        cellHeight = metrics.cellHeight,
        cellSpacing = metrics.cellSpacing,
        draggedItemId = dragState.draggedItemId,
        hoveredSlot = dragState.hoveredSlot,
        menuShownForItemId = menuShownForItemId,
        onGridBoundsMeasured = callbacks.onGridBoundsMeasured,
        onMenuDismiss = callbacks.onMenuDismiss,
        onTap = onItemClick,
        onLongPress = callbacks.onLongPress,
        onRemoveFromFolder = callbacks.onRemoveFromFolder,
        onWindowPositionMeasured = callbacks.onWindowPositionMeasured,
        onExternalDragStarted = onClose,
        onDragStart = callbacks.onDragStart,
        onDragDelta = callbacks.onDragDelta,
        onDragEnd = callbacks.onDragEnd,
        onDragCancel = callbacks.onDragCancel
    )
}

private data class FolderPagerPageCallbacks(
    val onGridBoundsMeasured: (Rect) -> Unit,
    val onMenuDismiss: () -> Unit,
    val onLongPress: (String) -> Unit,
    val onRemoveFromFolder: (String) -> Unit,
    val onWindowPositionMeasured: (String, Offset) -> Unit,
    val onDragStart: (HomeItem) -> Unit,
    val onDragDelta: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit
)

@Suppress("LongMethod")
@Composable
private fun rememberFolderPagerPageCallbacks(
    page: Int,
    folder: HomeItem.FolderItem,
    layout: FolderGridLayout,
    localChildren: List<HomeItem>,
    metrics: FolderSurfaceMetrics,
    pagerState: PagerState,
    dragState: FolderDialogDragState,
    itemWindowOffsets: MutableMap<String, Offset>,
    density: Density,
    hapticFeedback: HapticFeedback,
    hostView: android.view.View,
    onClose: () -> Unit,
    onRemoveItemFromFolder: (String) -> Unit,
    onReorderFolderItems: (List<HomeItem>) -> Unit,
    onMenuShownForItemIdChange: (String?) -> Unit,
    onDraggingInternallyChange: (Boolean) -> Unit
): FolderPagerPageCallbacks {
    return remember(
        page,
        folder,
        layout,
        localChildren,
        metrics,
        pagerState,
        dragState,
        itemWindowOffsets,
        density,
        hapticFeedback,
        hostView,
        onClose,
        onRemoveItemFromFolder,
        onReorderFolderItems,
        onMenuShownForItemIdChange,
        onDraggingInternallyChange
    ) {
        FolderPagerPageCallbacks(
            onGridBoundsMeasured = { pageBounds ->
                if (page == pagerState.currentPage) {
                    dragState.gridWindowRect = pageBounds
                }
            },
            onMenuDismiss = { onMenuShownForItemIdChange(null) },
            onLongPress = { itemId ->
                onMenuShownForItemIdChange(itemId)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onRemoveFromFolder = { itemId ->
                onMenuShownForItemIdChange(null)
                onRemoveItemFromFolder(itemId)
            },
            onWindowPositionMeasured = { itemId, windowOffset ->
                itemWindowOffsets[itemId] = windowOffset
            },
            onDragStart = { item ->
                startFolderPageDrag(
                    item = item,
                    page = page,
                    localChildren = localChildren,
                    itemWindowOffsets = itemWindowOffsets,
                    metrics = metrics,
                    density = density,
                    dragState = dragState,
                    hapticFeedback = hapticFeedback,
                    onMenuShownForItemIdChange = onMenuShownForItemIdChange,
                    onDraggingInternallyChange = onDraggingInternallyChange
                )
            },
            onDragDelta = { delta ->
                handleFolderItemDragDelta(
                    delta = delta,
                    dragState = dragState,
                    pagerState = pagerState,
                    layout = layout,
                    metrics = metrics,
                    density = density,
                    folderId = folder.id,
                    hostView = hostView,
                    onClose = onClose
                )
            },
            onDragEnd = {
                handleFolderItemDragEnd(
                    dragState = dragState,
                    pagerState = pagerState,
                    layout = layout,
                    metrics = metrics,
                    density = density,
                    localChildren = localChildren,
                    onReorderFolderItems = onReorderFolderItems,
                    hapticFeedback = hapticFeedback,
                    onDraggingInternallyChange = onDraggingInternallyChange
                )
            },
            onDragCancel = {
                dragState.reset()
                onDraggingInternallyChange(false)
            }
        )
    }
}

private fun startFolderPageDrag(
    item: HomeItem,
    page: Int,
    localChildren: List<HomeItem>,
    itemWindowOffsets: Map<String, Offset>,
    metrics: FolderSurfaceMetrics,
    density: Density,
    dragState: FolderDialogDragState,
    hapticFeedback: HapticFeedback,
    onMenuShownForItemIdChange: (String?) -> Unit,
    onDraggingInternallyChange: (Boolean) -> Unit
) {
    onDraggingInternallyChange(true)
    dragState.startInternalDrag(
        item = item,
        page = page,
        localChildren = localChildren,
        itemWindowOffsets = itemWindowOffsets,
        metrics = metrics,
        density = density
    )
    onMenuShownForItemIdChange(null)
    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
}

private fun handleFolderItemDragDelta(
    delta: Offset,
    dragState: FolderDialogDragState,
    pagerState: PagerState,
    layout: FolderGridLayout,
    metrics: FolderSurfaceMetrics,
    density: Density,
    folderId: String,
    hostView: android.view.View,
    onClose: () -> Unit
) {
    dragState.dragOffset += delta

    if (dragState.isPlatformDragActive) {
        return
    }

    val dragProbePos = dragState.dragStartWindowPos + dragState.dragProbeOffset + dragState.dragOffset
    val isNowOutside = !dragState.popupWindowRect.contains(dragProbePos)

    if (isNowOutside) {
        dragState.isDraggingOut = true
        val escapedItem = dragState.dragOutItem
        if (escapedItem != null) {
            dragState.isPlatformDragActive = true
            startExternalFolderItemDrag(
                hostView = hostView,
                folderId = folderId,
                item = escapedItem
            )
            onClose()
        }
        return
    }

    dragState.isDraggingOut = false
    dragState.hoveredSlot = resolveHoveredSlot(
        pointer = dragProbePos,
        currentPage = pagerState.currentPage,
        layout = layout,
        gridWindowRect = dragState.gridWindowRect,
        cellSpacingPx = with(density) { metrics.cellSpacing.toPx() }
    )

    if (!dragState.isAutoPaging) {
        dragState.pendingAutoPage = resolveAutoPageTarget(
            pointer = dragProbePos,
            popupBounds = dragState.popupWindowRect,
            currentPage = pagerState.currentPage,
            pageCount = layout.pageCount,
            edgeThresholdPx = with(density) {
                FOLDER_AUTO_PAGE_EDGE_THRESHOLD.toPx()
            }
        )
    }
}

private fun handleFolderItemDragEnd(
    dragState: FolderDialogDragState,
    pagerState: PagerState,
    layout: FolderGridLayout,
    metrics: FolderSurfaceMetrics,
    density: Density,
    localChildren: List<HomeItem>,
    onReorderFolderItems: (List<HomeItem>) -> Unit,
    hapticFeedback: HapticFeedback,
    onDraggingInternallyChange: (Boolean) -> Unit
) {
    val draggedItem = dragState.dragOutItem

    if (!dragState.isPlatformDragActive && draggedItem != null) {
        val dropSlot = dragState.hoveredSlot ?: resolveHoveredSlot(
            pointer = dragState.dragStartWindowPos + dragState.dragProbeOffset + dragState.dragOffset,
            currentPage = pagerState.currentPage,
            layout = layout,
            gridWindowRect = dragState.gridWindowRect,
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
                onReorderFolderItems(reordered)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    dragState.reset()
    onDraggingInternallyChange(false)
}

@Composable
private fun FolderDragPreview(
    dragState: FolderDialogDragState,
    metrics: FolderSurfaceMetrics
) {
    val draggedItem = dragState.dragOutItem
    if (draggedItem != null && dragState.draggedItemId != null && !dragState.isDraggingOut) {
        val previewOffset = dragState.dragStartWindowPos + dragState.dragOffset
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
                handleLongPress = false
            )
        }
    }
}

private class FolderDialogDragState {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var dragStartWindowPos by mutableStateOf(Offset.Zero)
    var dragProbeOffset by mutableStateOf(Offset.Zero)
    var dragOutItem by mutableStateOf<HomeItem?>(null)
    var isDraggingOut by mutableStateOf(false)
    var popupWindowRect by mutableStateOf(Rect.Zero)
    var gridWindowRect by mutableStateOf(Rect.Zero)
    var hoveredSlot by mutableStateOf<FolderDropSlot?>(null)
    var pendingAutoPage by mutableStateOf<Int?>(null)
    var isAutoPaging by mutableStateOf(false)
    var isPlatformDragActive by mutableStateOf(false)

    fun startInternalDrag(
        item: HomeItem,
        page: Int,
        localChildren: List<HomeItem>,
        itemWindowOffsets: Map<String, Offset>,
        metrics: FolderSurfaceMetrics,
        density: Density
    ) {
        draggedItemId = item.id
        dragOutItem = item
        dragOffset = Offset.Zero
        dragStartWindowPos = itemWindowOffsets[item.id] ?: Offset.Zero
        dragProbeOffset = with(density) {
            Offset(
                x = metrics.cellWidth.toPx() * FOLDER_CELL_CENTER_RATIO,
                y = metrics.cellHeight.toPx() * FOLDER_CELL_CENTER_RATIO
            )
        }
        isDraggingOut = false
        isPlatformDragActive = false
        hoveredSlot = FolderDropSlot(
            page = page,
            slotIndex = slotIndexForPageItem(
                itemIndex = localChildren.indexOfFirst { it.id == item.id },
                pageSize = layoutPageSizeOrDefault(localChildren)
            )
        )
        pendingAutoPage = null
        isAutoPaging = false
    }

    fun reset() {
        draggedItemId = null
        dragOffset = Offset.Zero
        dragProbeOffset = Offset.Zero
        dragOutItem = null
        isDraggingOut = false
        isPlatformDragActive = false
        hoveredSlot = null
        pendingAutoPage = null
        isAutoPaging = false
    }

    private fun layoutPageSizeOrDefault(localChildren: List<HomeItem>): Int {
        return folderGridLayoutForItemCount(localChildren.size).pageSize
    }
}

@Composable
private fun rememberFolderDialogDragState(): FolderDialogDragState {
    return remember { FolderDialogDragState() }
}

private data class FolderSurfaceTransform(
    val anchorCenter: Offset,
    val targetCenter: Offset,
    val startScaleX: Float,
    val startScaleY: Float
)
