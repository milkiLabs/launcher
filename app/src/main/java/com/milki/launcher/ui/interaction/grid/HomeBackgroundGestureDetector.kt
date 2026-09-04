package com.milki.launcher.ui.interaction.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.ui.interaction.PreTimeoutResult
import com.milki.launcher.ui.interaction.awaitPointerUp
import com.milki.launcher.ui.interaction.consumeUntilPointerUp
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.trackPointerUntilLongPressOrRelease
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class BackgroundGestureOutcome {
    Released,
    Triggered,
    Moved,
    Cancelled
}

/**
 * Multiplier applied to [ViewConfiguration.touchSlop] to derive the max
 * displacement between the two taps of a double tap. A deliberately
 * generous value: the second touch of a double tap typically lands with
 * more jitter than a single stationary finger, and Android's own gesture
 * pipeline has historically used a widened slop for the same reason.
 */
private const val DOUBLE_TAP_SLOP_TOUCH_SLOP_MULTIPLIER = 2f

internal fun Modifier.detectHomeBackgroundGestures(
    key: Any? = null,
    occupancyProvider: () -> GridOccupancy,
    layoutMetrics: AppDragDropLayoutMetrics,
    policy: HomeBackgroundGesturePolicy,
    doubleTapArbiter: DoubleTapArbiter,
    gestureThresholdPx: Float,
    bindingsProvider: () -> HomeBackgroundGestureBindings
): Modifier {
    // The pointerInput key covers only the gesture *configuration* (interaction
    // mode, policy, metrics). Grid contents (items/occupancy) and action
    // bindings are read through providers at gesture time: pinning, moving, or
    // unpinning an item must not restart the detector mid-gesture and swallow
    // the in-flight swipe. Occupancy is snapshotted once per gesture at
    // finger-down so a mid-gesture mutation cannot retarget the pressed cell.
    return pointerInput(key, layoutMetrics, policy, gestureThresholdPx) {
        coroutineScope {
            var pendingTapJob: Job? = null

            fun cancelPendingTapResolution() {
                pendingTapJob?.cancel()
                pendingTapJob = null
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val bindings = bindingsProvider()
                // Raw (unclamped) cell on purpose: out-of-range cells have no
                // occupant, so presses outside the grid count as empty area.
                val pressedCell = layoutMetrics.pixelToCell(down.position)
                val occupant = occupancyProvider().occupantAt(pressedCell)
                val startCellOccupied = occupant != null

                // Popup widget icons handle their own swipe-up gesture to launch
                // the provider app. Suppress background directional gestures when
                // the touch starts on one so both don't fire simultaneously.
                val isPopupWidgetCell = (occupant as? HomeItem.WidgetItem)
                    ?.displayMode == com.milki.launcher.domain.model.WidgetDisplayMode.PopupIcon

                if (!policy.canStartBackgroundGesture) {
                    return@awaitEachGesture
                }

                val supportsDoubleTap = LauncherTrigger.HOME_DOUBLE_TAP in policy.enabledTriggers
                val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
                val doubleTapSlopPx =
                    viewConfiguration.touchSlop * DOUBLE_TAP_SLOP_TOUCH_SLOP_MULTIPLIER

                /**
                 * Arbitrate this down against any tap pending from a previous
                 * gesture. The pending tap is consumed either way, and the
                 * arbiter's state lives outside `pointerInput`, so detector
                 * restarts (grid mutations) no longer discard unresolved taps.
                 */
                val arbitration = doubleTapArbiter.arbitrateDown(
                    downTimeMillis = down.uptimeMillis,
                    downPosition = down.position,
                    landsOnEmptyCell = !startCellOccupied,
                    supportsDoubleTap = supportsDoubleTap,
                    doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                    doubleTapSlopPx = doubleTapSlopPx
                )
                cancelPendingTapResolution()

                // A pending tap that failed arbitration must not wait for the
                // (now cancelled) timeout job — emit its deferred single tap now.
                if (arbitration == DoubleTapDownDecision.PENDING_FLUSHED_AS_SINGLE_TAP) {
                    bindings.invoke(LauncherTrigger.HOME_TAP)
                }

                val outcome = awaitBackgroundGestureOutcome(
                    pointerId = down.id,
                    startPosition = down.position,
                    touchSlopPx = viewConfiguration.touchSlop,
                    gestureThresholdPx = gestureThresholdPx,
                    longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                    policy = policy,
                    bindings = bindings,
                    suppressDirectionalGestures = isPopupWidgetCell
                )

                when (outcome) {
                    null -> {
                        if (!startCellOccupied) {
                            bindings.onEmptyAreaLongPress(down.position)
                        }
                        awaitPointerUp(pointerId = down.id)
                    }

                    BackgroundGestureOutcome.Triggered -> {
                        consumeUntilPointerUp(pointerId = down.id)
                    }

                    BackgroundGestureOutcome.Released -> {
                        if (!startCellOccupied) {
                            if (supportsDoubleTap) {
                                when (arbitration) {
                                    DoubleTapDownDecision.SECOND_TAP ->
                                        bindings.invoke(LauncherTrigger.HOME_DOUBLE_TAP)

                                    else -> {
                                        // First half of a potential double tap:
                                        // defer the single tap until either a
                                        // qualifying second tap arrives or the
                                        // double-tap window expires.
                                        doubleTapArbiter.recordTap(
                                            uptimeMillis = down.uptimeMillis,
                                            position = down.position
                                        )
                                        pendingTapJob = launch {
                                            delay(doubleTapTimeoutMillis)
                                            if (doubleTapArbiter.resolvePendingTap()) {
                                                bindings.invoke(LauncherTrigger.HOME_TAP)
                                            }
                                        }
                                    }
                                }
                            } else {
                                bindings.invoke(LauncherTrigger.HOME_TAP)
                            }
                        }
                    }

                    BackgroundGestureOutcome.Moved,
                    BackgroundGestureOutcome.Cancelled -> Unit
                }

                /**
                 * SECOND-TAP-BECAME-SWIPE REPAIR PATH:
                 * Arbitration consumed the previous tap expecting a double tap,
                 * but this gesture ended as anything other than a clean release
                 * (e.g. it turned into a swipe, long-press, or was cancelled).
                 * No HOME_DOUBLE_TAP will fire for the pair, so repay the user
                 * with the deferred HOME_TAP from the first press.
                 */
                if (arbitration == DoubleTapDownDecision.SECOND_TAP &&
                    outcome != BackgroundGestureOutcome.Released
                ) {
                    bindings.invoke(LauncherTrigger.HOME_TAP)
                }
            }
        }
    }
}

