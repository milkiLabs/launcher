package com.milki.launcher.data.widget

import android.util.Log

class DefaultWidgetHostFacade(
    private val widgetHostManager: WidgetHostManager
) : WidgetHostFacade {

    companion object {
        private const val TAG = "WidgetHostFacade"
    }

    private var flags = WidgetHostLifecycleFlags()
    private var isListening = false

    override fun setActivityStarted(started: Boolean) {
        flags = flags.copy(activityStarted = started)
        syncListeningState()
    }

    override fun setActivityResumed(resumed: Boolean) {
        flags = flags.copy(activityResumed = resumed)
        syncListeningState()
    }

    override fun setStateIsNormal(isNormal: Boolean) {
        flags = flags.copy(stateIsNormal = isNormal)
        syncListeningState()
    }

    private fun syncListeningState() {
        val shouldListen = flags.shouldListen()
        if (shouldListen == isListening) return

        runCatching {
            if (shouldListen) {
                widgetHostManager.startListening()
            } else {
                widgetHostManager.stopListening()
            }
        }.onSuccess {
            isListening = shouldListen
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to sync listening state shouldListen=$shouldListen", throwable)
        }
    }
}
