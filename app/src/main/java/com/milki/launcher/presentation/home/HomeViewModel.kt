/**
 * HomeViewModel.kt - ViewModel for the home screen pinned items
 *
 * Manages the state of pinned items on the home screen.
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
 */

package com.milki.launcher.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the home screen.
 *
 * @property pinnedItems List of items currently pinned to the home screen
 * @property isLoading Whether the initial load is in progress
 */
data class HomeUiState(
    val pinnedItems: List<HomeItem> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * ViewModel for the home screen.
 *
 * RESPONSIBILITIES:
 * - Observe pinned items from the repository
 * - Provide pinned items state to the UI
 *
 * Note: Pinning/unpinning actions are handled by ActionExecutor via SearchResultAction.
 * This separation keeps the action handling logic centralized and consistent.
 */
class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observePinnedItems()
    }

    /**
     * Observes pinned items from the repository.
     *
     * The repository emits a new list whenever pinned items change
     * (due to pin/unpin actions processed by ActionExecutor).
     */
    private fun observePinnedItems() {
        viewModelScope.launch {
            homeRepository.pinnedItems.collect { items ->
                _uiState.value = _uiState.value.copy(
                    pinnedItems = items,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Reorders pinned items in the grid.
     *
     * Called when the user drags an item to a new position.
     *
     * @param fromIndex Current index of the item
     * @param toIndex Target index for the item
     */
    fun reorderItems(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            homeRepository.reorderPinnedItems(fromIndex, toIndex)
        }
    }
}
