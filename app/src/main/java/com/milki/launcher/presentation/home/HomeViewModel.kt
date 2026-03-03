/**
 * HomeViewModel.kt - ViewModel for the home screen pinned items
 *
 * Manages the state of pinned items on the home screen, including their
 * positions on the grid.
 *
 * ACTION HANDLING:
 * All pinning/unpinning actions are handled by ActionExecutor via SearchResultAction:
 * - SearchResultAction.PinApp: Pins an app to the home screen
 * - SearchResultAction.PinFile: Pins a file to the home screen
 * - SearchResultAction.UnpinItem: Removes an item from the home screen
 *
 * This ViewModel only observes the pinned items from the repository.
 * The UI components (PinnedItem) emit actions via LocalSearchActionHandler,
 * which are processed by ActionExecutor.
 *
 * POSITION MANAGEMENT:
 * Each item has a grid position (row, column). The ViewModel provides
 * functions to move items to new positions when the user drags them.
 */

package com.milki.launcher.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UI State for the home screen.
 *
 * @property pinnedItems List of items currently pinned to the home screen
 * @property isLoading Whether the initial load is in progress
 */
data class HomeUiState(
    val pinnedItems: List<HomeItem> = emptyList(),
    val isLoading: Boolean = true,
    val isUpdatingPositions: Boolean = false,
    val lastMoveErrorMessage: String? = null
)

/**
 * ViewModel for the home screen.
 *
 * RESPONSIBILITIES:
 * - Observe pinned items from the repository
 * - Provide pinned items state to the UI
 * - Handle item position updates when user drags items
 *
 * Note: Pinning/unpinning actions are handled by ActionExecutor via SearchResultAction.
 * This separation keeps the action handling logic centralized and consistent.
 */
class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    /**
     * Number of currently running position-update operations.
     *
     * WHY COUNTER INSTEAD OF BOOLEAN:
     * Multiple drag-drop updates can be triggered close together.
     * A counter allows us to represent true in-flight state even when
     * operations overlap briefly.
     */
    private val pendingPositionUpdateCount = MutableStateFlow(0)

    /**
     * Latest user-visible move error message.
     *
     * This stays nullable to avoid forcing UI to show any message by default.
     */
    private val lastMoveErrorMessage = MutableStateFlow<String?>(null)

    /**
     * Mutex used to serialize DataStore position writes.
     *
     * PAIN POINT REMOVED:
     * Rapid drag-drop interactions could trigger overlapping writes.
     * DataStore itself is safe, but serialization here gives deterministic
     * ordering and avoids race-like visual churn.
     */
    private val positionUpdateMutex = Mutex()

    /**
     * UI state derived from source streams.
     *
     * Sources:
     * 1) Repository pinned items
     * 2) Position update in-flight count
     * 3) Last position update error message
     *
     * This keeps UI rendering deterministic and avoids imperative state mutation.
     */
    val uiState = combine(
        homeRepository.pinnedItems,
        pendingPositionUpdateCount,
        lastMoveErrorMessage
    ) { items, pendingUpdates, moveErrorMessage ->
        HomeUiState(
            pinnedItems = items,
            isLoading = false,
            isUpdatingPositions = pendingUpdates > 0,
            lastMoveErrorMessage = moveErrorMessage
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )

    /**
     * Moves an item to a new grid position.
     *
     * This is called when the user finishes dragging an item.
     * If the target position is occupied, the items swap positions.
     *
     * @param itemId The ID of the item to move
     * @param newPosition The new grid position (row, column)
     */
    fun moveItemToPosition(itemId: String, newPosition: GridPosition) {
        viewModelScope.launch {
            positionUpdateMutex.withLock {
                // Read current snapshot once to avoid unnecessary writes.
                val currentItems = homeRepository.pinnedItems.first()
                val currentItem = currentItems.firstOrNull { it.id == itemId }

                // If the item no longer exists, no operation is needed.
                if (currentItem == null) return@withLock

                // If dropped back onto the same position, skip write.
                // This removes avoidable DataStore transactions.
                if (currentItem.position == newPosition) return@withLock

                pendingPositionUpdateCount.update { current -> current + 1 }
                lastMoveErrorMessage.value = null

                try {
                    homeRepository.updateItemPosition(itemId, newPosition)
                } catch (exception: Exception) {
                    lastMoveErrorMessage.value = exception.message
                        ?: "Failed to update item position"
                } finally {
                    pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    /**
     * Clear the last move error after the UI has consumed it.
     *
     * This keeps error signaling explicit and avoids sticky stale messages.
     */
    fun clearMoveError() {
        lastMoveErrorMessage.value = null
    }
}
