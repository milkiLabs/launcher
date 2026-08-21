package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.PermissionAccessState

import com.milki.launcher.domain.search.ActionSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Centralized mutable state holder for SearchViewModel.
 *
 * This class keeps state concerns together so the ViewModel can focus on wiring
 * and orchestration. It intentionally has no business logic (no searching,
 * no repository calls, no side effects beyond in-memory state updates).
 */
internal class SearchState(
    scope: CoroutineScope
) {

    val query = MutableStateFlow("")
    val isSearchVisible = MutableStateFlow(false)

    val contactsPermissionState = MutableStateFlow(PermissionAccessState.CAN_REQUEST)
    val filesPermissionState = MutableStateFlow(PermissionAccessState.CAN_REQUEST)

    val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val searchOutput = MutableStateFlow(SearchPipelineOutput())
    val runtimeSettings = MutableStateFlow(SearchRuntimeSettings())
    val clipboardSuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val querySuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val providerAccentColorById = MutableStateFlow<Map<String, String>>(emptyMap())

    // Both stateIn flows below deliberately diverge from ViewModelSharingStarted
    // (WhileSubscribed(5_000)) by using SharingStarted.Eagerly: uiState.value is
    // read synchronously outside any collection context (SurfaceStateCoordinator
    // via LauncherHostRuntime checks uiState.value.isSearchVisible for back-press
    // and onResume reconciliation). With WhileSubscribed, inputs mutated while no
    // collector is attached (e.g. hideSearch() when the search surface is not
    // composed) would leave .value stale until the next subscription. Every input
    // here is an in-memory MutableStateFlow, so keeping the combines hot is cheap.

    val backgroundState: StateFlow<SearchBackgroundState> = combine(
        installedApps,
        recentApps,
        contactsPermissionState,
        filesPermissionState
    ) { installed, recent, contactsPermissionState, filesPermissionState ->
        SearchBackgroundState(
            installedApps = installed,
            recentApps = recent,
            contactsPermissionState = contactsPermissionState,
            filesPermissionState = filesPermissionState
        )
    }.stateIn(scope, SharingStarted.Eagerly, SearchBackgroundState())

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        isSearchVisible,
        searchOutput,
        runtimeSettings,
        clipboardSuggestion,
        querySuggestion,
        providerAccentColorById
    ) { flows ->
        val currentQuery = flows[0] as String
        val visible = flows[1] as Boolean
        val output = flows[2] as SearchPipelineOutput
        val runtimeSettings = flows[3] as SearchRuntimeSettings
        val clipSuggestion = flows[4] as ActionSuggestion?
        val qSuggestion = flows[5] as ActionSuggestion?
        @Suppress("UNCHECKED_CAST")
        val colorMap = flows[6] as Map<String, String>

        val isSearchVisible = visible && runtimeSettings.isSettingsLoaded

        SearchUiState(
            query = currentQuery,
            isSearchVisible = isSearchVisible,
            searchLayout = runtimeSettings.searchLayout,
            results = if (visible) output.results else emptyList(),
            activeProviderConfig = if (visible) output.activeProviderConfig else null,
            isLoading = visible && output.isLoading,
            clipboardSuggestion = if (visible) clipSuggestion else null,
            querySuggestion = if (isSearchVisible) qSuggestion else null,
            providerAccentColorById = colorMap,
            suggestedActionSources = if (visible) runtimeSettings.searchSources else emptyList(),
            defaultSearchSourceId = runtimeSettings.defaultSearchSourceId
        )
    }.stateIn(scope, SharingStarted.Eagerly, SearchUiState())
}
