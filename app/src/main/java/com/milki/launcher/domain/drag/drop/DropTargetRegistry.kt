package com.milki.launcher.domain.drag.drop

class DropTargetRegistry<C>(
    private val nodes: List<DropTargetNode<C>>
) {
    fun dispatch(context: C): DropDecision {
        for (node in nodes) {
            when (val decision = node.evaluate(context)) {
                DropDecision.Pass -> Unit
                else -> return decision
            }
        }
        return DropDecision.Rejected(RejectReason.PAYLOAD_UNSUPPORTED)
    }
}
