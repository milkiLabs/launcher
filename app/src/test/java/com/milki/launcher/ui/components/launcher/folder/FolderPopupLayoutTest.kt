package com.milki.launcher.ui.components.launcher.folder

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPopupLayoutTest {

    @Test
    fun `single item uses one by one layout`() {
        val layout = folderGridLayoutForItemCount(1)

        assertEquals(1, layout.columns)
        assertEquals(1, layout.rows)
        assertEquals(1, layout.pageSize)
        assertEquals(1, layout.pageCount)
    }

    @Test
    fun `two to six items stay in two column layout`() {
        val twoItemLayout = folderGridLayoutForItemCount(2)
        val sixItemLayout = folderGridLayoutForItemCount(6)

        assertEquals(2, twoItemLayout.columns)
        assertEquals(1, twoItemLayout.rows)
        assertEquals(2, sixItemLayout.columns)
        assertEquals(3, sixItemLayout.rows)
        assertEquals(1, sixItemLayout.pageCount)
    }

    @Test
    fun `seven items promote folder to three by three layout`() {
        val layout = folderGridLayoutForItemCount(7)

        assertEquals(3, layout.columns)
        assertEquals(3, layout.rows)
        assertEquals(9, layout.pageSize)
        assertEquals(1, layout.pageCount)
    }

    @Test
    fun `more than nine items create another page`() {
        val layout = folderGridLayoutForItemCount(10)

        assertEquals(3, layout.columns)
        assertEquals(3, layout.rows)
        assertEquals(9, layout.pageSize)
        assertEquals(2, layout.pageCount)
    }

    @Test
    fun `insertion index appends within partially filled last page`() {
        val insertionIndex = resolveFolderInsertionIndex(
            totalItemsWithoutDragged = 9,
            targetPage = 1,
            slotIndex = 4,
            pageSize = 9
        )

        assertEquals(9, insertionIndex)
    }

    @Test
    fun `drop on occupied slot swaps dragged item with target item`() {
        val reordered = reorderFolderItemsForDrop(
            items = listOf("A", "B", "C", "D"),
            fromIndex = 0,
            targetIndex = 2
        )

        assertEquals(listOf("C", "B", "A", "D"), reordered)
    }

    @Test
    fun `drop on trailing empty slot appends dragged item`() {
        val reordered = reorderFolderItemsForDrop(
            items = listOf("A", "B", "C", "D"),
            fromIndex = 0,
            targetIndex = 4
        )

        assertEquals(listOf("B", "C", "D", "A"), reordered)
    }

    @Test
    fun `move list item inserts using post removal index semantics`() {
        val reordered = moveListItem(
            items = listOf("A", "B", "C", "D"),
            fromIndex = 0,
            insertionIndex = 2
        )

        assertEquals(listOf("B", "C", "A", "D"), reordered)
    }
}
