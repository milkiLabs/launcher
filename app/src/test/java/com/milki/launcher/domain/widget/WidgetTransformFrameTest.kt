package com.milki.launcher.domain.widget

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTransformFrameTest {

    @Test
    fun body_handle_moves_frame_without_changing_size() {
        val result = applyWidgetTransformHandle(
            startFrame = WidgetFrame(
                position = GridPosition(row = 1, column = 1),
                span = GridSpan(columns = 2, rows = 3)
            ),
            handle = WidgetTransformHandle.Body,
            columnDelta = 2,
            rowDelta = 1,
            maxColumns = 5,
            maxRows = 6
        )

        assertEquals(GridPosition(row = 2, column = 3), result.position)
        assertEquals(GridSpan(columns = 2, rows = 3), result.span)
    }

    @Test
    fun top_left_handle_changes_anchor_and_span() {
        val result = applyWidgetTransformHandle(
            startFrame = WidgetFrame(
                position = GridPosition(row = 2, column = 2),
                span = GridSpan(columns = 2, rows = 2)
            ),
            handle = WidgetTransformHandle.TopLeft,
            columnDelta = -1,
            rowDelta = -1,
            maxColumns = 5,
            maxRows = 6
        )

        assertEquals(GridPosition(row = 1, column = 1), result.position)
        assertEquals(GridSpan(columns = 3, rows = 3), result.span)
    }

    @Test
    fun left_handle_can_shrink_widget_to_single_cell_width() {
        val result = applyWidgetTransformHandle(
            startFrame = WidgetFrame(
                position = GridPosition(row = 0, column = 1),
                span = GridSpan(columns = 3, rows = 2)
            ),
            handle = WidgetTransformHandle.Left,
            columnDelta = 2,
            rowDelta = 0,
            maxColumns = 5,
            maxRows = 6
        )

        assertEquals(GridPosition(row = 0, column = 3), result.position)
        assertEquals(GridSpan(columns = 1, rows = 2), result.span)
    }

    @Test
    fun bottom_right_handle_stays_inside_grid() {
        val result = applyWidgetTransformHandle(
            startFrame = WidgetFrame(
                position = GridPosition(row = 3, column = 3),
                span = GridSpan(columns = 2, rows = 2)
            ),
            handle = WidgetTransformHandle.BottomRight,
            columnDelta = 3,
            rowDelta = 3,
            maxColumns = 5,
            maxRows = 5
        )

        assertEquals(GridPosition(row = 3, column = 3), result.position)
        assertEquals(GridSpan(columns = 2, rows = 2), result.span)
    }
}
