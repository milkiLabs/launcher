package com.milki.launcher.ui.components.launcher.folder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderReorderEngineTest {

    private val density = Density(1f)

    private fun app(id: String) = HomeItem.PinnedApp(
        id = id,
        packageName = "pkg",
        activityName = id,
        label = id
    )

    private fun threeItemChildren() = listOf(app("a"), app("b"), app("c"))

    private fun twoColumnMetrics() = FolderSurfaceMetrics.create(
        density = density,
        layout = folderGridLayoutForItemCount(3),
        pageCount = 1,
        maxWidth = 2000.dp,
        maxHeight = 2000.dp
    )

    private fun engineWithGrid(
        children: List<HomeItem>,
        draggedId: String
    ): FolderReorderEngine {
        val engine = FolderReorderEngine()
        engine.state.startInternalDrag(
            item = children.first { it.id == draggedId },
            page = 0,
            localChildren = children,
            // Probe offset is (cellWidth/2, cellHeight/2) = (40, 48);
            // this top-left puts the starting probe at slot 0's center (174, 174).
            itemWindowTopLeft = Offset(134f, 126f),
            metrics = twoColumnMetrics(),
            density = density
        )
        // 2-column grid, spacing 4px: cell centers at (174,174), (326,174), (174,326), (326,326)
        engine.state.gridWindowRect = Rect(100f, 100f, 400f, 400f)
        engine.state.popupWindowRect = Rect(0f, 0f, 500f, 500f)
        return engine
    }

    @Test
    fun `start drag hovers the source slot`() {
        val engine = FolderReorderEngine()

        engine.state.startInternalDrag(
            item = app("b"),
            page = 0,
            localChildren = threeItemChildren(),
            itemWindowTopLeft = Offset.Zero,
            metrics = twoColumnMetrics(),
            density = density
        )

        assertEquals(FolderDropSlot(page = 0, slotIndex = 1), engine.state.hoveredSlot)
        assertEquals("b", engine.state.draggedItemId)
    }

    @Test
    fun `frame inside popup moves hover to nearest slot`() {
        val engine = engineWithGrid(threeItemChildren(), "a")

        val escaped = engine.onDragFrame(
            delta = Offset(152f, 0f),
            currentPage = 0,
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertNull(escaped)
        assertEquals(FolderDropSlot(page = 0, slotIndex = 1), engine.state.hoveredSlot)
        assertNull(engine.state.pendingAutoPage)
        assertFalse(engine.state.isDraggingOut)
    }

    @Test
    fun `frame crossing popup edge reports drag out`() {
        val engine = engineWithGrid(threeItemChildren(), "a")

        val escaped = engine.onDragFrame(
            delta = Offset(400f, 0f),
            currentPage = 0,
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertEquals("a", escaped?.id)
        assertTrue(engine.state.isDraggingOut)
        assertTrue(engine.state.isPlatformDragActive)
    }

    @Test
    fun `frames during active platform drag are ignored`() {
        val engine = engineWithGrid(threeItemChildren(), "a")
        engine.onDragFrame(
            delta = Offset(400f, 0f),
            currentPage = 0,
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        val escaped = engine.onDragFrame(
            delta = Offset(10f, 10f),
            currentPage = 0,
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertNull(escaped)
    }

    @Test
    fun `drop on occupied slot swaps dragged item with target`() {
        val engine = engineWithGrid(threeItemChildren(), "a")
        // Move the probe down one row onto slot 2's center (174, 326).
        engine.onDragFrame(
            delta = Offset(0f, 152f),
            currentPage = 0,
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        val reordered = engine.resolveDrop(
            currentPage = 0,
            children = threeItemChildren(),
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertEquals(listOf("c", "b", "a"), reordered?.map { it.id })
    }

    @Test
    fun `drop without hover change is null`() {
        val engine = engineWithGrid(threeItemChildren(), "a")

        val reordered = engine.resolveDrop(
            currentPage = 0,
            children = threeItemChildren(),
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertNull(reordered)
    }

    @Test
    fun `drop on empty trailing slot appends dragged item`() {
        val children = (0 until 7).map { app("i$it") }
        val engine = engineWithGrid(children, "i0")
        engine.state.hoveredSlot = FolderDropSlot(page = 0, slotIndex = 8)

        val reordered = engine.resolveDrop(
            currentPage = 0,
            children = children,
            layout = folderGridLayoutForItemCount(7),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertEquals(listOf("i1", "i2", "i3", "i4", "i5", "i6", "i0"), reordered?.map { it.id })
    }

    @Test
    fun `drop during platform drag is null`() {
        val engine = engineWithGrid(threeItemChildren(), "a")
        engine.state.isPlatformDragActive = true

        val reordered = engine.resolveDrop(
            currentPage = 0,
            children = threeItemChildren(),
            layout = folderGridLayoutForItemCount(3),
            metrics = twoColumnMetrics(),
            density = density
        )

        assertNull(reordered)
    }

    @Test
    fun `reset clears transient drag state`() {
        val engine = engineWithGrid(threeItemChildren(), "a")

        engine.reset()

        assertNull(engine.state.draggedItemId)
        assertNull(engine.state.dragOutItem)
        assertNull(engine.state.hoveredSlot)
        assertFalse(engine.state.isDraggingOut)
        assertFalse(engine.state.isPlatformDragActive)
    }
}
