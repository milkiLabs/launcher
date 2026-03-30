package com.milki.launcher.ui.components.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.findOccupantAt
import kotlinx.coroutines.withTimeoutOrNull

private enum class BackgroundGestureOutcome {
    Released,
    SwipeUp,
    Moved,
    Cancelled
}

internal fun Modifier.detectHomeBackgroundGestures(
    key: Any? = null,
    items: List<HomeItem>,
    layoutMetrics: AppDragDropLayoutMetrics,
    policy: HomeBackgroundGesturePolicy,
    onEmptyAreaLongPress: (Offset) -> Unit,
    swipeUpThresholdPx: Float,
    onSwipeUp: () -> Unit
): Modifier {
    return pointerInput(key, items, layoutMetrics, policy, swipeUpThresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pressedCell = layoutMetrics.pixelToCell(down.position)
            val startCellOccupied = items.findOccupantAt(pressedCell) != null

            if (!policy.shouldTrackGesture()) {
                return@awaitEachGesture
            }

            val outcome = awaitBackgroundGestureOutcome(
                pointerId = down.id,
                startPosition = down.position,
                touchSlopPx = viewConfiguration.touchSlop,
                swipeUpThresholdPx = swipeUpThresholdPx,
                longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis.toLong(),
                canSwipeUp = policy.canSwipeUp
            )

            when (outcome) {
                null -> {
                    if (!startCellOccupied) {
                        onEmptyAreaLongPress(down.position)
                    }
                    awaitPointerUp(pointerId = down.id)
                }

                BackgroundGestureOutcome.SwipeUp -> {
                    onSwipeUp()
                    consumeUntilPointerUp(pointerId = down.id)
                }

                BackgroundGestureOutcome.Released,
                BackgroundGestureOutcome.Moved,
                BackgroundGestureOutcome.Cancelled -> Unit
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitBackgroundGestureOutcome(
    pointerId: PointerId,
    startPosition: Offset,
    touchSlopPx: Float,
    swipeUpThresholdPx: Float,
    longPressTimeoutMillis: Long,
    canSwipeUp: Boolean
): BackgroundGestureOutcome? {
    var exceededTouchSlop = false
    val slopOutcome = withTimeoutOrNull(longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: return@withTimeoutOrNull BackgroundGestureOutcome.Cancelled

            if (!change.pressed) {
                return@withTimeoutOrNull BackgroundGestureOutcome.Released
            }

            val totalDrag = change.position - startPosition
            if (canSwipeUp && totalDrag.isSwipeUpGesture(minimumDistancePx = swipeUpThresholdPx)) {
                return@withTimeoutOrNull BackgroundGestureOutcome.SwipeUp
            }

            if (totalDrag.exceedsTouchSlop(touchSlopPx = touchSlopPx)) {
                exceededTouchSlop = true
                break
            }
        }
        null
    }

    if (slopOutcome != null) {
        return slopOutcome
    }

    if (!exceededTouchSlop) {
        return null
    }

    var event = currentEvent
    var change = event.changes.firstOrNull { it.id == pointerId }
        ?: return BackgroundGestureOutcome.Cancelled
    var totalDrag = change.position - startPosition

    val isMovingUp = canSwipeUp && totalDrag.isSwipeUpGesture(minimumDistancePx = 0f)
    if (!isMovingUp) {
        return BackgroundGestureOutcome.Moved
    }

    while (true) {
        event = awaitPointerEvent()
        change = event.changes.firstOrNull { it.id == pointerId }
            ?: return BackgroundGestureOutcome.Cancelled

        if (!change.pressed) {
            return BackgroundGestureOutcome.Released
        }

        totalDrag = change.position - startPosition

        if (!totalDrag.isSwipeUpGesture(minimumDistancePx = 0f)) {
            return BackgroundGestureOutcome.Moved
        }

        if (totalDrag.isSwipeUpGesture(minimumDistancePx = swipeUpThresholdPx)) {
            return BackgroundGestureOutcome.SwipeUp
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitPointerUp(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilPointerUp(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        change.consume()
        if (!change.pressed) return
    }
}
