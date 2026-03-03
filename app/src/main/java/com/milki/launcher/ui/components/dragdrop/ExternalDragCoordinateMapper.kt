package com.milki.launcher.ui.components.dragdrop

import android.view.DragEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * ExternalDragCoordinateMapper.kt - Coordinate normalization helper for DragEvent.
 *
 * WHY THIS FILE EXISTS:
 * Android DragEvent x/y can be reported in different coordinate spaces depending
 * on API path / OEM behavior (already-local vs window-like vs screen-like).
 * This utility normalizes event coordinates into target-view local space.
 */
object ExternalDragCoordinateMapper {

    /**
     * Converts DragEvent coordinates into coordinates local to [targetView].
     */
    fun toLocalOffset(targetView: View, event: DragEvent): Offset {
        val rawOffset = Offset(event.x, event.y)
        val viewWidth = targetView.width.toFloat().coerceAtLeast(1f)
        val viewHeight = targetView.height.toFloat().coerceAtLeast(1f)

        fun isInsideBounds(offset: Offset): Boolean {
            return offset.x in 0f..viewWidth && offset.y in 0f..viewHeight
        }

        if (isInsideBounds(rawOffset)) {
            return rawOffset
        }

        val locationInWindow = IntArray(2)
        targetView.getLocationInWindow(locationInWindow)
        val windowCandidate = Offset(
            x = event.x - locationInWindow[0].toFloat(),
            y = event.y - locationInWindow[1].toFloat()
        )

        val locationOnScreen = IntArray(2)
        targetView.getLocationOnScreen(locationOnScreen)
        val screenCandidate = Offset(
            x = event.x - locationOnScreen[0].toFloat(),
            y = event.y - locationOnScreen[1].toFloat()
        )

        if (isInsideBounds(windowCandidate)) return windowCandidate
        if (isInsideBounds(screenCandidate)) return screenCandidate

        fun outOfBoundsDistance(offset: Offset): Float {
            val dx = when {
                offset.x < 0f -> abs(offset.x)
                offset.x > viewWidth -> abs(offset.x - viewWidth)
                else -> 0f
            }
            val dy = when {
                offset.y < 0f -> abs(offset.y)
                offset.y > viewHeight -> abs(offset.y - viewHeight)
                else -> 0f
            }
            return dx + dy
        }

        return if (outOfBoundsDistance(windowCandidate) <= outOfBoundsDistance(screenCandidate)) {
            windowCandidate
        } else {
            screenCandidate
        }
    }
}
