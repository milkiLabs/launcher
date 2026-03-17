package com.milki.launcher.domain.drag.reorder

interface ReorderStrategy {
    val id: ReorderStrategyId
    fun attempt(input: ReorderInput, occupancy: OccupancySnapshot): ReorderPlan?
}
