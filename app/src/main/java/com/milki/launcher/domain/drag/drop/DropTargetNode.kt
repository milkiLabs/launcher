package com.milki.launcher.domain.drag.drop

interface DropTargetNode<C> {
    val id: String
    fun evaluate(context: C): DropDecision
}
