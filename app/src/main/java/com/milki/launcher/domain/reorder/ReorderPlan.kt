package com.milki.launcher.domain.reorder

import com.milki.launcher.domain.model.GridPosition

enum class ReorderRejectReason {
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
