package com.milki.launcher.domain.reorder

import com.milki.launcher.domain.model.GridOccupancy
import com.milki.launcher.domain.model.GridPosition
import kotlin.math.abs

class GridReorderEngine(
    private val strategies: List<ReorderStrategy> = listOf(NearestFitStrategy, RejectStrategy)
) {
    fun compute(input: ReorderInput, occupancy: GridOccupancy? = null): ReorderPlan {
        val resolved = occupancy ?: GridOccupancy.fromItems(input.items, excludeItemId = input.excludeItemId)
        for (strategy in strategies) {
            val result = strategy.attempt(input, resolved)
            if (result != null) return result
        }

        return ReorderPlan(
            anchorCell = input.preferredCell,
            isValid = false,
            strategyId = ReorderStrategyId.REJECT,
            rejectReason = ReorderRejectReason.NO_SPACE
        )
    }
}

private object NearestFitStrategy : ReorderStrategy {
    override val id: ReorderStrategyId = ReorderStrategyId.NEAREST_FIT

    // Precomputed ring offsets (radius -> sorted candidates relative to the center).
    // Candidate order is deterministic: by Manhattan distance, then row, then column,
    // which mirrors the previous per-frame `sortedWith` pass without allocating
    // a set plus a sorted list on every gesture frame.
    private val ringOffsets = mutableMapOf<Int, List<Pair<Int, Int>>>()

    override fun attempt(input: ReorderInput, occupancy: GridOccupancy): ReorderPlan? {
        val maxRadius = maxOf(input.gridColumns, input.gridRows)
        var checked = 0

        for (radius in 0..maxRadius) {
            for ((dr, dc) in ring(radius)) {
                checked += 1
                val candidate = GridPosition(
                    row = input.preferredCell.row + dr,
                    column = input.preferredCell.column + dc
                )
                if (!occupancy.canPlace(
                        anchor = candidate,
                        span = input.draggedSpan,
                        gridColumns = input.gridColumns,
                        gridRows = input.gridRows,
                        excludeItemId = input.excludeItemId
                    )
                ) {
                    continue
                }
                return ReorderPlan(
                    anchorCell = candidate,
                    isValid = true,
                    strategyId = id,
                    diagnostics = ReorderDiagnostics(checkedCells = checked, searchRadius = radius)
                )
            }
        }

        return null
    }

    private fun ring(radius: Int): List<Pair<Int, Int>> {
        return ringOffsets.getOrPut(radius) {
            if (radius == 0) {
                listOf(0 to 0)
            } else {
                val cells = ArrayList<Pair<Int, Int>>((radius * 8).coerceAtLeast(4))
                for (dr in -radius..radius) {
                    for (dc in -radius..radius) {
                        if (abs(dr) != radius && abs(dc) != radius) continue
                        cells.add(dr to dc)
                    }
                }
                cells.distinct()
                    .sortedWith(
                        compareBy(
                            { abs(it.first) + abs(it.second) },
                            { it.first },
                            { it.second }
                        )
                    )
            }
        }
    }
}

private object RejectStrategy : ReorderStrategy {
    override val id: ReorderStrategyId = ReorderStrategyId.REJECT

    override fun attempt(input: ReorderInput, occupancy: GridOccupancy): ReorderPlan {
        return ReorderPlan(
            anchorCell = input.preferredCell,
            isValid = false,
            strategyId = id,
            rejectReason = ReorderRejectReason.NO_SPACE
        )
    }
}