/**
 * Resolution produced by the shared tracker for phase 1 (pre-slop) of a
 * background gesture.
 */
private sealed interface BackgroundPhase1Resolution {
    /** A terminal gesture outcome was reached before touch slop. */
    data class Terminal(val outcome: BackgroundGestureOutcome) : BackgroundPhase1Resolution

    /** Movement passed touch slop; hand off to post-slop tracking. */
    data object SlopExceeded : BackgroundPhase1Resolution
}

private suspend fun AwaitPointerEventScope.awaitBackgroundGestureOutcome(
    pointerId: PointerId,
    startPosition: Offset,
    touchSlopPx: Float,
    gestureThresholdPx: Float,
    longPressTimeoutMillis: Long,
    policy: HomeBackgroundGesturePolicy,
    bindings: HomeBackgroundGestureBindings,
    suppressDirectionalGestures: Boolean = false
): BackgroundGestureOutcome? {
    val result = trackPointerUntilLongPressOrRelease(
        pointerId = pointerId,
        longPressTimeoutMillis = longPressTimeoutMillis,
        onMove = { change ->
            val totalDrag = change.position - startPosition
            val matchedTrigger = policy.matchingTrigger(
                dragOffset = totalDrag,
                minimumDistancePx = gestureThresholdPx
            )
            when {
                matchedTrigger != null && suppressDirectionalGestures ->
                    BackgroundPhase1Resolution.Terminal(BackgroundGestureOutcome.Moved)

                matchedTrigger != null -> {
                    bindings.invoke(matchedTrigger)
                    BackgroundPhase1Resolution.Terminal(BackgroundGestureOutcome.Triggered)
                }

                totalDrag.exceedsTouchSlop(touchSlopPx = touchSlopPx) ->
                    BackgroundPhase1Resolution.SlopExceeded

                else -> null
            }
        },
        onLift = {
            BackgroundPhase1Resolution.Terminal(BackgroundGestureOutcome.Released)
        }
    )

    var exceededTouchSlop = false
    val phase1Outcome: BackgroundGestureOutcome? = when (result) {
        is PreTimeoutResult.Resolved -> when (val value = result.value) {
            is BackgroundPhase1Resolution.Terminal -> value.outcome
            BackgroundPhase1Resolution.SlopExceeded -> {
                exceededTouchSlop = true
                null
            }
        }

        // Finger released before timeout or slop.
        is PreTimeoutResult.Released -> when (val value = result.value) {
            is BackgroundPhase1Resolution.Terminal -> value.outcome
            // Unreachable at lift time (onLift only produces Terminal).
            BackgroundPhase1Resolution.SlopExceeded -> null
        }

        // Pointer disappeared (multi-touch, system cancel).
        PreTimeoutResult.Lost -> BackgroundGestureOutcome.Cancelled

        // Timeout fired → long press.
        is PreTimeoutResult.LongPress -> null
    }

    if (!exceededTouchSlop) {
        return phase1Outcome
    }

    var change = (result as PreTimeoutResult.Resolved).change
    var totalDrag = change.position - startPosition

    if (!policy.hasDirectionalMotion(totalDrag)) {
        return BackgroundGestureOutcome.Moved
    }

    while (true) {
        val event = awaitPointerEvent()
        change = event.changes.firstOrNull { it.id == pointerId }
            ?: return BackgroundGestureOutcome.Cancelled

        if (!change.pressed) {
            return BackgroundGestureOutcome.Moved
        }

        totalDrag = change.position - startPosition

        if (!policy.hasDirectionalMotion(totalDrag)) {
            return BackgroundGestureOutcome.Moved
        }

        val matchedTrigger = policy.matchingTrigger(
            dragOffset = totalDrag,
            minimumDistancePx = gestureThresholdPx
        )
        if (matchedTrigger != null) {
            if (suppressDirectionalGestures) {
                return BackgroundGestureOutcome.Moved
            }
            bindings.invoke(matchedTrigger)
            return BackgroundGestureOutcome.Triggered
        }
    }
}

private fun HomeBackgroundGesturePolicy.matchingTrigger(
    dragOffset: Offset,
    minimumDistancePx: Float
): LauncherTrigger? {
    return directionalTriggers.firstOrNull { trigger ->
        dragOffset.matchesTriggerDirection(
            trigger = trigger,
            minimumDistancePx = minimumDistancePx
        )
    }
}

private fun HomeBackgroundGesturePolicy.hasDirectionalMotion(
    dragOffset: Offset
): Boolean {
    return directionalTriggers.any { trigger ->
        dragOffset.matchesTriggerDirection(
            trigger = trigger,
            minimumDistancePx = 0f
        )
    }
}
