/**
 * AppDrawerViewModel.kt - State holder for the homescreen app drawer overlay
 *
 * This ViewModel is intentionally focused on one feature only: the app drawer.
 * It owns:
 * - The full installed-app list loaded from AppRepository
 * - The drawer loading state used to render progress UI
 * - A UI-ready app list consumed directly by the drawer composable
 *
 * WHY A DEDICATED VIEWMODEL (INSTEAD OF REUSING SearchViewModel):
 * - SearchViewModel is optimized for dialog search workflows and prefix providers.
 * - Drawer requirements are different (always show all apps with one stable order,
 *   open/close controlled by launcher gestures).
 * - Keeping drawer state separate avoids coupling the drawer lifecycle to search
 *   internals and keeps both features easier to reason about for new contributors.
 */

package com.milki.launcher.presentation.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.drawer.DrawerAppStore
import com.milki.launcher.domain.drawer.DrawerModelFlags
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state consumed by the app drawer composable.
 *
 * @property isLoading Whether the repository load is still in progress.
 * @property apps Final list presented by the drawer.
 */
data class AppDrawerUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val adapterItems: List<DrawerAdapterItem> = emptyList(),
    val sections: List<DrawerSection> = emptyList(),
    val query: String = ""
)

/**
 * ViewModel for app-drawer state.
 *
 * PERFORMANCE RATIONALE:
 * - The repository already emits apps in stable alphabetical order.
 * - The drawer now renders that list directly, which removes runtime sorting work
 *   and avoids extra recompositions caused by sort-mode changes.
 * - This keeps open/close interactions lightweight and consistent.
 */
class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val drawerAppStore: DrawerAppStore,
    private val drawerListAssembler: DrawerListAssembler
) : ViewModel() {

    /**
     * Shared installed-app stream scoped to this ViewModel.
     *
     * WHY THIS EXISTS EVEN THOUGH THE REPOSITORY IS ALREADY SHARED:
     * - The repository already guarantees a single upstream PackageManager scan path
     *   for all feature consumers (search + drawer).
     * - This local stateIn adds a stable replay point inside the drawer feature so
     *   additional internal collectors can fan out without re-subscribing directly.
     * - Keeping drawer/search patterns aligned makes maintenance easier for new contributors.
     */
    private val installedAppsStream = appRepository.observeInstalledApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList()
    )

    /**
     * Tracks whether the initial load is running.
     */
    private val isLoading = MutableStateFlow(true)

    /** Current drawer query (reserved for future in-drawer search). */
    private val query = MutableStateFlow("")

    /**
     * Public state observed by Compose.
     *
     * We combine loading + app list into one immutable state object so the UI
     * remains simple and stateless.
     */
    val uiState = combine(
        isLoading,
        drawerAppStore.apps,
        query
    ) { loading, apps, searchQuery ->
        val assembly = if (searchQuery.isBlank()) {
            drawerListAssembler.assembleNormal(apps)
        } else {
            drawerListAssembler.assembleSearch(apps, searchQuery)
        }

        AppDrawerUiState(
            isLoading = loading,
            apps = apps,
            adapterItems = assembly.items,
            sections = assembly.sections,
            query = searchQuery
        )
    }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppDrawerUiState(isLoading = true)
    )

    init {
        observeInstalledApps()
    }

    /**
     * Collects installed apps from the repository's reactive stream.
     *
     * HOW THIS WORKS:
     * AppRepository.observeInstalledApps() emits the full app list immediately
     * on first collection, and then re-emits automatically whenever an app is
     * installed, uninstalled, or updated on the device. This means the drawer
     * always shows the current set of apps without any manual refresh button.
     *
     * The repository handles PackageManager queries and icon preloading
     * internally, so this method only moves data into state flows.
     */
    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsStream.collect { apps ->
                drawerAppStore.setApps(
                    apps = apps,
                    flags = DrawerModelFlags(source = "repository")
                )
                isLoading.value = false
            }
        }
    }

    fun updateQuery(query: String) {
        this.query.value = query
    }
}
