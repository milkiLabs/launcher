package com.milki.launcher.ui.components.dragdrop

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
                        return@OnDragListener false
                    }

                    hasActiveSession = true
                    callbacks.onStarted()

                    activeDragAppInfo = ExternalDragPayloadCodec.decodeAppInfo(event)
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    if (!hasActiveSession) return@OnDragListener false

                    val dragAppInfo = activeDragAppInfo
                        ?: ExternalDragPayloadCodec.decodeAppInfo(event)

                    if (dragAppInfo != null && activeDragAppInfo == null) {
                        activeDragAppInfo = dragAppInfo
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

                    callbacks.onDropped(appInfo, localOffset)
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    val eventResult = event.result

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
