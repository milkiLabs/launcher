package com.milki.launcher.domain.drag.drop

sealed interface DropDecision {
    data object Pass : DropDecision
    data object Accepted : DropDecision
    data class Rejected(val reason: RejectReason) : DropDecision
}

enum class RejectReason {
    OCCUPIED_TARGET,
    INVALID_FOLDER_ROUTE,
    INVALID_WIDGET_ROUTE,
    PAYLOAD_UNSUPPORTED,
    OUT_OF_BOUNDS,
    INTERNAL_ERROR
}
