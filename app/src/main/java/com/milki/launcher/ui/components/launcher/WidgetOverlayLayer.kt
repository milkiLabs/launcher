package com.milki.launcher.ui.components.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.GridBounds
import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.widget.WidgetFrame
import com.milki.launcher.domain.widget.WidgetTransformHandle
import com.milki.launcher.domain.widget.applyWidgetTransformHandle
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

private val WidgetResizeBorderHitTarget = 32.dp
private val WidgetResizeHandles = listOf(
    Alignment.TopStart to WidgetTransformHandle.TopLeft,
    Alignment.TopCenter to WidgetTransformHandle.Top,
    Alignment.TopEnd to WidgetTransformHandle.TopRight,
    Alignment.CenterStart to WidgetTransformHandle.Left,
    Alignment.CenterEnd to WidgetTransformHandle.Right,
    Alignment.BottomStart to WidgetTransformHandle.BottomLeft,
    Alignment.BottomCenter to WidgetTransformHandle.Bottom,
    Alignment.BottomEnd to WidgetTransformHandle.BottomRight
)

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
                    onUpdateWidgetFrame(
                        widgetItem.id,
                        if (widgetItem.displayMode == WidgetDisplayMode.PopupIcon) {
                            widgetItem.position
                        } else {
                            frame.position
                        },
                        frame.span
                    )
                },
                onCancelTransform = onCancelTransform
            )
        } else {
            onCancelTransform()
        }
    }
}

/**
 * Immutable snapshot of an in-progress widget resize session.
 */
