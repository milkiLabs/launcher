/**
 * SearchViewModel.kt - ViewModel for the search feature
 *
 * This ViewModel manages all search-related state and logic.
 * It follows the Unidirectional Data Flow (UDF) pattern:
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      ViewModel                              │
 * │                                                             │
 * │  State (StateFlow)  ◄──── UI collects this                 │
 * │  Action (SharedFlow) ◄─── UI observes this for one-time    │
 * │                         events                              │
 * │                                                             │
 * │  Functions ◄──────────── UI calls these on user actions    │
 * └─────────────────────────────────────────────────────────────┘
 *
 * RESPONSIBILITIES:
 * - Hold and update search UI state
 * - Coordinate search across providers
 * - Emit navigation actions
 * - NOT responsible for actual navigation (that's Activity)
 */

package com.milki.launcher.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.parseSearchQuery
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the search feature.
 *
 * This ViewModel is the single source of truth for search state.
 * It coordinates between:
 * - UI input (query changes, result clicks)
 * - Search providers (app, web, contacts, YouTube)
 * - Data sources (installed apps, recent apps, contacts)
 *
 * @property appRepository Repository for app data
 * @property providerRegistry Registry of search providers
 * @property filterAppsUseCase Use case for filtering apps
 */
class SearchViewModel(
    private val appRepository: AppRepository,
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase
) : ViewModel() {

    // ========================================================================
    // PRIVATE STATE
    // ========================================================================

    /**
     * Private mutable state flow for UI state.
     * Only the ViewModel can modify this.
     */
    private val _uiState = MutableStateFlow(SearchUiState())

    /**
     * Public immutable state flow for UI to collect.
     */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * Private mutable shared flow for one-time actions.
     * Actions are consumed and not replayed.
     */
    private val _action = MutableSharedFlow<SearchAction>()

    /**
     * Public shared flow for UI to observe actions.
     * Use collect() to receive actions.
     */
    val action: SharedFlow<SearchAction> = _action.asSharedFlow()

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        // Load installed apps
        loadInstalledApps()

        // Observe recent apps
        observeRecentApps()
    }

    // ========================================================================
    // DATA LOADING
    // ========================================================================

    /**
     * Load all installed apps from the repository.
     * Updates the UI state with the loaded apps.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = appRepository.getInstalledApps()
            updateState { copy(installedApps = apps) }
        }
    }

    /**
     * Observe recent apps from the repository.
     * Updates automatically when recent apps change.
     */
    private fun observeRecentApps() {
        viewModelScope.launch {
            appRepository.getRecentApps()
                .collect { recentApps ->
                    updateState { copy(recentApps = recentApps) }
                }
        }
    }

    // ========================================================================
    // PUBLIC API - Called from UI
    // ========================================================================

    /**
     * Show the search dialog.
     * Also triggers an initial search to show recent apps.
     */
    fun showSearch() {
        updateState { copy(isSearchVisible = true) }
        /**
         * Trigger an empty search to show recent apps.
         * Without this, the results list would be empty until
         * the user types something.
         */
        performSearch("")
    }

    /**
     * Hide the search dialog.
     * Also clears the query.
     */
    fun hideSearch() {
        updateState {
            copy(
                isSearchVisible = false,
                query = "",
                results = emptyList(),
                activeProviderConfig = null
            )
        }
    }

    /**
     * Update the search query.
     * Triggers a new search automatically.
     *
     * @param newQuery The new query text
     */
    fun onQueryChange(newQuery: String) {
        updateState { copy(query = newQuery) }
        performSearch(newQuery)
    }

    /**
     * Handle a search result click.
     * Emits the appropriate action based on result type.
     *
     * @param result The clicked result
     */
    fun onResultClick(result: SearchResult) {
        val action = when (result) {
            is AppSearchResult -> SearchAction.LaunchApp(result.appInfo)
            is WebSearchResult -> SearchAction.OpenWebSearch(
                url = result.url,
                query = result.query,
                engine = result.engine
            )
            is YouTubeSearchResult -> SearchAction.OpenYouTubeSearch(result.query)
            is ContactSearchResult -> {
                val phone = result.contact.phoneNumbers.firstOrNull()
                if (phone != null) {
                    SearchAction.CallContact(result.contact, phone)
                } else {
                    // No phone number - just close search
                    SearchAction.CloseSearch
                }
            }
            is PermissionRequestResult -> SearchAction.RequestContactsPermission
        }

        emitAction(action)

        // Close search after action (except permission request)
        if (action.shouldCloseSearch()) {
            hideSearch()
        }
    }

    /**
     * Update contacts permission status.
     * Called from Activity when permission state changes.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateContactsPermission(hasPermission: Boolean) {
        updateState { copy(hasContactsPermission = hasPermission) }

        // Re-run search if we're in contacts mode
        val currentState = _uiState.value
        if (currentState.activeProviderConfig?.prefix == "c") {
            performSearch(currentState.query)
        }
    }

    /**
     * Save an app to recent apps.
     * Called after launching an app.
     *
     * @param packageName The package name of the launched app
     */
    fun saveRecentApp(packageName: String) {
        viewModelScope.launch {
            appRepository.saveRecentApp(packageName)
        }
    }

    /**
     * Clear the search query.
     */
    fun clearQuery() {
        updateState {
            copy(query = "", results = emptyList(), activeProviderConfig = null)
        }
    }

    // ========================================================================
    // SEARCH LOGIC
    // ========================================================================

