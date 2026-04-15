@file:OptIn(ExperimentalCoroutinesApi::class)

package com.milki.launcher.presentation.drawer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@Immutable
data class AppDrawerUiState(
    val isLoading: Boolean = true,
    val adapterItems: List<DrawerAdapterItem> = emptyList(),
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
    private val isLoading = MutableStateFlow(true)
    private val query = MutableStateFlow("")
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
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val uiState = combine(
        isLoading,
        visibleAssemblyItems,
        query
    ) { loading, assemblyItems, searchQuery ->
        AppDrawerUiState(
            isLoading = loading,
            adapterItems = assemblyItems,
            query = searchQuery
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppDrawerUiState(isLoading = true)
    )

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
