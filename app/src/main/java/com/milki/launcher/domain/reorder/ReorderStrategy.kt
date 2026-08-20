package com.milki.launcher.domain.reorder

import com.milki.launcher.domain.model.GridOccupancy

interface ReorderStrategy {
    val id: ReorderStrategyId
    fun attempt(input: ReorderInput, occupancy: GridOccupancy): ReorderPlan?
}