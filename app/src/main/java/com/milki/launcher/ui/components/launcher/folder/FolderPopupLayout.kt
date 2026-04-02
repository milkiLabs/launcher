package com.milki.launcher.ui.components.launcher.folder

import kotlin.math.ceil

internal data class FolderGridLayout(
    val columns: Int,
    val rows: Int,
    val pageSize: Int,
    val pageCount: Int
)

internal fun folderGridLayoutForItemCount(itemCount: Int): FolderGridLayout {
    val safeCount = itemCount.coerceAtLeast(1)

    return when {
        safeCount == 1 -> FolderGridLayout(
            columns = 1,
            rows = 1,
            pageSize = 1,
            pageCount = 1
        )
        safeCount <= 6 -> {
            val rows = ceil(safeCount / 2f).toInt().coerceIn(1, 3)
            FolderGridLayout(
                columns = 2,
                rows = rows,
                pageSize = rows * 2,
                pageCount = 1
            )
        }
        else -> {
            val pageSize = 9
            FolderGridLayout(
                columns = 3,
                rows = 3,
                pageSize = pageSize,
                pageCount = ceil(safeCount / pageSize.toFloat()).toInt()
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
    val safePageSize = pageSize.coerceAtLeast(1)
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
    val safePageSize = pageSize.coerceAtLeast(1)
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
