/**
 * AppDragDropContract.kt - Reusable drag and drop core for launcher surfaces
 *
 * This file contains a generic drag-and-drop controller that is intentionally
 * independent from any specific UI component. The home grid uses it today,
 * but the same controller can be reused later for dock editing, folder editing,
 * or any other launcher surface that relies on cell-based placement.
 *
 * DESIGN GOALS:
 * 1) Keep drag state in one place with a clear lifecycle.
 * 2) Keep coordinate math centralized and deterministic.
 * 3) Keep API generic so it can be reused by different item types.
 * 4) Make end/cancel operations idempotent and safe.
 */

package com.milki.launcher.ui.components.dragdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.ui.components.grid.GridConfig
import kotlin.math.roundToInt

/**
 * Geometry values required for drag calculations.
 *
 * The UI layer computes these values from layout constraints and passes them
 * to the controller on each update/end call. This keeps the controller pure
 * with respect to view measurement while still allowing robust clamping.
 */
data class AppDragDropLayoutMetrics(
	val cellWidthPx: Float,
	val cellHeightPx: Float,
	val columns: Int,
	val rows: Int
) {
	/**
	 * Validates and clamps a [GridPosition] to this metrics range.
	 */
	fun clamp(position: GridPosition): GridPosition {
		return GridPosition(
			row = position.row.coerceIn(0, rows - 1),
			column = position.column.coerceIn(0, columns - 1)
		)
	}

	/**
	 * Converts a cell position to its pixel top-left position.
	 */
	fun cellToPixel(position: GridPosition): Offset {
		return Offset(
			x = position.column * cellWidthPx,
			y = position.row * cellHeightPx
		)
	}

	/**
	 * Converts a local pixel position inside the drop surface to a cell.
	 */
	fun pixelToCell(localPixelPosition: Offset): GridPosition {
		val column = (localPixelPosition.x / cellWidthPx).toInt()
		val row = (localPixelPosition.y / cellHeightPx).toInt()
		return clamp(GridPosition(row = row, column = column))
	}

	/**
	 * Computes target cell by applying [offset] to [startPosition].
	 */
	fun calculateTarget(startPosition: GridPosition, offset: Offset): GridPosition {
		val targetColumn = startPosition.column + (offset.x / cellWidthPx).roundToInt()
		val targetRow = startPosition.row + (offset.y / cellHeightPx).roundToInt()
		return clamp(GridPosition(row = targetRow, column = targetColumn))
	}

	/**
	 * Clamps drag offsets so previews stay within valid grid bounds.
	 */
	fun clampOffset(startPosition: GridPosition, rawOffset: Offset): Offset {
		val minDragX = -startPosition.column * cellWidthPx
		val maxDragX = (columns - 1 - startPosition.column) * cellWidthPx
		val minDragY = -startPosition.row * cellHeightPx
		val maxDragY = (rows - 1 - startPosition.row) * cellHeightPx

		return Offset(
			x = rawOffset.x.coerceIn(minDragX, maxDragX),
			y = rawOffset.y.coerceIn(minDragY, maxDragY)
		)
	}
}

/**
 * Immutable snapshot of an active drag operation.
 */
data class AppDragDropSession<T>(
	val item: T,
	val itemId: String,
	val startPosition: GridPosition,
	val currentOffset: Offset = Offset.Zero,
	val hasExceededThreshold: Boolean = false
)

/**
 * Result emitted when the drag interaction completes.
 */
sealed class AppDragDropResult<out T> {
	data object Cancelled : AppDragDropResult<Nothing>()

	data class Moved<T>(
		val item: T,
		val itemId: String,
		val from: GridPosition,
		val to: GridPosition
	) : AppDragDropResult<T>()

	data class Unchanged<T>(
		val item: T,
		val itemId: String,
		val position: GridPosition
	) : AppDragDropResult<T>()
}

/**
 * Reusable controller for launcher drag and drop.
 *
 * This controller intentionally does not mutate repository data. It only tracks
 * gesture state and resolves target positions. The caller decides how to persist
 * movement once [endDrag] returns a result.
 */
@Stable
class AppDragDropController<T>(
	private val config: GridConfig
) {
	/**
	 * Current active drag session, or null when idle.
	 */
	var session: AppDragDropSession<T>? by mutableStateOf(null)
		private set

	/**
	 * Current target cell while dragging, used for drop preview highlighting.
	 */
	var targetPosition: GridPosition? by mutableStateOf(null)
		private set

	/**
	 * Starts a drag session for [item] at [startPosition].
	 */
	fun startDrag(item: T, itemId: String, startPosition: GridPosition) {
		session = AppDragDropSession(
			item = item,
			itemId = itemId,
			startPosition = startPosition,
			currentOffset = Offset.Zero,
			hasExceededThreshold = false
		)
		targetPosition = startPosition
	}

	/**
	 * Adds [delta] movement to current drag and updates target preview position.
	 */
	fun updateDrag(delta: Offset, metrics: AppDragDropLayoutMetrics) {
		val currentSession = session ?: return

		val rawOffset = currentSession.currentOffset + delta
		val clampedOffset = metrics.clampOffset(currentSession.startPosition, rawOffset)
		val hasExceeded = currentSession.hasExceededThreshold ||
			kotlin.math.abs(clampedOffset.x) > config.dragThresholdPx ||
			kotlin.math.abs(clampedOffset.y) > config.dragThresholdPx

		val updatedSession = currentSession.copy(
			currentOffset = clampedOffset,
			hasExceededThreshold = hasExceeded
		)

		session = updatedSession
		targetPosition = metrics.calculateTarget(updatedSession.startPosition, updatedSession.currentOffset)
	}

	/**
	 * Completes the drag and returns a movement result.
	 *
	 * The controller resets to idle after this call.
	 */
	fun endDrag(metrics: AppDragDropLayoutMetrics): AppDragDropResult<T> {
		val currentSession = session ?: return AppDragDropResult.Cancelled
		val finalTarget = targetPosition ?: metrics.calculateTarget(
			currentSession.startPosition,
			currentSession.currentOffset
		)

		reset()

		return if (finalTarget == currentSession.startPosition) {
			AppDragDropResult.Unchanged(
				item = currentSession.item,
				itemId = currentSession.itemId,
				position = currentSession.startPosition
			)
		} else {
			AppDragDropResult.Moved(
				item = currentSession.item,
				itemId = currentSession.itemId,
				from = currentSession.startPosition,
				to = finalTarget
			)
		}
	}

	/**
	 * Cancels any active drag and resets state.
	 *
	 * Safe to call repeatedly.
	 */
	fun cancelDrag() {
		reset()
	}

	/**
	 * Returns true when [itemId] is the actively dragged item.
	 */
	fun isDraggingItem(itemId: String): Boolean {
		return session?.itemId == itemId
	}

	/**
	 * Helper for positioning: dragged item should render at start cell while
	 * floating preview follows the finger.
	 */
	fun resolveBasePosition(itemId: String, currentPosition: GridPosition): GridPosition {
		val currentSession = session
		if (currentSession != null && currentSession.itemId == itemId) {
			return currentSession.startPosition
		}
		return currentPosition
	}

	private fun reset() {
		session = null
		targetPosition = null
	}
}

/**
 * Remembers a controller instance across recompositions.
 */
@Composable
fun <T> rememberAppDragDropController(
	config: GridConfig = GridConfig.Default
): AppDragDropController<T> {
	return remember(config) {
		AppDragDropController(config = config)
	}
}
