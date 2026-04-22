package com.milki.launcher.ui.components.launcher

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.ui.interaction.dragdrop.ExternalDragPayloadCodec.ExternalDragItem
import com.milki.launcher.ui.interaction.dragdrop.AppDragDropController
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.HomeBackgroundGestureBindings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSurfaceInteractionControllerTest {

    @Test
    fun snapshot_blocks_background_gestures_when_any_transient_interaction_is_active() {
        val bindings = HomeBackgroundGestureBindings(
            onEmptyAreaTap = {},
            onTrigger = {},
            configuredTriggers = setOf(
                LauncherTrigger.HOME_TAP,
                LauncherTrigger.HOME_SWIPE_UP,
                LauncherTrigger.HOME_SWIPE_DOWN
            )
        )

        val blockedByMenu = HomeSurfaceInteractionSnapshot(
            hasInternalDrag = false,
            isExternalDragActive = false,
            isResizeModeActive = false,
            isAnyContextMenuOpen = true
        )

        val blockedByDrag = blockedByMenu.copy(
            hasInternalDrag = true,
            isAnyContextMenuOpen = false
        )

        assertFalse(blockedByMenu.toBackgroundGesturePolicy(bindings).canStartBackgroundGesture)
        assertFalse(blockedByDrag.toBackgroundGesturePolicy(bindings).canStartBackgroundGesture)
    }

    @Test
    fun snapshot_exposes_enabled_triggers_from_bindings() {
        val policy = HomeSurfaceInteractionSnapshot(
            hasInternalDrag = false,
            isExternalDragActive = false,
            isResizeModeActive = false,
            isAnyContextMenuOpen = false
        ).toBackgroundGesturePolicy(
            HomeBackgroundGestureBindings(
                onEmptyAreaTap = {},
                onTrigger = {},
                configuredTriggers = setOf(
                    LauncherTrigger.HOME_TAP,
                    LauncherTrigger.HOME_SWIPE_UP,
                    LauncherTrigger.HOME_SWIPE_DOWN
                )
            )
        )

        assertTrue(policy.canStartBackgroundGesture)
        assertTrue(LauncherTrigger.HOME_TAP in policy.enabledTriggers)
        assertTrue(LauncherTrigger.HOME_SWIPE_UP in policy.enabledTriggers)
        assertTrue(LauncherTrigger.HOME_SWIPE_DOWN in policy.enabledTriggers)
    }

    @Test
    fun snapshot_only_enables_tap_when_directional_trigger_handler_is_missing() {
        val policy = HomeSurfaceInteractionSnapshot(
            hasInternalDrag = false,
            isExternalDragActive = false,
            isResizeModeActive = false,
            isAnyContextMenuOpen = false
        ).toBackgroundGesturePolicy(
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
    fun controller_clears_menu_when_internal_drag_starts() {
        val controller = HomeSurfaceInteractionController(
            dragController = AppDragDropController(GridConfig.Default)
        )
        val item = samplePinnedApp(id = "app:drag")

        assertTrue(controller.showItemMenu(item.id))
        assertTrue(controller.startInternalDrag(item))

        assertNull(controller.menuShownForItemId)
        assertFalse(controller.isMenuGestureActive)
        assertTrue(controller.snapshot.hasInternalDrag)
    }

    @Test
    fun controller_tracks_and_clears_external_drag_state() {
        val controller = HomeSurfaceInteractionController(
            dragController = AppDragDropController(GridConfig.Default)
        )
        val target = GridPosition(row = 2, column = 1)
        val payload = ExternalDragItem.App(
            appInfo = com.milki.launcher.domain.model.AppInfo(
                name = "Example",
                packageName = "com.example",
                activityName = "MainActivity"
            )
        )

        controller.onExternalDragStarted()
        controller.onExternalDragMoved(targetPosition = target, item = payload)

        assertTrue(controller.externalDragState.isActive)
        assertEquals(target, controller.externalDragState.targetPosition)
        assertNotNull(controller.externalDragState.item)

        controller.onExternalDragEnded()

        assertFalse(controller.externalDragState.isActive)
        assertNull(controller.externalDragState.targetPosition)
        assertNull(controller.externalDragState.item)
    }

    @Test
    fun controller_blocks_menu_while_widget_transform_is_active() {
        val controller = HomeSurfaceInteractionController(
            dragController = AppDragDropController(GridConfig.Default)
        )

        controller.startWidgetTransform("widget:42")

        assertFalse(controller.showItemMenu("app:1"))
        assertTrue(controller.snapshot.isResizeModeActive)

        controller.finishWidgetTransform()

        assertTrue(controller.showItemMenu("app:1"))
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
