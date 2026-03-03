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
 * This ViewModel now acts as the single home mutation coordinator.
 * UI actions still originate from LocalSearchActionHandler + ActionExecutor,
 * but ActionExecutor routes home write actions back into this ViewModel through
 * HomeMutationHandler, so ordering stays serialized in one place.
 *
 * POSITION MANAGEMENT:
 * Each item has a grid position (row, column). The ViewModel provides
 * functions to move items to new positions when the user drags them.
 */

package com.milki.launcher.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.FileDocument
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
 * - Handle all home layout writes (move/pin/unpin/drop) through one serialized path
 *
 * Note: Pinning/unpinning actions are handled by ActionExecutor via SearchResultAction.
 * This separation keeps the action handling logic centralized and consistent.
 */
class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel(), HomeMutationHandler {

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
     * Execute a home-layout mutation through one serialized coordinator path.
     *
     * WHY THIS HELPER EXISTS:
     * - Guarantees ordering across all home layout writes handled by this ViewModel.
     * - Keeps loading/error bookkeeping consistent.
     * - Prevents multiple call sites from duplicating mutex/try-catch logic.
     */
    private fun launchSerializedHomeMutation(
        fallbackErrorMessage: String,
        mutation: suspend () -> Boolean
    ) {
        viewModelScope.launch {
            positionUpdateMutex.withLock {
                pendingPositionUpdateCount.update { current -> current + 1 }
                lastMoveErrorMessage.value = null

                try {
                    val wasApplied = mutation()
                    if (!wasApplied) {
                        lastMoveErrorMessage.value = fallbackErrorMessage
                    }
                } catch (exception: Exception) {
                    lastMoveErrorMessage.value = exception.message ?: fallbackErrorMessage
                } finally {
                    pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
                }
            }
        }
    }

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
        * If the target position is occupied by another item, the move is rejected.
     *
     * @param itemId The ID of the item to move
     * @param newPosition The new grid position (row, column)
     */
    fun moveItemToPosition(itemId: String, newPosition: GridPosition) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Target position is occupied or item no longer exists"
        ) {
            val currentItems = homeRepository.pinnedItems.first()
            val currentItem = currentItems.firstOrNull { it.id == itemId } ?: return@launchSerializedHomeMutation false

            if (currentItem.position == newPosition) {
                return@launchSerializedHomeMutation true
            }

            homeRepository.moveItemToPositionIfEmpty(itemId, newPosition)
        }
    }

    /**
     * Pins a dropped app if needed, or moves the already-pinned app to the drop position.
     *
        * DROP HANDLING RULES:
        * 1) If the app is not currently pinned: pin directly into the requested cell.
        * 2) If the app is already pinned: move existing item to that cell.
        * 3) If another item already occupies the target cell: reject the drop.
        *
        * WHY THIS IS ATOMIC:
        * Repository performs pin-or-move in one edit transaction to avoid
        * transient "added in wrong slot then moved" intermediate states.
     *
     * @param appInfo The dropped app payload from drag-and-drop
     * @param dropPosition The target grid position where user released the drag
     */
    fun pinOrMoveAppToPosition(appInfo: AppInfo, dropPosition: GridPosition) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Target position is occupied"
        ) {
            val pinnedApp = HomeItem.PinnedApp.fromAppInfo(appInfo)
            val wasApplied = homeRepository.pinOrMoveItemToPosition(
                item = pinnedApp,
                targetPosition = dropPosition
            )

            wasApplied
        }
    }

    /**
     * Pin an app from search/home actions through the same serialized mutation path.
     */
    override fun pinApp(appInfo: AppInfo) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to pin app"
        ) {
            val pinnedApp = HomeItem.PinnedApp.fromAppInfo(appInfo)
            val existingItem = homeRepository.pinnedItems.first().firstOrNull { it.id == pinnedApp.id }
            if (existingItem != null) {
                return@launchSerializedHomeMutation true
            }

            homeRepository.addPinnedItem(pinnedApp)
            true
        }
    }

    /**
     * Pin a file shortcut through the same serialized mutation path.
     */
    override fun pinFile(file: FileDocument) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to pin file"
        ) {
            val pinnedFile = HomeItem.PinnedFile.fromFileDocument(file)
            val existingItem = homeRepository.pinnedItems.first().firstOrNull { it.id == pinnedFile.id }
            if (existingItem != null) {
                return@launchSerializedHomeMutation true
            }

            homeRepository.addPinnedItem(pinnedFile)
            true
        }
    }

    /**
     * Unpin an item through the same serialized mutation path.
     */
    override fun unpinItem(itemId: String) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to remove item"
        ) {
            homeRepository.removePinnedItem(itemId)
            true
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
