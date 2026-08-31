package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropController
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSurfaceInteractionControllerTest {

    private val directionalBindings = HomeBackgroundGestureBindings(
        onEmptyAreaTap = {},
        onTrigger = {},
        configuredTriggers = setOf(
            LauncherTrigger.HOME_TAP,
            LauncherTrigger.HOME_SWIPE_UP,
            LauncherTrigger.HOME_SWIPE_DOWN
        )
    )

    @Test
    fun only_idle_interaction_allows_background_gestures() {
        val blockingModes = listOf(
            HomeSurfaceInteraction.ContextMenu("app:1", longPressInProgress = false),
            HomeSurfaceInteraction.WidgetPopup("widget:1"),
            HomeSurfaceInteraction.InternalDrag("app:1"),
            HomeSurfaceInteraction.ExternalDrag(HomeSurfaceExternalDragState(isActive = true)),
            HomeSurfaceInteraction.WidgetTransform("widget:1")
        )

        assertTrue(
            HomeSurfaceInteraction.Idle
                .toBackgroundGesturePolicy(directionalBindings)
                .canStartBackgroundGesture
        )
        blockingModes.forEach { mode ->
            assertFalse(mode.toBackgroundGesturePolicy(directionalBindings).canStartBackgroundGesture)
        }
    }

    @Test
    fun policy_exposes_only_bound_gestures() {
        val policy = HomeSurfaceInteraction.Idle.toBackgroundGesturePolicy(
            HomeBackgroundGestureBindings(
                onEmptyAreaTap = {},
                configuredTriggers = setOf(
                    LauncherTrigger.HOME_TAP,
                    LauncherTrigger.HOME_SWIPE_UP,
                    LauncherTrigger.HOME_SWIPE_DOWN
                )
            )
        )

        assertTrue(LauncherTrigger.HOME_TAP in policy.enabledTriggers)
        assertFalse(LauncherTrigger.HOME_SWIPE_UP in policy.enabledTriggers)
        assertFalse(LauncherTrigger.HOME_SWIPE_DOWN in policy.enabledTriggers)
    }

    @Test
    fun internal_drag_replaces_menu_and_popup() {
        val controller = newController()
        val item = samplePinnedApp(id = "app:drag")

        assertTrue(controller.showItemMenu(item.id))
        assertTrue(controller.startInternalDrag(item))

        assertEquals(HomeSurfaceInteraction.InternalDrag(item.id), controller.interaction)
        assertNull(controller.menuShownForItemId)
        assertFalse(controller.isMenuGestureActive)

        controller.reset()
        controller.showWidgetPopup("widget:7")

        assertTrue(controller.startInternalDrag(item))
        assertEquals(HomeSurfaceInteraction.InternalDrag(item.id), controller.interaction)
        assertNull(controller.widgetPopupShownForItemId)
    }

    @Test
    fun active_drag_cannot_be_replaced_by_other_interactions() {
        val controller = newController()
        val item = samplePinnedApp(id = "app:drag")

        assertTrue(controller.startInternalDrag(item))

        assertFalse(controller.showItemMenu("app:other"))
        controller.showWidgetPopup("widget:7")
        assertFalse(controller.startWidgetTransform("widget:42"))
        assertEquals(HomeSurfaceInteraction.InternalDrag(item.id), controller.interaction)
    }

    @Test
    fun external_drag_supersedes_local_interaction_and_clears_when_finished() {
        val controller = newController()
        val item = samplePinnedApp(id = "app:drag")
        val target = GridPosition(row = 2, column = 1)
        val payload = ExternalDragItem.App(
            appInfo = com.milki.launcher.domain.model.AppInfo(
                name = "Example",
                packageName = "com.example",
                activityName = "MainActivity"
            )
        )

        assertTrue(controller.startInternalDrag(item))
        controller.onExternalDragStarted()
        controller.onExternalDragMoved(targetPosition = target, item = payload)

        assertFalse(controller.interaction is HomeSurfaceInteraction.InternalDrag)
        assertTrue(controller.externalDragState.isActive)
        assertEquals(target, controller.externalDragState.targetPosition)
        assertNotNull(controller.externalDragState.item)

        controller.onExternalDragEnded()

        assertEquals(HomeSurfaceInteraction.Idle, controller.interaction)
        assertFalse(controller.externalDragState.isActive)
        assertNull(controller.externalDragState.targetPosition)
        assertNull(controller.externalDragState.item)
    }

    @Test
    fun reset_returns_to_idle_and_restores_background_gestures() {
        val controller = newController()
        val item = samplePinnedApp(id = "app:drag")

        assertTrue(controller.startInternalDrag(item))
        assertFalse(controller.backgroundGesturePolicy(directionalBindings).canStartBackgroundGesture)

        controller.reset()

        assertEquals(HomeSurfaceInteraction.Idle, controller.interaction)
        assertTrue(controller.backgroundGesturePolicy(directionalBindings).canStartBackgroundGesture)
    }

    private fun newController(): HomeSurfaceInteractionController {
        return HomeSurfaceInteractionController(
            dragController = AppDragDropController(GridConfig.Default)
        )
    }

    private fun samplePinnedApp(id: String): HomeItem.PinnedApp {
        return HomeItem.PinnedApp(
            id = id,
            packageName = "com.example.app",
            activityName = "MainActivity",
            label = "Example",
            position = GridPosition(row = 0, column = 0)
        )
    }
}
