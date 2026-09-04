package com.milki.launcher.ui.components.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropController
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropResult
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.interaction.grid.DoubleTapArbiter
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGesturePolicy

/**
 * The one interaction the home surface is currently handling.
 *
 * A home surface cannot sensibly be resizing a widget while showing a context
 * menu or accepting a drag. Background gestures are available precisely when
 * this state is [Idle].
 */
internal sealed interface HomeSurfaceInteraction {
    data object Idle : HomeSurfaceInteraction

    data class ContextMenu(
        val itemId: String,
        val longPressInProgress: Boolean
    ) : HomeSurfaceInteraction

    data class WidgetPopup(val itemId: String) : HomeSurfaceInteraction

    data class InternalDrag(val itemId: String) : HomeSurfaceInteraction

    data class ExternalDrag(val state: HomeSurfaceExternalDragState) : HomeSurfaceInteraction

    data class WidgetTransform(val widgetId: String) : HomeSurfaceInteraction
}

internal data class HomeSurfaceExternalDragState(
    val isActive: Boolean = false,
    val targetPosition: GridPosition? = null,
    val item: ExternalDragDropItem? = null
)

internal data class HomeWidgetTransformSession(
    val widgetId: String
)

internal fun HomeSurfaceInteraction.toBackgroundGesturePolicy(
    bindings: HomeBackgroundGestureBindings
): HomeBackgroundGesturePolicy {
    return HomeBackgroundGesturePolicy(
        canStartBackgroundGesture = this is HomeSurfaceInteraction.Idle,
        enabledTriggers = bindings.enabledTriggers()
    )
}

/**
 * Single owner for all temporary home-surface interaction state.
 *
 * This controller owns no persisted data. It coordinates exclusive UI
 * interactions and delegates movement geometry to [AppDragDropController].
 * Every exit path calls [reset], so lifecycle and pointer cancellation are
 * handled exactly like an ordinary cancelled action.
 */
