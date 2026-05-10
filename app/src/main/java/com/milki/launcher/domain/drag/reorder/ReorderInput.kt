package com.milki.launcher.domain.drag.reorder

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

enum class ReorderMode {
    Preview,
    Commit
}

data class ReorderInput(
    val items: List<HomeItem>,
    val preferredCell: GridPosition,
    val draggedSpan: GridSpan,
    val gridColumns: Int,
    val gridRows: Int,
    val excludeItemId: String? = null,
    val mode: ReorderMode
)
