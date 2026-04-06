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

import androidx.compose.runtime.Immutable
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
 * @property apps Full repository-backed app list.
 * @property adapterItems Drawer-ready section headers + app rows.
 * @property sections Fast section metadata aligned with adapterItems.
 * @property query Current in-drawer search query.
 */
@Immutable
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
 * - Adapter rows (including section headers) are assembled from that snapshot.
 * - Query filtering runs in-memory over cached lowercase fields for responsive typing.
 */
class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val drawerAppStore: DrawerAppStore,
    private val drawerListAssembler: DrawerListAssembler
) : ViewModel() {

    private val normalAssemblyListener = DrawerAppStore.Listener { apps, _ ->
        normalAssembly.value = drawerListAssembler.assembleNormal(apps)
    }

    /**
     * Shared installed-app stream scoped to this ViewModel.
     */
    private val installedAppsStream = appRepository.observeInstalledApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList()
    )

    /** Tracks whether the initial load is running. */
    private val isLoading = MutableStateFlow(true)

    /** Current in-drawer query used to filter visible apps. */
    private val query = MutableStateFlow("")

    /** Cached drawer model for the common blank-query case. */
    private val normalAssembly = MutableStateFlow(DrawerListAssembler.Result(
        items = emptyList(),
        sections = emptyList()
    ))

    /** Public state observed by Compose. */
    val uiState = combine(
        isLoading,
        drawerAppStore.apps,
        normalAssembly,
        query
    ) { loading, apps, cachedNormalAssembly, searchQuery ->
        val assembly = if (searchQuery.isBlank()) {
            cachedNormalAssembly
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppDrawerUiState(isLoading = true)
    )

    init {
        drawerAppStore.addListener(normalAssemblyListener)
        observeInstalledApps()
    }

    override fun onCleared() {
        drawerAppStore.removeListener(normalAssemblyListener)
        super.onCleared()
    }

    /**
     * Collects installed apps from the repository and commits snapshots to store.
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
