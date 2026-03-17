package com.milki.launcher.data.widget

interface WidgetHostFacade {
    fun setActivityStarted(started: Boolean)
    fun setActivityResumed(resumed: Boolean)
    fun setStateIsNormal(isNormal: Boolean)
}
