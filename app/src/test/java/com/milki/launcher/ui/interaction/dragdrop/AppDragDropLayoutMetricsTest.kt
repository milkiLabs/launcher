package com.milki.launcher.ui.interaction.dragdrop

import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.GridPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDragDropLayoutMetricsTest {

    private val metrics = AppDragDropLayoutMetrics(
        cellWidthPx = 100f,
        cellHeightPx = 50f,
        columns = 4,
        rows = 6
    )

    @Test
    fun pixel_to_cell_returns_raw_in_range_cell() {
        assertEquals(
            GridPosition(row = 3, column = 2),
            metrics.pixelToCell(Offset(x = 250f, y = 175f))
        )
    }

    @Test
    fun pixel_to_cell_preserves_out_of_range_coordinates_instead_of_coercing() {
        assertEquals(
            GridPosition(row = -10, column = -5),
            metrics.pixelToCell(Offset(x = -500f, y = -500f))
        )
        assertEquals(
            GridPosition(row = 9, column = 7),
            metrics.pixelToCell(Offset(x = 750f, y = 450f))
        )
    }

    @Test
    fun clamp_matches_grid_bounds_single_cell_semantics() {
        assertEquals(
            GridPosition(row = 0, column = 0),
            metrics.clamp(GridPosition(row = -3, column = -1))
        )
        assertEquals(
            GridPosition(row = 5, column = 3),
            metrics.clamp(GridPosition(row = 9, column = 9))
        )
        assertEquals(
            GridPosition(row = 2, column = 1),
            metrics.clamp(GridPosition(row = 2, column = 1))
        )
    }

    @Test
    fun calculate_target_stays_within_grid_bounds() {
        val start = GridPosition(row = 5, column = 3)
        assertEquals(
            GridPosition(row = 5, column = 0),
            metrics.calculateTarget(start, Offset(x = -9000f, y = 9000f))
        )
        assertEquals(
            GridPosition(row = 4, column = 2),
            metrics.calculateTarget(start, Offset(x = -150f, y = -60f))
        )
    }
}
