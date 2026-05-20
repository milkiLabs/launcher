package com.milki.launcher.domain.reorder

interface ReorderStrategy {
    val id: ReorderStrategyId
    fun attempt(input: ReorderInput, occupancy: OccupancySnapshot): ReorderPlan?
}
