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
import com.milki.launcher.ui.components.common.ItemContextMenuRegistry
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropController
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropResult
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGesturePolicy

internal data class HomeSurfaceExternalDragState(
    val isActive: Boolean = false,
    val targetPosition: GridPosition? = null,
    val item: ExternalDragDropItem? = null
)

internal data class HomeWidgetTransformSession(
    val widgetId: String
)

internal data class HomeSurfaceInteractionSnapshot(
    val hasInternalDrag: Boolean,
    val isExternalDragActive: Boolean,
    val isResizeModeActive: Boolean,
    val isAnyContextMenuOpen: Boolean,
    val isWidgetPopupOpen: Boolean
)

internal fun HomeSurfaceInteractionSnapshot.toBackgroundGesturePolicy(
    bindings: HomeBackgroundGestureBindings
): HomeBackgroundGesturePolicy {
    return HomeBackgroundGesturePolicy(
        canStartBackgroundGesture =
            !isExternalDragActive &&
            !hasInternalDrag &&
                    !isResizeModeActive &&
                    !isAnyContextMenuOpen &&
                    !isWidgetPopupOpen,
        enabledTriggers = bindings.enabledTriggers()
    )
}

@Stable
internal class HomeSurfaceInteractionController(
    private val dragController: AppDragDropController<HomeItem>
) {
    private val menuRegistry = ItemContextMenuRegistry()

    val menuShownForItemId: String?
        get() = menuRegistry.shownForItemId

    val isMenuGestureActive: Boolean
        get() = menuRegistry.isGestureActive

    var widgetPopupShownForItemId: String? by mutableStateOf(null)
        private set

    var widgetTransformSession: HomeWidgetTransformSession? by mutableStateOf(null)
        private set

    var externalDragState: HomeSurfaceExternalDragState by mutableStateOf(HomeSurfaceExternalDragState())
        private set

    val snapshot: HomeSurfaceInteractionSnapshot
        get() = HomeSurfaceInteractionSnapshot(
            hasInternalDrag = dragController.session != null,
            isExternalDragActive = externalDragState.isActive,
            isResizeModeActive = widgetTransformSession != null,
            isAnyContextMenuOpen = menuShownForItemId != null,
            isWidgetPopupOpen = widgetPopupShownForItemId != null
        )

    fun backgroundGesturePolicy(bindings: HomeBackgroundGestureBindings): HomeBackgroundGesturePolicy {
        return snapshot.toBackgroundGesturePolicy(bindings)
    }

    fun showItemMenu(itemId: String): Boolean {
        if (dragController.session != null || widgetTransformSession != null) return false
        widgetPopupShownForItemId = null
        return menuRegistry.show(itemId)
    }

    fun showWidgetPopup(itemId: String) {
        if (dragController.session != null || widgetTransformSession != null) return
        menuRegistry.dismiss()
        widgetPopupShownForItemId = itemId
    }

    fun dismissWidgetPopup() {
        widgetPopupShownForItemId = null
    }

    fun dismissMenu() {
        menuRegistry.dismiss()
    }

    fun updateMenuGestureState(isActive: Boolean) {
        if (!isActive) menuRegistry.endLongPressGesture()
    }

    fun startWidgetTransform(widgetId: String) {
        dismissMenu()
        dismissWidgetPopup()
        widgetTransformSession = HomeWidgetTransformSession(widgetId = widgetId)
    }

    fun finishWidgetTransform() {
        widgetTransformSession = null
    }

    fun cancelWidgetTransform() {
        widgetTransformSession = null
    }

    fun startInternalDrag(item: HomeItem): Boolean {
        if (dragController.session != null || widgetTransformSession != null) return false
        dismissMenu()
        dismissWidgetPopup()
        dragController.startDrag(
            item = item,
            itemId = item.id,
            startPosition = item.position
        )
        return true
    }

    fun updateInternalDrag(
        itemId: String,
        change: PointerInputChange?,
        dragAmount: Offset,
        layoutMetrics: AppDragDropLayoutMetrics
    ) {
        if (!dragController.isDraggingItem(itemId)) return
        change?.consume()
        dragController.updateDrag(dragAmount, layoutMetrics)
    }

    fun finishInternalDrag(
        item: HomeItem,
        layoutMetrics: AppDragDropLayoutMetrics
    ): AppDragDropResult<HomeItem>? {
        if (!dragController.isDraggingItem(item.id)) return null
        menuRegistry.endLongPressGesture()
        return dragController.endDrag(layoutMetrics)
    }

    fun cancelInternalDrag() {
        dragController.cancelDrag()
        menuRegistry.cancelGesture()
    }

    /**
     * Returns the homescreen to an input-ready state after its UI is no longer
     * active.
     *
     * Pointer input is cancelled when the activity is backgrounded. Unlike a
     * normal pointer-up, that cancellation may bypass item-level callbacks, so
     * no individual interaction can be relied on to clean itself up. Keeping
     * this reset at the owner of all interaction state prevents a stale drag,
     * menu, widget popup, or resize session from disabling background gestures
     * when the launcher is shown again.
     */
    fun cancelAllInteractions() {
        dragController.cancelDrag()
        menuRegistry.cancelGesture()
        widgetPopupShownForItemId = null
        widgetTransformSession = null
        externalDragState = HomeSurfaceExternalDragState()
    }

    fun onExternalDragStarted() {
        widgetTransformSession = null
        dismissMenu()
        dismissWidgetPopup()
        externalDragState = HomeSurfaceExternalDragState(isActive = true)
    }

    fun onExternalDragMoved(
        targetPosition: GridPosition,
        item: ExternalDragDropItem?
    ) {
        externalDragState = externalDragState.copy(
            isActive = true,
            targetPosition = targetPosition,
            item = item ?: externalDragState.item
        )
    }

    fun onExternalDropCommitted(
        targetPosition: GridPosition,
        item: ExternalDragDropItem
    ) {
        externalDragState = externalDragState.copy(
            isActive = true,
            targetPosition = targetPosition,
            item = item
        )
    }

    fun onExternalDragEnded() {
        externalDragState = HomeSurfaceExternalDragState()
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
