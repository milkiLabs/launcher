package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.search.ClipboardSuggestion
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

    val hasContactsPermission = MutableStateFlow(false)
    val hasFilesPermission = MutableStateFlow(false)

    val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val searchOutput = MutableStateFlow(SearchPipelineOutput())
    val prefixConfigurations = MutableStateFlow<ProviderPrefixConfiguration>(emptyMap())
    val clipboardSuggestion = MutableStateFlow<ClipboardSuggestion?>(null)
    val providerAccentColorById = MutableStateFlow<Map<String, String>>(emptyMap())

    val backgroundState: StateFlow<SearchBackgroundState> = combine(
        installedApps,
        recentApps,
        hasContactsPermission,
        hasFilesPermission
    ) { installed, recent, contactsPerm, filesPerm ->
        SearchBackgroundState(
            installedApps = installed,
            recentApps = recent,
            hasContactsPermission = contactsPerm,
            hasFilesPermission = filesPerm
        )
    }.stateIn(scope, SharingStarted.Eagerly, SearchBackgroundState())

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        isSearchVisible,
        searchOutput,
        clipboardSuggestion
    ) { currentQuery, visible, output, suggestion ->
        SearchUiState(
            query = currentQuery,
            isSearchVisible = visible,
            results = if (visible) output.results else emptyList(),
            activeProviderConfig = if (visible) output.activeProviderConfig else null,
            isLoading = visible && output.isLoading,
            clipboardSuggestion = if (visible) suggestion else null
        )
    }
        .combine(providerAccentColorById) { partialState, colorMap ->
            partialState.copy(providerAccentColorById = colorMap)
        }
        .stateIn(scope, SharingStarted.Eagerly, SearchUiState())
}
