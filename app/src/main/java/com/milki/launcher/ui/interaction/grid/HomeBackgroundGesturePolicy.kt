package com.milki.launcher.ui.interaction.grid

import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.LauncherGestureDirection
import com.milki.launcher.domain.model.LauncherGestureKind
import com.milki.launcher.domain.model.LauncherTrigger
import kotlin.math.abs

/**
 * Policy for empty-space gestures on the home grid.
 *
 * Item gestures must always win over background gestures, and transient UI such
 * as menus/resize overlays should block new background interactions entirely.
 *
 * The policy exposes enabled launcher triggers as data instead of hardcoded
 * directional booleans, which keeps the gesture pipeline scalable as new
 * homescreen gestures are added.
 */
internal data class HomeBackgroundGesturePolicy(
    val canStartBackgroundGesture: Boolean,
    val enabledTriggers: Set<LauncherTrigger> = emptySet()
) {
    val directionalTriggers: List<LauncherTrigger> =
        enabledTriggers.filter { trigger ->
            trigger.metadata.kind == LauncherGestureKind.SWIPE
        }
}

/**
 * Runtime bindings for empty-area homescreen gestures.
 *
 * Taps remain a dedicated callback because they are semantically distinct from
 * directional gestures. All gesture triggers are routed through [onTrigger],
 * which keeps the API stable as new triggers are introduced.
 */
data class HomeBackgroundGestureBindings(
    val onEmptyAreaTap: (() -> Unit)? = null,
    val onEmptyAreaDoubleTap: (() -> Unit)? = null,
    val onEmptyAreaLongPress: (Offset) -> Unit = {},
    val onTrigger: ((LauncherTrigger) -> Unit)? = null,
    val configuredTriggers: Set<LauncherTrigger> = emptySet()
) {
    fun supports(trigger: LauncherTrigger): Boolean {
        if (trigger !in configuredTriggers) {
            return false
        }

        return when (trigger) {
            LauncherTrigger.HOME_TAP -> onEmptyAreaTap != null
            LauncherTrigger.HOME_DOUBLE_TAP -> onEmptyAreaDoubleTap != null
            else -> onTrigger != null
        }
    }

    fun invoke(trigger: LauncherTrigger) {
        when (trigger) {
            LauncherTrigger.HOME_TAP -> onEmptyAreaTap?.invoke()
            LauncherTrigger.HOME_DOUBLE_TAP -> onEmptyAreaDoubleTap?.invoke()
            else -> onTrigger?.invoke(trigger)
        }
    }

    fun enabledTriggers(): Set<LauncherTrigger> {
        return configuredTriggers
            .filterTo(linkedSetOf()) { trigger -> supports(trigger) }
    }
}

internal fun Offset.exceedsTouchSlop(touchSlopPx: Float): Boolean {
    return abs(x) > touchSlopPx || abs(y) > touchSlopPx
}

internal fun Offset.matchesTriggerDirection(
    trigger: LauncherTrigger,
    minimumDistancePx: Float,
    verticalDominanceRatio: Float = 1.25f
): Boolean {
    if (trigger.metadata.kind != LauncherGestureKind.SWIPE) return false

    return when (trigger.metadata.direction) {
        LauncherGestureDirection.UP -> {
            y <= -minimumDistancePx && -y > abs(x) * verticalDominanceRatio
        }

        LauncherGestureDirection.DOWN -> {
            y >= minimumDistancePx && y > abs(x) * verticalDominanceRatio
        }

        LauncherGestureDirection.LEFT -> {
            x <= -minimumDistancePx && -x > abs(y) * verticalDominanceRatio
        }

        LauncherGestureDirection.RIGHT -> {
            x >= minimumDistancePx && x > abs(y) * verticalDominanceRatio
        }

        null -> false
    }
}
