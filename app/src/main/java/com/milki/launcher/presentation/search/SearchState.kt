package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.PermissionAccessState

import com.milki.launcher.domain.search.ActionSuggestion
import com.milki.launcher.presentation.common.ViewModelSharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Centralized mutable state holder for SearchViewModel.
 *
 * This class keeps state concerns together so the ViewModel can focus on wiring
 * and orchestration. It intentionally has no business logic (no searching,
 * no repository calls, no side effects beyond in-memory state updates).
 *
 * VISIBILITY:
 * Search visibility is NOT owned here — it is observed from the launcher
 * navigator ([LauncherNavigator.searchVisibilityFlow]), which is the single
 * source of truth for whether the search route is open.
 */
internal class SearchState(
    scope: CoroutineScope,
    installedApps: Flow<List<AppInfo>>,
    isSearchVisible: Flow<Boolean>
) {

    val query = MutableStateFlow("")

    val contactsPermissionState = MutableStateFlow(PermissionAccessState.CAN_REQUEST)
    val filesPermissionState = MutableStateFlow(PermissionAccessState.CAN_REQUEST)

    val recentApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val runtimeSettings = MutableStateFlow(SearchRuntimeSettings())
    val clipboardSuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val querySuggestion = MutableStateFlow<ActionSuggestion?>(null)
    val providerAccentColorById = MutableStateFlow<Map<String, String>>(emptyMap())

    // All inputs are hot flows (repository snapshot flow + in-memory
    // MutableStateFlows), so the shared cold-sharing policy is safe here: on
    // re-subscription every upstream replays its current value immediately.

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
    }.stateIn(scope, ViewModelSharingStarted, SearchBackgroundState())

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
