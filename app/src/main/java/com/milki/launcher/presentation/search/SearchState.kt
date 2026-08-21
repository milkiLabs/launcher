package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.PermissionAccessState

import com.milki.launcher.domain.search.ActionSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
    scope: CoroutineScope,
    installedApps: Flow<List<AppInfo>>
) {

    val query = MutableStateFlow("")
    val isSearchVisible = MutableStateFlow(false)

    val contactsPermissionState = MutableStateFlow(PermissionAccessState.CAN_REQUEST)
    val filesPermissionState = MutableStateFlow(PermissionAccessState.CAN_REQUEST)

    val recentApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val runtimeSettings = MutableStateFlow(SearchRuntimeSettings())
    val clipboardSuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val querySuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val providerAccentColorById = MutableStateFlow<Map<String, String>>(emptyMap())

    // The stateIn flow below deliberately diverges from ViewModelSharingStarted
    // (WhileSubscribed(5_000)) by using SharingStarted.Eagerly: backgroundState.value
    // is read synchronously by the search pipeline outside any collection context.
    // With WhileSubscribed, inputs mutated while no collector is attached would
    // leave .value stale until the next subscription. The installed-apps input is
    // the repository's hot snapshot flow; the rest are in-memory MutableStateFlows,
    // so keeping the combine hot is cheap.

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

    // Grouped intermediate flows keep the ViewModel's final uiState combine fully
    // typed (max 5 args), avoiding the vararg Array<Any?> + unchecked-cast version.
    val visibilityInput = combine(query, isSearchVisible) { currentQuery, visible ->
        VisibilityInput(currentQuery, visible)
    }

    val config = combine(runtimeSettings, providerAccentColorById) { settings, colorMap ->
        SearchConfig(settings, colorMap)
    }

    data class VisibilityInput(
        val query: String,
        val visible: Boolean
    )

    data class SearchConfig(
        val settings: SearchRuntimeSettings,
        val providerAccentColorById: Map<String, String>
    )
}
