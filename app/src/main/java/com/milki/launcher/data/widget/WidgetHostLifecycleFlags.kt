package com.milki.launcher.data.widget

internal data class WidgetHostLifecycleFlags(
    val activityStarted: Boolean = false,
    val activityResumed: Boolean = false,
    val stateIsNormal: Boolean = false
) {
    fun shouldListen(): Boolean {
        return activityStarted && activityResumed && stateIsNormal
    }
}