/**
 * Perform a search based on the current query.
 *
 * Flow:
 * 1. Parse query to detect provider prefix
 * 2. If provider prefix found, use that provider
 * 3. If no prefix, filter apps (limited to 8 results for grid display)
 *
 * PERFORMANCE NOTE:
 * App results are limited to 8 because:
 * - The grid layout shows 2 rows × 4 columns = 8 items
 * - Limiting results improves performance (less data to process)
 * - Users can refine their search if the desired app isn't shown
 *
 * @param query The search query
 */
private fun performSearch(query: String) {
    val parsed = parseSearchQuery(query, providerRegistry)

    updateState {
        copy(activeProviderConfig = parsed.config)
    }

    viewModelScope.launch {
        if (parsed.provider != null) {
            // Provider search
            updateState { copy(isLoading = true) }

            try {
                val results = parsed.provider.search(parsed.query)
                updateState {
                    copy(results = results, isLoading = false)
                }
            } catch (e: Exception) {
                // Handle error (could emit error action)
                updateState { copy(isLoading = false, results = emptyList()) }
            }
        } else {
            // App search - filter and limit to 8 results for grid display
            val state = _uiState.value
            val filteredApps = filterAppsUseCase(
                query = parsed.query,
                installedApps = state.installedApps,
                recentApps = state.recentApps
            )

            /**
             * LIMIT TO 8 RESULTS:
             *
             * The grid layout (2 rows × 4 columns) displays exactly 8 apps.
             * We limit the results here rather than in the UI because:
             *
             * 1. PERFORMANCE: Processing fewer items is faster
             * 2. MEMORY: Smaller list uses less memory
             * 3. UX: Users see the most relevant 8 results
             *
             * For recent apps (empty query): The repository already limits
             * recent apps, but we enforce the limit here as a safety measure.
             *
             * For search results: We take the top 8 by priority (exact matches
             * first, then starts-with, then contains - this ordering is done
             * by FilterAppsUseCase).
             */
            val limitedApps = filteredApps.take(8)

            val results = limitedApps.map { app ->
                AppSearchResult(appInfo = app)
            }

            updateState { copy(results = results) }
        }
    }
}

    // ========================================================================
    // HELPER FUNCTIONS
    // ========================================================================

    /**
     * Update state with a transformation.
     * Provides a cleaner API for state updates.
     *
     * @param transform The transformation to apply
     */
    private inline fun updateState(transform: SearchUiState.() -> SearchUiState) {
        _uiState.update { it.transform() }
    }

    /**
     * Emit an action for the UI to handle.
     *
     * @param action The action to emit
     */
    private fun emitAction(action: SearchAction) {
        viewModelScope.launch {
            _action.emit(action)
        }
    }
}