private data class WidgetResizeDraft(
    val originalFrame: WidgetFrame,
    val draftFrame: WidgetFrame,
    val lastValidFrame: WidgetFrame,
    val isDraftValid: Boolean = true
)

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

    val isPopupWidget = widgetItem.displayMode == WidgetDisplayMode.PopupIcon

    // Draft transform state bundled into a single remembered value.
    //
    // The previous implementation declared four separate remember blocks that
    // each repeated the same six reset keys, so adding a key meant editing four
    // places and any drift between them would desynchronize the fields. One
    // keyed state resets atomically when the session/widget/grid identity
    // changes.
    var resizeDraft by remember(
        widgetItem.id,
        widgetItem.position,
        widgetItem.span,
        widgetItem.displayMode,
        gridColumns,
        maxVisibleRows
    ) {
        val previewPosition = if (isPopupWidget) {
            GridBounds(gridColumns, maxVisibleRows).clamp(widgetItem.position, widgetItem.span)
        } else {
            widgetItem.position
        }
        val originalFrame = WidgetFrame(position = previewPosition, span = widgetItem.span)
        mutableStateOf(
            WidgetResizeDraft(
                originalFrame = originalFrame,
                draftFrame = originalFrame,
                lastValidFrame = originalFrame
            )
        )
    }
    val draftOccupancy = remember(items, widgetItem.id) {
        GridOccupancy.fromItems(items, excludeItemId = widgetItem.id)
    }

    fun isFrameFree(frame: WidgetFrame): Boolean {
        if (isPopupWidget) return true

        return draftOccupancy.isSpanFree(frame.position, frame.span)
    }

    fun updateDraft(frame: WidgetFrame) {
        val valid = isFrameFree(frame)
        resizeDraft = resizeDraft.copy(
            draftFrame = frame,
            isDraftValid = valid,
            lastValidFrame = if (valid) frame else resizeDraft.lastValidFrame
        )
    }

    fun settleDraftAfterGesture() {
        val draft = resizeDraft
        if (!draft.isDraftValid) {
            resizeDraft = draft.copy(draftFrame = draft.lastValidFrame, isDraftValid = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures {
                    onConfirmTransform(resizeDraft.draftFrame)
                }
            }
    )

    val originX = (resizeDraft.draftFrame.position.column * cellWidthPx).roundToInt()
    val originY = (resizeDraft.draftFrame.position.row * cellHeightPx).roundToInt()
    val frameWidth = (resizeDraft.draftFrame.span.columns * cellWidthPx).roundToInt()
    val frameHeight = (resizeDraft.draftFrame.span.rows * cellHeightPx).roundToInt()
    val frameColor = if (resizeDraft.isDraftValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

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
                .then(
                    if (isPopupWidget) {
                        Modifier
                    } else {
                        Modifier.widgetTransformDrag(
                            handle = WidgetTransformHandle.Body,
                            cellWidthPx = cellWidthPx,
                            cellHeightPx = cellHeightPx,
                            gridColumns = gridColumns,
                            maxVisibleRows = maxVisibleRows,
                            draftFrame = resizeDraft.draftFrame,
                            updateDraft = ::updateDraft,
                            settleDraftAfterGesture = ::settleDraftAfterGesture
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${resizeDraft.draftFrame.span.columns} x ${resizeDraft.draftFrame.span.rows}",
                color = frameColor,
                style = MaterialTheme.typography.titleSmall
            )
        }

        WidgetResizeHandles.forEach { (alignment, handle) ->
            WidgetTransformBorderHandleNode(
                alignment = alignment,
                handle = handle,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                gridColumns = gridColumns,
                maxVisibleRows = maxVisibleRows,
                draftFrame = resizeDraft.draftFrame,
                updateDraft = ::updateDraft,
                settleDraftAfterGesture = ::settleDraftAfterGesture
            )
        }

        WidgetResizeHandles.forEach { (alignment, handle) ->
            WidgetTransformHandleNode(
                alignment = alignment,
                handle = handle,
                frameColor = frameColor,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                gridColumns = gridColumns,
                maxVisibleRows = maxVisibleRows,
                draftFrame = resizeDraft.draftFrame,
                updateDraft = ::updateDraft,
                settleDraftAfterGesture = ::settleDraftAfterGesture
            )
        }
    }
}

@Composable
private fun BoxScope.WidgetTransformBorderHandleNode(
    alignment: Alignment,
    handle: WidgetTransformHandle,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    maxVisibleRows: Int,
    draftFrame: WidgetFrame,
    updateDraft: (WidgetFrame) -> Unit,
    settleDraftAfterGesture: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(
                x = alignment.horizontalHandleOffset(WidgetResizeBorderHitTarget / 2),
                y = alignment.verticalHandleOffset(WidgetResizeBorderHitTarget / 2)
            )
            .then(
                when (alignment) {
                    Alignment.TopCenter, Alignment.BottomCenter -> Modifier
                        .fillMaxWidth()
                        .height(WidgetResizeBorderHitTarget)

                    Alignment.CenterStart, Alignment.CenterEnd -> Modifier
                        .fillMaxHeight()
                        .width(WidgetResizeBorderHitTarget)

                    else -> Modifier.size(WidgetResizeBorderHitTarget)
                }
            )
            .zIndex(if (handle.isCorner) 52.6f else 52.5f)
            .widgetTransformDrag(
                handle = handle,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                gridColumns = gridColumns,
                maxVisibleRows = maxVisibleRows,
                draftFrame = resizeDraft.draftFrame,
                updateDraft = updateDraft,
                settleDraftAfterGesture = settleDraftAfterGesture
            )
    )
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
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(
                x = alignment.horizontalHandleOffset(Spacing.smallMedium),
                y = alignment.verticalHandleOffset(Spacing.smallMedium)
            )
            .size(IconSize.standard)
            .background(
                color = frameColor,
                shape = CircleShape
            )
            .border(
                width = Spacing.hairline,
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            )
            .zIndex(53f)
            .widgetTransformDrag(
                handle = handle,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                gridColumns = gridColumns,
                maxVisibleRows = maxVisibleRows,
                draftFrame = resizeDraft.draftFrame,
                updateDraft = updateDraft,
                settleDraftAfterGesture = settleDraftAfterGesture
            )
    )
}

@Composable
private fun Modifier.widgetTransformDrag(
    handle: WidgetTransformHandle,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gridColumns: Int,
    maxVisibleRows: Int,
    draftFrame: WidgetFrame,
    updateDraft: (WidgetFrame) -> Unit,
    settleDraftAfterGesture: () -> Unit
): Modifier {
    val latestDraftFrame by rememberUpdatedState(draftFrame)
    val latestUpdateDraft by rememberUpdatedState(updateDraft)
    val latestSettleDraftAfterGesture by rememberUpdatedState(settleDraftAfterGesture)

    return pointerInput(handle, cellWidthPx, cellHeightPx, gridColumns, maxVisibleRows) {
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
}

private fun Alignment.horizontalHandleOffset(distance: Dp): Dp = when (this) {
    Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart -> -distance
    Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd -> distance
    else -> 0.dp
}

private fun Alignment.verticalHandleOffset(distance: Dp): Dp = when (this) {
    Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> -distance
    Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> distance
    else -> 0.dp
}

private val WidgetTransformHandle.isCorner: Boolean
    get() = horizontalDirection != 0 && verticalDirection != 0
