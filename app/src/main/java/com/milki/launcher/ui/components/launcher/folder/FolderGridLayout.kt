package com.milki.launcher.ui.components.launcher.folder

import kotlin.math.ceil

private const val SINGLE_ITEM_COUNT = 1
private const val TWO_COLUMN_LAYOUT_ITEM_LIMIT = 6
private const val TWO_COLUMN_LAYOUT_COLUMNS = 2
private const val TWO_COLUMN_LAYOUT_DIVISOR = 2f
private const val TWO_COLUMN_LAYOUT_MAX_ROWS = 3
private const val FOLDER_PAGE_SIZE = 9
private const val THREE_COLUMN_LAYOUT_SIZE = 3

internal data class FolderGridLayout(
    val columns: Int,
    val rows: Int,
    val pageSize: Int,
    val pageCount: Int
)

internal fun folderGridLayoutForItemCount(itemCount: Int): FolderGridLayout {
    val safeCount = itemCount.coerceAtLeast(SINGLE_ITEM_COUNT)

    return when {
        safeCount == SINGLE_ITEM_COUNT -> FolderGridLayout(
            columns = SINGLE_ITEM_COUNT,
            rows = SINGLE_ITEM_COUNT,
            pageSize = SINGLE_ITEM_COUNT,
            pageCount = SINGLE_ITEM_COUNT
        )
        safeCount <= TWO_COLUMN_LAYOUT_ITEM_LIMIT -> {
            val rows = ceil(safeCount / TWO_COLUMN_LAYOUT_DIVISOR)
                .toInt()
                .coerceIn(SINGLE_ITEM_COUNT, TWO_COLUMN_LAYOUT_MAX_ROWS)
            FolderGridLayout(
                columns = TWO_COLUMN_LAYOUT_COLUMNS,
                rows = rows,
                pageSize = rows * TWO_COLUMN_LAYOUT_COLUMNS,
                pageCount = SINGLE_ITEM_COUNT
            )
        }
        else -> {
            FolderGridLayout(
                columns = THREE_COLUMN_LAYOUT_SIZE,
                rows = THREE_COLUMN_LAYOUT_SIZE,
                pageSize = FOLDER_PAGE_SIZE,
                pageCount = ceil(safeCount / FOLDER_PAGE_SIZE.toFloat()).toInt()
            )
        }
    }
}

internal fun resolveFolderInsertionIndex(
    totalItemsWithoutDragged: Int,
    targetPage: Int,
    slotIndex: Int,
    pageSize: Int
): Int {
    val safePageSize = pageSize.coerceAtLeast(SINGLE_ITEM_COUNT)
    val pageStart = (targetPage.coerceAtLeast(0) * safePageSize)
    val itemsOnPage = (totalItemsWithoutDragged - pageStart)
        .coerceAtLeast(0)
        .coerceAtMost(safePageSize)
    val boundedSlotIndex = slotIndex.coerceIn(0, itemsOnPage)

    return (pageStart + boundedSlotIndex).coerceIn(0, totalItemsWithoutDragged)
}

internal fun resolveFolderDropIndex(
    targetPage: Int,
    slotIndex: Int,
    pageSize: Int
): Int {
    val safePageSize = pageSize.coerceAtLeast(SINGLE_ITEM_COUNT)
    return (targetPage.coerceAtLeast(0) * safePageSize) + slotIndex.coerceAtLeast(0)
}

internal fun <T> reorderFolderItemsForDrop(
    items: List<T>,
    fromIndex: Int,
    targetIndex: Int
): List<T> {
    if (fromIndex !in items.indices) return items

    return if (targetIndex in items.indices) {
        swapListItems(
            items = items,
            firstIndex = fromIndex,
            secondIndex = targetIndex
        )
    } else {
        moveListItem(
            items = items,
            fromIndex = fromIndex,
            insertionIndex = targetIndex.coerceAtMost(items.size)
        )
    }
}

internal fun <T> swapListItems(
    items: List<T>,
    firstIndex: Int,
    secondIndex: Int
): List<T> {
    if (firstIndex !in items.indices || secondIndex !in items.indices || firstIndex == secondIndex) {
        return items
    }

    return items.toMutableList().apply {
        val firstItem = this[firstIndex]
        this[firstIndex] = this[secondIndex]
        this[secondIndex] = firstItem
    }
}

internal fun <T> moveListItem(
    items: List<T>,
    fromIndex: Int,
    insertionIndex: Int
): List<T> {
    if (fromIndex !in items.indices) return items

    val movedItem = items[fromIndex]
    val remainingItems = items.toMutableList().apply {
        removeAt(fromIndex)
    }
    val targetIndex = insertionIndex.coerceIn(0, remainingItems.size)
    remainingItems.add(targetIndex, movedItem)
    return remainingItems
}
