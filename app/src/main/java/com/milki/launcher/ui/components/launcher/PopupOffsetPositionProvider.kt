package com.milki.launcher.ui.components.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Shared [PopupPositionProvider] that delegates to [calculateItemActionMenuPlacement]
 * and exposes the computed [ItemActionMenuPlacement] via [placement].
 *
 * Used by both [ItemActionMenu] and [PopupWidgetView] to avoid duplicating
 * the position provider boilerplate.
 */
internal class PopupOffsetPositionProvider(
    private val windowMarginPx: Int,
    private val anchorGapPx: Int,
    private val arrowSizePx: Int,
    private val arrowEdgePaddingPx: Int
) : PopupPositionProvider {
    var placement by mutableStateOf(ItemActionMenuPlacement())
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val resolved = calculateItemActionMenuPlacement(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            popupContentSize = popupContentSize,
            windowMarginPx = windowMarginPx,
            anchorGapPx = anchorGapPx,
            arrowSizePx = arrowSizePx,
            arrowEdgePaddingPx = arrowEdgePaddingPx
        )
        placement = resolved
        return resolved.popupOffset
    }
}
