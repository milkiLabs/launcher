/**
 * HomeViewModel.kt - ViewModel for the home screen pinned items
 *
 * This ViewModel manages the state of pinned items on the home screen.
 * It follows the same Unidirectional Data Flow pattern as SearchViewModel.
 *
 * RESPONSIBILITIES:
 * - Hold and update home screen UI state
 * - Coordinate with HomeRepository for persistence
 * - Provide actions for pinning/unpinning items
 *
 * STATE:
 * - pinnedItems: Flow of pinned items from repository
 * - isLoading: Whether data is being loaded
 */

package com.milki.launcher.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State for the home screen.
 *
 * @property pinnedItems List of items pinned to the home screen
 * @property isLoading Whether data is being loaded
 * @property showRemoveDialog Whether the remove confirmation dialog is shown
 * @property itemToRemove The item being considered for removal
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
 * Manages pinned items state and provides actions for pinning/unpinning.
 * All operations are non-blocking and use coroutines.
 *
 * @property homeRepository Repository for pinned items persistence
 */
class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    // ========================================================================
    // UI STATE
    // ========================================================================

    /**
     * Private mutable state flow for UI state.
     */
    private val _uiState = MutableStateFlow(HomeUiState())

    /**
     * Public immutable state flow for UI to collect.
     *
     * The pinned items are sourced directly from the repository flow,
     * combined with local UI state (dialogs, loading).
     */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        observePinnedItems()
    }

    /**
     * Observe pinned items from the repository.
     *
     * The repository flow is converted to a StateFlow that survives
     * configuration changes and can be collected in Compose.
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

    // ========================================================================
    // PIN ACTIONS
    // ========================================================================

    /**
     * Pin any item to the home screen.
     *
     * This is the main method called from the UI via LocalPinAction.
     * It accepts any HomeItem subtype and adds it to the repository.
     *
     * @param item The item to pin
     */
    fun pinItem(item: HomeItem) {
        viewModelScope.launch {
            homeRepository.addPinnedItem(item)
        }
    }

    /**
     * Pin an app to the home screen.
     *
     * Creates a PinnedApp from the AppInfo and saves it to the repository.
     *
     * @param appInfo The app to pin
     */
    fun pinApp(appInfo: AppInfo) {
        viewModelScope.launch {
            val pinnedApp = HomeItem.PinnedApp.fromAppInfo(appInfo)
            homeRepository.addPinnedItem(pinnedApp)
        }
    }

    /**
     * Pin a file to the home screen.
     *
     * Creates a PinnedFile from the FileDocument and saves it to the repository.
     *
     * @param file The file to pin
     */
    fun pinFile(file: FileDocument) {
        viewModelScope.launch {
            val pinnedFile = HomeItem.PinnedFile.fromFileDocument(file)
            homeRepository.addPinnedItem(pinnedFile)
        }
    }

    /**
     * Pin an app shortcut to the home screen.
     *
     * @param shortcut The shortcut to pin
     */
    fun pinShortcut(shortcut: HomeItem.AppShortcut) {
        viewModelScope.launch {
            homeRepository.addPinnedItem(shortcut)
        }
    }

    /**
     * Remove a pinned item from the home screen.
     *
     * @param id The ID of the item to remove
     */
    fun removePinnedItem(id: String) {
        viewModelScope.launch {
            homeRepository.removePinnedItem(id)
        }
    }

    /**
     * Show the remove confirmation dialog for an item.
     *
     * @param item The item to potentially remove
     */
    fun showRemoveDialog(item: HomeItem) {
        _uiState.value = _uiState.value.copy(
            showRemoveDialog = true,
            itemToRemove = item
        )
    }

    /**
     * Dismiss the remove confirmation dialog.
     */
    fun dismissRemoveDialog() {
        _uiState.value = _uiState.value.copy(
            showRemoveDialog = false,
            itemToRemove = null
        )
    }

    /**
     * Confirm removal of the item.
     *
     * Called when user confirms the remove dialog.
     */
    fun confirmRemove() {
        val item = _uiState.value.itemToRemove
        if (item != null) {
            removePinnedItem(item.id)
        }
        dismissRemoveDialog()
    }

    /**
     * Reorder pinned items.
     *
     * @param fromIndex Current position
     * @param toIndex Target position
     */
    fun reorderItems(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            homeRepository.reorderPinnedItems(fromIndex, toIndex)
        }
    }

    /**
     * Check if an item is pinned.
     *
     * @param id The ID to check
     * @return true if pinned
     */
    suspend fun isPinned(id: String): Boolean {
        return homeRepository.isPinned(id)
    }
}
