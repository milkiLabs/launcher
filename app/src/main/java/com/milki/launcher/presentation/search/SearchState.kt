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
    // read synchronously outside any collection context (LauncherNavigator
    // toggles search visibility from runtime callbacks). With WhileSubscribed,
    // inputs mutated while no
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

    // Grouped intermediate flows keep the final combine fully typed (max 5 args),
    // avoiding the vararg Array<Any?> + unchecked-cast version.
    private val visibilityInput = combine(query, isSearchVisible) { currentQuery, visible ->
        VisibilityInput(currentQuery, visible)
    }

    private val config = combine(runtimeSettings, providerAccentColorById) { settings, colorMap ->
        SearchConfig(settings, colorMap)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        visibilityInput,
        searchOutput,
        clipboardSuggestion,
        querySuggestion,
        config
    ) { input, output, clipSuggestion, qSuggestion, cfg ->
        val settings = cfg.settings
        val visible = input.visible
        val isSearchVisible = visible && settings.isSettingsLoaded

        SearchUiState(
            query = input.query,
            isSearchVisible = isSearchVisible,
            searchLayout = settings.searchLayout,
            results = if (visible) output.results else emptyList(),
            activeProviderConfig = if (visible) output.activeProviderConfig else null,
            isLoading = visible && output.isLoading,
            clipboardSuggestion = if (visible) clipSuggestion else null,
            querySuggestion = if (isSearchVisible) qSuggestion else null,
            providerAccentColorById = cfg.providerAccentColorById,
            suggestedActionSources = if (visible) settings.searchSources else emptyList(),
            defaultSearchSourceId = settings.defaultSearchSourceId
        )
    }.stateIn(scope, SharingStarted.Eagerly, SearchUiState())

    private data class VisibilityInput(
        val query: String,
        val visible: Boolean
    )

    private data class SearchConfig(
        val settings: SearchRuntimeSettings,
        val providerAccentColorById: Map<String, String>
    )
}
