package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
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
internal class SearchViewModelStateHolder(
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
    val prefixConfigurations = MutableStateFlow<ProviderPrefixConfiguration>(emptyMap())
    val clipboardSuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val querySuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val providerAccentColorById = MutableStateFlow<Map<String, String>>(emptyMap())

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

    private val presentationState = combine(
        searchOutput,
        runtimeSettings
    ) { output, runtimeSettings ->
        output to runtimeSettings
    }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        isSearchVisible,
        presentationState,
        clipboardSuggestion,
        querySuggestion
    ) { currentQuery, visible, presentationState, clipSuggestion, qSuggestion ->
        val (output, runtimeSettings) = presentationState
        SearchUiState(
            query = currentQuery,
            isSearchVisible = visible,
            results = if (visible) output.results else emptyList(),
            activeProviderConfig = if (visible) output.activeProviderConfig else null,
            isLoading = visible && output.isLoading,
            autoFocusKeyboard = runtimeSettings.autoFocusKeyboard,
            clipboardSuggestion = if (visible) clipSuggestion else null,
            querySuggestion = if (visible) qSuggestion else null,
            suggestedActionSources = if (visible) runtimeSettings.searchSources else emptyList(),
            defaultSearchSourceId = runtimeSettings.defaultSearchSourceId
        )
    }
        .combine(providerAccentColorById) { partialState, colorMap ->
            partialState.copy(providerAccentColorById = colorMap)
        }
        .stateIn(scope, SharingStarted.Eagerly, SearchUiState())
}