@Stable
internal class HomeSurfaceInteractionController(
    private val dragController: AppDragDropController<HomeItem>
) {
    var interaction: HomeSurfaceInteraction by mutableStateOf(HomeSurfaceInteraction.Idle)
        private set

    /**
     * Double-tap arbitration state. Owned here — alongside every other piece
     * of transient homescreen interaction — so [reset] clears tap tracking
     * together with drag/menu/popup state instead of leaving a stale pending
     * tap that could mis-fire after a lifecycle boundary.
     */
    val doubleTapArbiter = DoubleTapArbiter()

    val menuShownForItemId: String?
        get() = (interaction as? HomeSurfaceInteraction.ContextMenu)?.itemId

    val isMenuGestureActive: Boolean
        get() = (interaction as? HomeSurfaceInteraction.ContextMenu)?.longPressInProgress == true

    val widgetPopupShownForItemId: String?
        get() = (interaction as? HomeSurfaceInteraction.WidgetPopup)?.itemId

    val widgetTransformSession: HomeWidgetTransformSession?
        get() = (interaction as? HomeSurfaceInteraction.WidgetTransform)
            ?.let { HomeWidgetTransformSession(widgetId = it.widgetId) }

    val externalDragState: HomeSurfaceExternalDragState
        get() = (interaction as? HomeSurfaceInteraction.ExternalDrag)?.state
            ?: HomeSurfaceExternalDragState()

    fun backgroundGesturePolicy(bindings: HomeBackgroundGestureBindings): HomeBackgroundGesturePolicy {
        return interaction.toBackgroundGesturePolicy(bindings)
    }

    fun showItemMenu(itemId: String): Boolean {
        if (!canReplacePassiveInteraction()) return false
        interaction = HomeSurfaceInteraction.ContextMenu(
            itemId = itemId,
            longPressInProgress = true
        )
        return true
    }

    fun showWidgetPopup(itemId: String) {
        if (!canReplacePassiveInteraction()) return
        interaction = HomeSurfaceInteraction.WidgetPopup(itemId)
    }

    fun dismissWidgetPopup() {
        if (interaction is HomeSurfaceInteraction.WidgetPopup) {
            interaction = HomeSurfaceInteraction.Idle
        }
    }

    fun dismissMenu() {
        if (interaction is HomeSurfaceInteraction.ContextMenu) {
            interaction = HomeSurfaceInteraction.Idle
        }
    }

    fun endLongPressGesture() {
        val menu = interaction as? HomeSurfaceInteraction.ContextMenu ?: return
        interaction = menu.copy(longPressInProgress = false)
    }

    fun startWidgetTransform(widgetId: String): Boolean {
        if (!canReplacePassiveInteraction()) return false
        interaction = HomeSurfaceInteraction.WidgetTransform(widgetId)
        return true
    }

    fun finishWidgetTransform() {
        if (interaction is HomeSurfaceInteraction.WidgetTransform) {
            interaction = HomeSurfaceInteraction.Idle
        }
    }

    fun cancelWidgetTransform() {
        finishWidgetTransform()
    }

    fun startInternalDrag(item: HomeItem): Boolean {
        if (!canReplacePassiveInteraction()) return false

        dragController.startDrag(
            item = item,
            itemId = item.id,
            startPosition = item.position
        )
        interaction = HomeSurfaceInteraction.InternalDrag(item.id)
        return true
    }

    fun updateInternalDrag(
        itemId: String,
        change: PointerInputChange?,
        dragAmount: Offset,
        layoutMetrics: AppDragDropLayoutMetrics
    ) {
        if (interaction !is HomeSurfaceInteraction.InternalDrag ||
            !dragController.isDraggingItem(itemId)
        ) {
            return
        }

        change?.consume()
        dragController.updateDrag(dragAmount, layoutMetrics)
    }

    fun finishInternalDrag(
        item: HomeItem,
        layoutMetrics: AppDragDropLayoutMetrics
    ): AppDragDropResult<HomeItem>? {
        val activeDrag = interaction as? HomeSurfaceInteraction.InternalDrag
            ?: return null
        if (activeDrag.itemId != item.id || !dragController.isDraggingItem(item.id)) {
            reset()
            return null
        }

        interaction = HomeSurfaceInteraction.Idle
        return dragController.endDrag(layoutMetrics)
    }

    fun cancelInternalDrag() {
        reset()
    }

    fun onExternalDragStarted() {
        // A platform drag supersedes any local interaction. This also prevents
        // a stale internal session from surviving a transition to external drag.
        dragController.cancelDrag()
        interaction = HomeSurfaceInteraction.ExternalDrag(HomeSurfaceExternalDragState(isActive = true))
    }

    fun onExternalDragMoved(
        targetPosition: GridPosition,
        item: ExternalDragDropItem?
    ) {
        val externalDrag = interaction as? HomeSurfaceInteraction.ExternalDrag ?: return
        interaction = externalDrag.copy(
            state = externalDrag.state.copy(
                isActive = true,
                targetPosition = targetPosition,
                item = item ?: externalDrag.state.item
            )
        )
    }

    fun onExternalDropCommitted(
        targetPosition: GridPosition,
        item: ExternalDragDropItem
    ) {
        val externalDrag = interaction as? HomeSurfaceInteraction.ExternalDrag ?: return
        interaction = externalDrag.copy(
            state = externalDrag.state.copy(
                isActive = true,
                targetPosition = targetPosition,
                item = item
            )
        )
    }

    fun onExternalDragEnded() {
        if (interaction is HomeSurfaceInteraction.ExternalDrag) {
            interaction = HomeSurfaceInteraction.Idle
        }
    }

    /** Clears every transient interaction, including a cancelled pointer drag. */
    fun reset() {
        dragController.cancelDrag()
        interaction = HomeSurfaceInteraction.Idle
        doubleTapArbiter.clear()
    }

    private fun canReplacePassiveInteraction(): Boolean {
        return interaction is HomeSurfaceInteraction.Idle ||
                interaction is HomeSurfaceInteraction.ContextMenu ||
                interaction is HomeSurfaceInteraction.WidgetPopup
    }
}

@Composable
internal fun rememberHomeSurfaceInteractionController(
    dragController: AppDragDropController<HomeItem>
): HomeSurfaceInteractionController {
    return remember(dragController) {
        HomeSurfaceInteractionController(dragController = dragController)
    }
}
