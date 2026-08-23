package com.milki.launcher.ui.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

internal fun LayoutCoordinates.windowRect(): Rect {
    val topLeft = localToWindow(Offset.Zero)
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height
    )
}

internal fun Rect.center(): Offset {
    return Offset(
        x = (left + right) * 0.5f,
        y = (top + bottom) * 0.5f
    )
}

internal fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress)
}
