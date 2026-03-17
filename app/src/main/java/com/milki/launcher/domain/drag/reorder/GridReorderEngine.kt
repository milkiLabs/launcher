package com.milki.launcher.domain.drag.reorder

import com.milki.launcher.domain.model.GridPosition
import kotlin.math.abs

class GridReorderEngine(
    private val strategies: List<ReorderStrategy> = listOf(NearestFitStrategy, RejectStrategy)
) {
    fun compute(input: ReorderInput): ReorderPlan {
        val occupancy = OccupancyBuilder.fromItems(input.items, excludeItemId = input.excludeItemId)
        for (strategy in strategies) {
            val result = strategy.attempt(input, occupancy)
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

    override fun attempt(input: ReorderInput, occupancy: OccupancySnapshot): ReorderPlan? {
        val maxRadius = maxOf(input.gridColumns, input.gridRows)
        var checked = 0

        for (radius in 0..maxRadius) {
            val candidates = ringCandidates(input.preferredCell, radius)
                .sortedWith(compareBy<GridPosition> { manhattan(it, input.preferredCell) }
                    .thenBy { it.row }
                    .thenBy { it.column })

            for (candidate in candidates) {
                checked += 1
                if (!occupancy.isSpanFree(candidate, input.draggedSpan, input.gridColumns, input.gridRows)) {
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

    private fun ringCandidates(center: GridPosition, radius: Int): Set<GridPosition> {
        if (radius == 0) return setOf(center)

        val cells = linkedSetOf<GridPosition>()
        for (dr in -radius..radius) {
            for (dc in -radius..radius) {
                if (abs(dr) != radius && abs(dc) != radius) continue
                cells.add(GridPosition(center.row + dr, center.column + dc))
            }
        }
        return cells
    }

    private fun manhattan(a: GridPosition, b: GridPosition): Int {
        return abs(a.row - b.row) + abs(a.column - b.column)
    }
}

private object RejectStrategy : ReorderStrategy {
    override val id: ReorderStrategyId = ReorderStrategyId.REJECT

    override fun attempt(input: ReorderInput, occupancy: OccupancySnapshot): ReorderPlan {
        return ReorderPlan(
            anchorCell = input.preferredCell,
            isValid = false,
            strategyId = id,
            rejectReason = ReorderRejectReason.NO_SPACE
        )
    }
}
