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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.milki.launcher.presentation.drawer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.drawer.DrawerAppStore
import com.milki.launcher.domain.drawer.DrawerModelFlags
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * UI state consumed by the app drawer composable.
 *
 * @property isLoading Whether the repository load is still in progress.
 * @property adapterItems Drawer-ready section headers + app rows.
 * @property sections Fast section metadata aligned with adapterItems.
 * @property query Current in-drawer search query.
 */
@Immutable
data class AppDrawerUiState(
    val isLoading: Boolean = true,
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
    private val drawerListAssembler: DrawerListAssembler,
    private val assemblyContext: CoroutineContext = Dispatchers.Default
) : ViewModel() {
    companion object {
        private const val DRAWER_HIDDEN_DEFER_FLAG = "drawer-hidden"
    }

    private var resetQueryOnNextOpen = false

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

    /** Whether the drawer surface is currently visible to the user. */
    private val isDrawerVisible = MutableStateFlow(false)

    /** Cached drawer model for the common blank-query case. */
    private val normalAssembly = drawerAppStore.apps
        .mapLatest { apps ->
            withContext(assemblyContext) {
                drawerListAssembler.assembleNormal(apps)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DrawerListAssembler.Result(
                items = emptyList(),
                sections = emptyList()
            )
        )

    private val visibleAssembly = combine(
        drawerAppStore.apps,
        normalAssembly,
        query
    ) { apps, cachedNormalAssembly, searchQuery ->
        if (searchQuery.isBlank()) {
            cachedNormalAssembly
        } else {
            withContext(assemblyContext) {
                drawerListAssembler.assembleSearch(apps, searchQuery)
            }
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DrawerListAssembler.Result(
                items = emptyList(),
                sections = emptyList()
            )
        )

    /** Public state observed by Compose. */
    val uiState = combine(
        isLoading,
        visibleAssembly,
        query
    ) { loading, assembly, searchQuery ->
        AppDrawerUiState(
            isLoading = loading,
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
        drawerAppStore.enableDefer(DRAWER_HIDDEN_DEFER_FLAG)
        observeInstalledApps()
    }

    override fun onCleared() {
        drawerAppStore.disableDefer(DRAWER_HIDDEN_DEFER_FLAG)
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
        if (this.query.value == query) return

        this.query.value = query
        if (query.isNotBlank()) {
            resetQueryOnNextOpen = false
        }
    }

    fun setDrawerVisible(isVisible: Boolean) {
        if (!isVisible && query.value.isNotBlank()) {
            resetQueryOnNextOpen = true
        } else if (isVisible && resetQueryOnNextOpen) {
            query.value = ""
            resetQueryOnNextOpen = false
        }

        if (isDrawerVisible.value == isVisible) return

        isDrawerVisible.value = isVisible
        if (isVisible) {
            drawerAppStore.disableDefer(DRAWER_HIDDEN_DEFER_FLAG)
        } else {
            drawerAppStore.enableDefer(DRAWER_HIDDEN_DEFER_FLAG)
        }
    }
}
