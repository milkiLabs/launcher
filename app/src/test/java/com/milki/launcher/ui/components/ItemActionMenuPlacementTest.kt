package com.milki.launcher.ui.components

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemActionMenuPlacementTest {

    @Test
    fun `menu prefers showing above the anchor when space allows`() {
        val placement = calculateItemActionMenuPlacement(
            anchorBounds = IntRect(left = 120, top = 420, right = 220, bottom = 520),
            windowSize = IntSize(width = 1080, height = 1920),
            popupContentSize = IntSize(width = 280, height = 220),
            windowMarginPx = 24,
            anchorGapPx = 16,
            arrowSizePx = 20,
            arrowEdgePaddingPx = 36
        )

        assertEquals(ItemActionMenuVerticalPlacement.Above, placement.verticalPlacement)
        assertTrue(placement.popupOffset.y < 420)
    }

    @Test
    fun `menu flips below when there is not enough space above`() {
        val placement = calculateItemActionMenuPlacement(
            anchorBounds = IntRect(left = 200, top = 48, right = 320, bottom = 148),
            windowSize = IntSize(width = 1080, height = 1920),
            popupContentSize = IntSize(width = 280, height = 220),
            windowMarginPx = 24,
            anchorGapPx = 16,
            arrowSizePx = 20,
            arrowEdgePaddingPx = 36
        )

        assertEquals(ItemActionMenuVerticalPlacement.Below, placement.verticalPlacement)
        assertTrue(placement.popupOffset.y > 148)
    }

    @Test
    fun `arrow offset is clamped when anchor is near the screen edge`() {
        val placement = calculateItemActionMenuPlacement(
            anchorBounds = IntRect(left = 8, top = 420, right = 88, bottom = 500),
            windowSize = IntSize(width = 1080, height = 1920),
            popupContentSize = IntSize(width = 280, height = 220),
            windowMarginPx = 24,
            anchorGapPx = 16,
            arrowSizePx = 20,
            arrowEdgePaddingPx = 36
        )

        assertEquals(36, placement.arrowOffsetPx)
    }
}
