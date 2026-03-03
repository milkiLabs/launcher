/**
 * AppDragDropModifiers.kt - Shared drag-drop gesture modifiers
 *
 * This file provides small, reusable extensions so any launcher surface can
 * attach drag gestures without duplicating pointer-input setup.
 */

package com.milki.launcher.ui.components.dragdrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import com.milki.launcher.ui.components.grid.detectDragGesture

/**
 * Callback bundle for drag gestures used by launcher drag/drop surfaces.
 *
 * onLongPressRelease fires when the finger lifts after a long press without
 * exceeding the drag threshold. Callers use this to transition non-interactive
 * UI (e.g., a non-focusable menu popup) into its interactive state.
 */
data class AppDragDropGestureCallbacks(
	val onTap: () -> Unit,
	val onLongPress: (position: Offset) -> Unit,
	val onLongPressRelease: () -> Unit = {},
	val onDragStart: () -> Unit,
	val onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
	val onDragEnd: () -> Unit,
	val onDragCancel: () -> Unit
)

/**
 * Attaches launcher drag-drop gesture handling to any composable.
 *
 * [key] should be stable and include values that require detector recreation,
 * such as the item id and current grid position.
 */
fun Modifier.appDragDropGestures(
	key: Any?,
	dragThresholdPx: Float,
	callbacks: AppDragDropGestureCallbacks
): Modifier {
	return this.detectDragGesture(
		key = key,
		dragThreshold = dragThresholdPx,
		onTap = callbacks.onTap,
		onLongPress = callbacks.onLongPress,
		onLongPressRelease = callbacks.onLongPressRelease,
		onDragStart = callbacks.onDragStart,
		onDrag = callbacks.onDrag,
		onDragEnd = callbacks.onDragEnd,
		onDragCancel = callbacks.onDragCancel
	)
}
