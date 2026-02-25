/**
 * HomeViewModel.kt - ViewModel for the home screen pinned items
 *
 * Manages the state of pinned items on the home screen.
 * Pinning/unpinning is now handled by ActionExecutor via SearchResultAction.
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
 */
data class HomeUiState(
    val pinnedItems: List<HomeItem> = emptyList(),
    val isLoading: Boolean = true,
    val showRemoveDialog: Boolean = false,
    val itemToRemove: HomeItem? = null
)

/**
 * ViewModel for the home screen.
 *
 * Note: Pinning actions are handled by ActionExecutor via SearchResultAction.
 * This ViewModel only manages:
 * - Observing pinned items from repository
 * - Remove confirmation dialog state
 */
class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observePinnedItems()
    }

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

    fun removePinnedItem(id: String) {
        viewModelScope.launch {
            homeRepository.removePinnedItem(id)
        }
    }

    fun showRemoveDialog(item: HomeItem) {
        _uiState.value = _uiState.value.copy(
            showRemoveDialog = true,
            itemToRemove = item
        )
    }

    fun dismissRemoveDialog() {
        _uiState.value = _uiState.value.copy(
            showRemoveDialog = false,
            itemToRemove = null
        )
    }

    fun confirmRemove() {
        val item = _uiState.value.itemToRemove
        if (item != null) {
            removePinnedItem(item.id)
        }
        dismissRemoveDialog()
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            homeRepository.reorderPinnedItems(fromIndex, toIndex)
        }
    }
}
