package com.milki.launcher.ui.components.dragdrop

import android.util.Log
import android.view.DragEvent
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.AppInfo

/**
 * ExternalAppDragDropCoordinator.kt - Reusable platform drag/drop bridge coordinator.
 *
 * DESIGN INTENT:
 * - Centralize Android View drag listener lifecycle in one reusable class.
 * - Keep payload decode behavior consistent across all target surfaces.
 * - Keep target surfaces focused on rendering + offset-to-cell conversion only.
 */
interface ExternalAppDragDropCoordinator {

    /**
     * Callback contract implemented by each target surface integration.
     */
    interface TargetCallbacks {
        fun onStarted()
        fun onMoved(localOffset: Offset, appInfo: AppInfo?)
        fun onDropped(appInfo: AppInfo, localOffset: Offset): Boolean
        fun onEnded(result: Boolean)
    }

    /**
     * Creates an OnDragListener bound to [callbacks].
     */
    fun createListener(callbacks: TargetCallbacks): View.OnDragListener
}

/**
 * Default implementation used across launcher surfaces.
 */
@Stable
class DefaultExternalAppDragDropCoordinator : ExternalAppDragDropCoordinator {

    override fun createListener(callbacks: ExternalAppDragDropCoordinator.TargetCallbacks): View.OnDragListener {
        var activeDragAppInfo: AppInfo? = null
        var hasActiveSession = false

        return View.OnDragListener { dragTargetView, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    if (!ExternalDragPayloadCodec.isLikelyAppPayload(event)) {
                        activeDragAppInfo = null
                        hasActiveSession = false
                        Log.d("AppExternalDragDrop", "ACTION_DRAG_STARTED ignored: payload not likely app drag")
                        return@OnDragListener false
                    }

                    hasActiveSession = true
                    callbacks.onStarted()

                    activeDragAppInfo = ExternalDragPayloadCodec.decodeAppInfo(event)
                    val dragAppInfo = activeDragAppInfo
                    if (dragAppInfo != null) {
                        Log.d(
                            "AppExternalDragDrop",
                            "ACTION_DRAG_STARTED accepted for ${dragAppInfo.packageName}/${dragAppInfo.activityName}"
                        )
                    } else {
                        Log.d("AppExternalDragDrop", "ACTION_DRAG_STARTED accepted with deferred payload decode")
                    }
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    if (!hasActiveSession) return@OnDragListener false

                    val dragAppInfo = activeDragAppInfo
                        ?: ExternalDragPayloadCodec.decodeAppInfo(event)

                    if (dragAppInfo != null && activeDragAppInfo == null) {
                        activeDragAppInfo = dragAppInfo
                        Log.d(
                            "AppExternalDragDrop",
                            "Deferred payload decode resolved on DRAG_LOCATION for ${dragAppInfo.packageName}/${dragAppInfo.activityName}"
                        )
                    }

                    val localOffset = ExternalDragCoordinateMapper.toLocalOffset(
                        targetView = dragTargetView,
                        event = event
                    )

                    callbacks.onMoved(localOffset, dragAppInfo)
                    true
                }

                DragEvent.ACTION_DROP -> {
                    if (!hasActiveSession) return@OnDragListener false

                    val appInfo = activeDragAppInfo
                        ?: ExternalDragPayloadCodec.decodeAppInfo(event)
                        ?: return@OnDragListener false

                    val localOffset = ExternalDragCoordinateMapper.toLocalOffset(
                        targetView = dragTargetView,
                        event = event
                    )

                    Log.d(
                        "AppExternalDragDrop",
                        "ACTION_DROP received for ${appInfo.packageName}/${appInfo.activityName} raw=(${event.x},${event.y}) local=(${localOffset.x},${localOffset.y}) viewSize=(${dragTargetView.width},${dragTargetView.height})"
                    )

                    callbacks.onDropped(appInfo, localOffset)
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    val eventResult = event.result
                    Log.d(
                        "AppExternalDragDrop",
                        "ACTION_DRAG_ENDED result=$eventResult hadActivePayload=${activeDragAppInfo != null}"
                    )

                    if (hasActiveSession) {
                        callbacks.onEnded(eventResult)
                    }

                    activeDragAppInfo = null
                    hasActiveSession = false
                    true
                }

                DragEvent.ACTION_DRAG_ENTERED,
                DragEvent.ACTION_DRAG_EXITED -> hasActiveSession
                else -> false
            }
        }
    }
}
