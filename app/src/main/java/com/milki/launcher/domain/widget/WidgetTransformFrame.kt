package com.milki.launcher.domain.widget

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan

data class WidgetFrame(
    val position: GridPosition,
    val span: GridSpan
)

enum class WidgetTransformHandle(
    val horizontalDirection: Int,
    val verticalDirection: Int
) {
    Body(horizontalDirection = 0, verticalDirection = 0),
    Left(horizontalDirection = -1, verticalDirection = 0),
    Top(horizontalDirection = 0, verticalDirection = -1),
    Right(horizontalDirection = 1, verticalDirection = 0),
    Bottom(horizontalDirection = 0, verticalDirection = 1),
    TopLeft(horizontalDirection = -1, verticalDirection = -1),
    TopRight(horizontalDirection = 1, verticalDirection = -1),
    BottomLeft(horizontalDirection = -1, verticalDirection = 1),
    BottomRight(horizontalDirection = 1, verticalDirection = 1)
}

fun applyWidgetTransformHandle(
    startFrame: WidgetFrame,
    handle: WidgetTransformHandle,
    columnDelta: Int,
    rowDelta: Int,
    maxColumns: Int,
    maxRows: Int
): WidgetFrame {
    require(maxColumns >= 1) { "maxColumns must be at least 1" }
    require(maxRows >= 1) { "maxRows must be at least 1" }

    val startLeft = startFrame.position.column
    val startTop = startFrame.position.row
    val startRight = startLeft + startFrame.span.columns
    val startBottom = startTop + startFrame.span.rows

    if (handle == WidgetTransformHandle.Body) {
        val maxLeft = (maxColumns - startFrame.span.columns).coerceAtLeast(0)
        val maxTop = (maxRows - startFrame.span.rows).coerceAtLeast(0)
        return WidgetFrame(
            position = GridPosition(
                row = (startTop + rowDelta).coerceIn(0, maxTop),
                column = (startLeft + columnDelta).coerceIn(0, maxLeft)
            ),
            span = startFrame.span
        )
    }

    var left = startLeft
    var top = startTop
    var right = startRight
    var bottom = startBottom

    if (handle.horizontalDirection < 0) {
        left = (startLeft + columnDelta).coerceIn(0, startRight - 1)
    } else if (handle.horizontalDirection > 0) {
        right = (startRight + columnDelta).coerceIn(startLeft + 1, maxColumns)
    }

    if (handle.verticalDirection < 0) {
        top = (startTop + rowDelta).coerceIn(0, startBottom - 1)
    } else if (handle.verticalDirection > 0) {
        bottom = (startBottom + rowDelta).coerceIn(startTop + 1, maxRows)
    }

    return WidgetFrame(
        position = GridPosition(row = top, column = left),
        span = GridSpan(
            columns = (right - left).coerceAtLeast(1),
            rows = (bottom - top).coerceAtLeast(1)
        )
    )
}
