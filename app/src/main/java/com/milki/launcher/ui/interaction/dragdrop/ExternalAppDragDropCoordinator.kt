package com.milki.launcher.ui.interaction.dragdrop

import android.view.DragEvent
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragItemCache

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
        fun onMoved(localOffset: Offset, item: ExternalDragItem?)
        fun onDropped(item: ExternalDragItem, localOffset: Offset): Boolean
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
        var activeDragItem: ExternalDragItem? = null
        var hasActiveSession = false

        return View.OnDragListener { dragTargetView, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    if (!ExternalDragPayloadCodec.isLikelyLauncherPayload(event)) {
                        activeDragItem = null
                        hasActiveSession = false
                        return@OnDragListener false
                    }

                    hasActiveSession = true
                    callbacks.onStarted()

                    activeDragItem = ExternalDragPayloadCodec.decodeDragItem(event)
                        ?: ExternalDragItemCache.currentItem
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    if (!hasActiveSession) return@OnDragListener false

                    val dragItem = activeDragItem
                        ?: ExternalDragPayloadCodec.decodeDragItem(event)

                    if (dragItem != null && activeDragItem == null) {
                        activeDragItem = dragItem
                    }

                    val localOffset = ExternalDragCoordinateMapper.toLocalOffset(
                        targetView = dragTargetView,
                        event = event
                    )

                    callbacks.onMoved(localOffset, dragItem)
                    true
                }

                DragEvent.ACTION_DROP -> {
                    if (!hasActiveSession) return@OnDragListener false

                    val item = activeDragItem
                        ?: ExternalDragPayloadCodec.decodeDragItem(event)
                        ?: return@OnDragListener false

                    val localOffset = ExternalDragCoordinateMapper.toLocalOffset(
                        targetView = dragTargetView,
                        event = event
                    )

                    callbacks.onDropped(item, localOffset)
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    val eventResult = event.result

                    if (hasActiveSession) {
                        callbacks.onEnded(eventResult)
                    }

                    activeDragItem = null
                    hasActiveSession = false
                    ExternalDragItemCache.clear()
                    true
                }

                DragEvent.ACTION_DRAG_ENTERED,
                DragEvent.ACTION_DRAG_EXITED -> hasActiveSession
                else -> false
            }
        }
    }
}
