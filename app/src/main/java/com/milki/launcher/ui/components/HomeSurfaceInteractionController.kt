package com.milki.launcher.ui.components

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
import com.milki.launcher.ui.components.dragdrop.AppDragDropController
import com.milki.launcher.ui.components.dragdrop.AppDragDropLayoutMetrics
import com.milki.launcher.ui.components.dragdrop.AppDragDropResult
import com.milki.launcher.ui.components.dragdrop.ExternalDragDropItem
import com.milki.launcher.ui.components.grid.HomeBackgroundGestureBindings
import com.milki.launcher.ui.components.grid.HomeBackgroundGesturePolicy

internal data class HomeSurfaceExternalDragState(
    val isActive: Boolean = false,
    val targetPosition: GridPosition? = null,
    val item: ExternalDragDropItem? = null
)

internal data class HomeSurfaceInteractionSnapshot(
    val hasInternalDrag: Boolean,
    val isExternalDragActive: Boolean,
    val isResizeModeActive: Boolean,
    val isAnyContextMenuOpen: Boolean
)

internal fun HomeSurfaceInteractionSnapshot.toBackgroundGesturePolicy(
    bindings: HomeBackgroundGestureBindings
): HomeBackgroundGesturePolicy {
    return HomeBackgroundGesturePolicy(
        canStartBackgroundGesture =
            !isExternalDragActive &&
                !hasInternalDrag &&
                !isResizeModeActive &&
                !isAnyContextMenuOpen,
        canSwipeUp = bindings.onSwipeUp != null,
        canSwipeDown = bindings.onSwipeDown != null
    )
}

@Stable
internal class HomeSurfaceInteractionController(
    private val dragController: AppDragDropController<HomeItem>
) {
    var menuShownForItemId: String? by mutableStateOf(null)
        private set

    var isMenuGestureActive: Boolean by mutableStateOf(false)
        private set

    var resizingWidgetId: String? by mutableStateOf(null)
        private set

    var externalDragState: HomeSurfaceExternalDragState by mutableStateOf(HomeSurfaceExternalDragState())
        private set

    val snapshot: HomeSurfaceInteractionSnapshot
        get() = HomeSurfaceInteractionSnapshot(
            hasInternalDrag = dragController.session != null,
            isExternalDragActive = externalDragState.isActive,
            isResizeModeActive = resizingWidgetId != null,
            isAnyContextMenuOpen = menuShownForItemId != null
        )

    fun backgroundGesturePolicy(bindings: HomeBackgroundGestureBindings): HomeBackgroundGesturePolicy {
        return snapshot.toBackgroundGesturePolicy(bindings)
    }

    fun showItemMenu(itemId: String): Boolean {
        if (dragController.session != null) return false
        menuShownForItemId = itemId
        isMenuGestureActive = true
        return true
    }

    fun dismissMenu() {
        menuShownForItemId = null
        isMenuGestureActive = false
    }

    fun updateMenuGestureState(isActive: Boolean) {
        isMenuGestureActive = isActive
    }

    fun requestResize(widgetId: String?) {
        resizingWidgetId = widgetId
    }

    fun startInternalDrag(item: HomeItem): Boolean {
        if (dragController.session != null) return false
        dismissMenu()
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
        isMenuGestureActive = false
        return dragController.endDrag(layoutMetrics)
    }

    fun cancelInternalDrag() {
        dragController.cancelDrag()
        isMenuGestureActive = false
    }

    fun onExternalDragStarted() {
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
