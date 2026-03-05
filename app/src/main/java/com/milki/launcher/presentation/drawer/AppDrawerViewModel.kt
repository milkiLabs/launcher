/**
 * AppDrawerViewModel.kt - State holder for the homescreen app drawer overlay
 *
 * This ViewModel is intentionally focused on one feature only: the app drawer.
 * It owns:
 * - The full installed-app list loaded from AppRepository
 * - The selected sorting mode for drawer rendering
 * - The sorted projection consumed by the drawer composable
 *
 * WHY A DEDICATED VIEWMODEL (INSTEAD OF REUSING SearchViewModel):
 * - SearchViewModel is optimized for dialog search workflows and prefix providers.
 * - Drawer requirements are different (always show all apps, local sort options,
 *   open/close controlled by launcher gestures).
 * - Keeping drawer state separate avoids coupling the drawer lifecycle to search
 *   internals and keeps both features easier to reason about for new contributors.
 */

package com.milki.launcher.presentation.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drawer sort options visible in the app-drawer dropdown.
 *
 * USER-FACING REQUIREMENTS COVERED:
 * - Alphabetical sorting with both directions (A→Z and Z→A)
 * - Last update date sorting
 */
enum class AppDrawerSortMode(val displayName: String) {
    ALPHABETICAL_ASC("Alphabetical (A → Z)"),
    ALPHABETICAL_DESC("Alphabetical (Z → A)"),
    LAST_UPDATED_DESC("Last update date (Newest first)")
}

/**
 * UI state consumed by the app drawer composable.
 *
 * @property isLoading Whether the repository load is still in progress.
 * @property allApps Raw app list from repository (unsorted source of truth).
 * @property sortMode Currently selected sorting mode.
 * @property sortedApps Final list presented by the drawer after sorting.
 */
data class AppDrawerUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppInfo> = emptyList(),
    val sortMode: AppDrawerSortMode = AppDrawerSortMode.ALPHABETICAL_ASC,
    val sortedApps: List<AppInfo> = emptyList()
)

/**
 * ViewModel for app-drawer state and sorting.
 */
class AppDrawerViewModel(
    private val appRepository: AppRepository
) : ViewModel() {

    /**
     * Source of truth for repository-provided app list.
     */
    private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Tracks whether the initial load is running.
     */
    private val isLoading = MutableStateFlow(true)

    /**
     * Current drawer sorting mode selected by user from dropdown.
     */
    private val sortMode = MutableStateFlow(AppDrawerSortMode.ALPHABETICAL_ASC)

    /**
     * Public state observed by Compose.
     *
     * We combine raw inputs and compute sortedApps in one place so UI remains
     * stateless and easy to test.
     */
    val uiState = combine(
        isLoading,
        installedApps,
        sortMode
    ) { loading, apps, mode ->
        val sortedApps = when (mode) {
            AppDrawerSortMode.ALPHABETICAL_ASC -> apps.sortedBy { it.nameLower }
            AppDrawerSortMode.ALPHABETICAL_DESC -> apps.sortedByDescending { it.nameLower }
            AppDrawerSortMode.LAST_UPDATED_DESC -> apps.sortedByDescending { it.lastUpdatedTimestamp }
        }

        AppDrawerUiState(
            isLoading = loading,
            allApps = apps,
            sortMode = mode,
            sortedApps = sortedApps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppDrawerUiState(isLoading = true)
    )

    init {
        loadInstalledApps()
    }

    /**
     * Reads installed apps once from repository.
     *
     * NOTE:
     * The repository already performs launcher-activity discovery and icon preload,
     * so this method intentionally stays simple and only moves data into state.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            isLoading.value = true
            installedApps.value = appRepository.getInstalledApps()
            isLoading.value = false
        }
    }

    /**
     * Update selected sort mode from the drawer dropdown.
     */
    fun setSortMode(mode: AppDrawerSortMode) {
        sortMode.value = mode
    }

    /**
     * Convenience helper used by the dropdown button to flip alphabetical order.
     *
     * Behavior:
     * - If current mode is A→Z, switch to Z→A.
     * - If current mode is Z→A, switch to A→Z.
     * - If current mode is last-updated, switch to A→Z (safe default).
     */
    fun toggleAlphabeticalDirection() {
        sortMode.value = when (sortMode.value) {
            AppDrawerSortMode.ALPHABETICAL_ASC -> AppDrawerSortMode.ALPHABETICAL_DESC
            AppDrawerSortMode.ALPHABETICAL_DESC -> AppDrawerSortMode.ALPHABETICAL_ASC
            AppDrawerSortMode.LAST_UPDATED_DESC -> AppDrawerSortMode.ALPHABETICAL_ASC
        }
    }
}
