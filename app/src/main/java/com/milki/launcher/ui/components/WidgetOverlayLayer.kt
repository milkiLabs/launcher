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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.widget.WidgetFrame
import com.milki.launcher.domain.widget.WidgetTransformHandle
import com.milki.launcher.domain.widget.applyWidgetTransformHandle
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlin.math.roundToInt

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
