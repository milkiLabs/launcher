@file:OptIn(ExperimentalCoroutinesApi::class)

package com.milki.launcher.presentation.drawer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.domain.homegraph.HomeGridDefaults
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Immutable
data class AppDrawerUiState(
    val isLoading: Boolean = true,
    val adapterItems: List<DrawerAdapterItem> = emptyList(),
    val recentlyChangedApps: List<AppInfo> = emptyList(),
    val query: String = ""
)

/**
 * Simple drawer state holder.
 *
 * The drawer only needs three pieces of local policy:
 * - current query
 * - whether the surface is visible
 * - whether hidden app-list updates should wait until the next open
 */
class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val drawerListAssembler: DrawerListAssembler,
    private val assemblyContext: CoroutineContext = Dispatchers.Default
) : ViewModel() {
    private     companion object {
        val RECENTLY_CHANGED_ROW_LIMIT = HomeGridDefaults.COLUMNS * 2
    }

    private val isLoading = MutableStateFlow(true)
    private val query = MutableStateFlow("")
    private val benchmarkScrollEventsChannel = Channel<Unit>(Channel.BUFFERED)
    private val visibleApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private var isDrawerVisible = false
    private var pendingAppsWhileHidden: List<AppInfo>? = null
    private var resetQueryOnNextOpen = false

    private val visibleAssemblyItems = combine(
        visibleApps,
        query
    ) { apps, searchQuery ->
        apps to searchQuery
    }
        .mapLatest { (apps, searchQuery) ->
            withContext(assemblyContext) {
                if (searchQuery.isBlank()) {
                    drawerListAssembler.assembleNormal(apps)
                } else {
                    drawerListAssembler.assembleSearch(apps, searchQuery)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = ViewModelSharingStarted,
            initialValue = emptyList()
        )

    private val recentlyChangedApps = visibleApps
        .mapLatest { apps ->
            withContext(assemblyContext) {
                drawerListAssembler.selectRecentlyUpdatedOrInstalled(
                    apps = apps,
                    limit = RECENTLY_CHANGED_ROW_LIMIT
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = ViewModelSharingStarted,
            initialValue = emptyList()
        )

    val uiState = combine(
        isLoading,
        visibleAssemblyItems,
        recentlyChangedApps,
        query
    ) { loading, assemblyItems, recencyApps, searchQuery ->
        AppDrawerUiState(
            isLoading = loading,
            adapterItems = assemblyItems,
            recentlyChangedApps = recencyApps,
            query = searchQuery
        )
    }.stateIn(
        scope = viewModelScope,
        started = ViewModelSharingStarted,
        initialValue = AppDrawerUiState(isLoading = true)
    )

    /**
     * Benchmark-only scroll trigger, kept outside [uiState] so production UI
     * state never carries test scaffolding. Emits one event per benchmark
     * trigger; buffered so no trigger is lost before the UI collects.
     */
    val benchmarkScrollEvents: Flow<Unit> =
        benchmarkScrollEventsChannel.receiveAsFlow()

    init {
        observeInstalledApps()
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

        if (isDrawerVisible == isVisible) return
        isDrawerVisible = isVisible

        if (isVisible) {
            pendingAppsWhileHidden?.let { apps ->
                visibleApps.value = apps
                pendingAppsWhileHidden = null
            }
        }
    }

    fun triggerBenchmarkScrollSequenceDownUp() {
        benchmarkScrollEventsChannel.trySend(Unit)
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            appRepository.observeInstalledApps().collect { apps ->
                if (isDrawerVisible) {
                    visibleApps.value = apps
                } else {
                    pendingAppsWhileHidden = apps
                }
                isLoading.value = false
            }
        }
    }
}
