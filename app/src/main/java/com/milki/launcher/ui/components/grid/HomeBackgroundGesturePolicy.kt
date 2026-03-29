package com.milki.launcher.ui.components.grid

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Policy for empty-space gestures on the home grid.
 *
 * Item gestures must always win over background gestures, and transient UI such
 * as menus/resize overlays should block new background interactions entirely.
 */
internal data class HomeBackgroundGesturePolicy(
    val canStartBackgroundGesture: Boolean,
    val canSwipeUp: Boolean
) {
    fun shouldTrackGesture(): Boolean {
        return canStartBackgroundGesture
    }
}

internal fun Offset.exceedsTouchSlop(touchSlopPx: Float): Boolean {
    return abs(x) > touchSlopPx || abs(y) > touchSlopPx
}

internal fun Offset.isSwipeUpGesture(
    minimumDistancePx: Float,
    verticalDominanceRatio: Float = 1.25f
): Boolean {
    return y <= -minimumDistancePx && -y > abs(x) * verticalDominanceRatio
}
