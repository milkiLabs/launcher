package com.milki.launcher.domain.drag.reorder

import com.milki.launcher.domain.model.GridPosition

enum class ReorderRejectReason {
    OCCUPIED_TARGET,
    OUT_OF_BOUNDS,
    NO_SPACE
}

enum class ReorderStrategyId {
    NEAREST_FIT,
    REJECT
}

data class ReorderPlan(
    val anchorCell: GridPosition,
    val isValid: Boolean,
    val strategyId: ReorderStrategyId,
    val rejectReason: ReorderRejectReason? = null,
    val diagnostics: ReorderDiagnostics = ReorderDiagnostics()
)
