package com.milki.launcher.domain.widget

import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLayoutPolicyTest {

    @Test
    fun keepsPreferredSpanWhenItFitsAtAnchor() {
        val result = fitInlineWidgetSpanAtAnchor(
            anchor = GridPosition(row = 0, column = 0),
            preferredSpan = GridSpan(columns = 2, rows = 2),
            occupancy = GridOccupancy.empty(),
            gridColumns = 5,
            visibleRows = 5
        )

        assertEquals(GridSpan(columns = 2, rows = 2), result)
    }

    @Test
    fun shrinksWidthNearRightEdge() {
        val result = fitInlineWidgetSpanAtAnchor(
            anchor = GridPosition(row = 0, column = 4),
            preferredSpan = GridSpan(columns = 2, rows = 2),
            occupancy = GridOccupancy.empty(),
            gridColumns = 5,
            visibleRows = 5
        )

        assertEquals(GridSpan(columns = 1, rows = 2), result)
    }

    @Test
    fun shrinksHeightNearVisibleBottom() {
        val result = fitInlineWidgetSpanAtAnchor(
            anchor = GridPosition(row = 4, column = 0),
            preferredSpan = GridSpan(columns = 2, rows = 2),
            occupancy = GridOccupancy.empty(),
            gridColumns = 5,
            visibleRows = 5
        )

        assertEquals(GridSpan(columns = 2, rows = 1), result)
    }

    @Test
    fun returnsNullWhenAnchorCellIsOccupied() {
        val anchor = GridPosition(row = 0, column = 0)
        val occupancy = GridOccupancy.fromItems(
            listOf(
                HomeItem.PinnedApp(
                    id = "app:occupant",
                    packageName = "com.example.occupant",
                    activityName = "MainActivity",
                    label = "Occupant",
                    position = anchor
                )
            )
        )

        val result = fitInlineWidgetSpanAtAnchor(
            anchor = anchor,
            preferredSpan = GridSpan(columns = 2, rows = 2),
            occupancy = occupancy,
            gridColumns = 5,
            visibleRows = 5
        )

        assertNull(result)
    }
}