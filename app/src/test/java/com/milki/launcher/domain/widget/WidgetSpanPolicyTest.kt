package com.milki.launcher.domain.widget

import com.milki.launcher.domain.model.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSpanPolicyTest {

    @Test
    fun keepsCompactWidgetsUnchanged() {
        val result = recommendWidgetPlacementSpan(
            rawSpan = GridSpan(columns = 2, rows = 2),
            gridColumns = 5
        )

        assertEquals(GridSpan(columns = 2, rows = 2), result)
    }

    @Test
    fun keepsFullWidthBannersWhenTheyAreAlreadyReasonable() {
        val result = recommendWidgetPlacementSpan(
            rawSpan = GridSpan(columns = 5, rows = 1),
            gridColumns = 5
        )

        assertEquals(GridSpan(columns = 5, rows = 1), result)
    }

    @Test
    fun shrinksVeryLargeWidgetsToAReasonableDefault() {
        val result = recommendWidgetPlacementSpan(
            rawSpan = GridSpan(columns = 5, rows = 4),
            gridColumns = 5
        )

        assertEquals(GridSpan(columns = 4, rows = 3), result)
    }

    @Test
    fun trimsWideTallWidgetsBeforePlacementBecomesAwkward() {
        val result = recommendWidgetPlacementSpan(
            rawSpan = GridSpan(columns = 5, rows = 3),
            gridColumns = 5
        )

        assertEquals(GridSpan(columns = 5, rows = 2), result)
    }
}
