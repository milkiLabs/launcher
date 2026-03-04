/**
 * SearchViewModel.kt - ViewModel for the search feature
 *
 * This ViewModel manages all search-related state and logic.
 *
 * RESPONSIBILITIES:
 * - Hold and update search UI state
 * - Coordinate search across providers
 * - NOT responsible for action execution (that's ActionExecutor)
 *
 * ACTION HANDLING:
 * User actions are handled via LocalSearchActionHandler (CompositionLocal),
 * which delegates to ActionExecutor. This keeps the ViewModel focused on
 * state management only.
 *
 * ARCHITECTURE: SEPARATED QUERY & RESULTS
 * ========================================
 * The key design principle is that query text (user-driven, synchronous) and
 * search results (system-driven, asynchronous) have different lifecycles and
 * are managed separately:
 *
 *   LAYER 1 — Input state (written directly from UI/Activity, instant):
 *     stateHolder.query, stateHolder.isSearchVisible,
 *     stateHolder.hasContactsPermission, stateHolder.hasFilesPermission
 *
 *   LAYER 2 — Background data (loaded async, changes infrequently):
 *     stateHolder.installedApps, stateHolder.recentApps
 *     (combined into stateHolder.backgroundState)
 *
 *   LAYER 3 — Search pipeline output (async, may be slow):
 *     stateHolder.searchOutput (results, provider config, loading flag)
 *
 *   LAYER 4 — Final UI state (combines all layers for Compose):
 *     uiState: StateFlow<SearchUiState>
 *
 * WHY THIS SEPARATION MATTERS:
 * The query text is NEVER part of the search pipeline's output. The pipeline
 * takes the query as INPUT and produces results as OUTPUT, but it never writes
 * back to the query. This means a slow search (files, contacts) can never
 * overwrite the TextField with stale text — the race condition that caused
 * disappearing characters is eliminated BY DESIGN, not by patches.
 */

package com.milki.launcher.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.domain.search.ClipboardSuggestionResolver
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerResolver
import kotlinx.coroutines.launch

/**
 * ViewModel for the search feature.
 *
 * This ViewModel is the single source of truth for search state.
 * It coordinates between:
 * - UI input (query changes)
 * - Search providers (app, web, contacts, YouTube)
 * - Data sources (installed apps, recent apps)
 * - Settings (prefix configurations)
 *
 * ACTION HANDLING:
 * User actions (launching apps, making calls, etc.) are handled by
 * ActionExecutor via LocalSearchActionHandler. This separation keeps
 * the ViewModel focused on state management.
 *
 * PREFIX CONFIGURATION:
 * The ViewModel observes settings changes and updates the SearchProviderRegistry
 * when prefix configurations change. This allows users to customize their
 * prefixes without restarting the app.
 *
 * @property appRepository Repository for app data
 * @property contactsRepository Repository for contacts data (for recent contacts)
 * @property settingsRepository Repository for settings (including prefix configs)
 * @property providerRegistry Registry of search providers
 * @property filterAppsUseCase Use case for filtering apps
 * @property urlHandlerResolver Resolver for URL handler apps
 * @property clipboardSuggestionResolver Resolver that classifies clipboard text into one smart action suggestion
 */
class SearchViewModel(
    private val appRepository: AppRepository,
    private val contactsRepository: ContactsRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val urlHandlerResolver: UrlHandlerResolver,
    private val clipboardSuggestionResolver: ClipboardSuggestionResolver
) : ViewModel() {
    private val stateHolder = SearchViewModelStateHolder(viewModelScope)

    private val settingsAdapter = SearchViewModelSettingsAdapter(
        settingsRepository = settingsRepository,
        providerRegistry = providerRegistry
    )

    private val pipelineCoordinator = SearchViewModelPipelineCoordinator(
        providerRegistry = providerRegistry,
        filterAppsUseCase = filterAppsUseCase,
        urlHandlerResolver = urlHandlerResolver
    )

    val uiState = stateHolder.uiState

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        pipelineCoordinator.bind(
            scope = viewModelScope,
            query = stateHolder.query,
            isSearchVisible = stateHolder.isSearchVisible,
            backgroundState = stateHolder.backgroundState,
            prefixConfigurations = stateHolder.prefixConfigurations,
            existingOutput = stateHolder.searchOutput
        )
        loadInstalledApps()
        observeRecentApps()
        settingsAdapter.bind(
            scope = viewModelScope,
            prefixConfigurations = stateHolder.prefixConfigurations
        )
    }

    // ========================================================================
    // DATA LOADING
    // ========================================================================

    /**
     * Load all installed apps from the repository.
        * This runs once at startup and populates stateHolder.installedApps.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            stateHolder.installedApps.value = appRepository.getInstalledApps()
        }
    }

    /**
     * Observe recent apps from the repository.
     * Updates automatically when recent apps change (e.g., after launching an app).
     */
    private fun observeRecentApps() {
        viewModelScope.launch {
            appRepository.getRecentApps()
                .collect { updatedRecentApps ->
                    stateHolder.recentApps.value = updatedRecentApps
                }
        }
    }

    // ========================================================================
    // PUBLIC API - Called from UI
    // ========================================================================

    /**
     * Show the search dialog.
     *
        * Setting stateHolder.isSearchVisible to true triggers:
     * 1. The search pipeline re-runs (via combine), producing results for
     *    the current query (which is "" after hideSearch)
     * 2. The uiState combine emits a new state with isSearchVisible=true
     * 3. Compose shows the AppSearchDialog
     */
    fun showSearch() {
        stateHolder.isSearchVisible.value = true
        stateHolder.clipboardSuggestion.value = clipboardSuggestionResolver.resolveFromClipboard()
    }

    /**
     * Hide the search dialog and reset query text.
     *
     * WHY WE RESET THE QUERY:
     * Clearing the query ensures the next open starts fresh (no leftover text).
     * Setting visibility to false causes the pipeline to emit SearchOutput()
     * (empty), and the uiState combine to emit a hidden state.
     */
    fun hideSearch() {
        stateHolder.isSearchVisible.value = false
        stateHolder.query.value = ""
        stateHolder.clipboardSuggestion.value = null
    }

    /**
     * Update the search query.
     *
     * This is called on every keystroke from the OutlinedTextField's onValueChange.
        * It updates stateHolder.query immediately, which:
     * 1. Updates uiState.query via the uiState combine (next Compose frame)
     * 2. Triggers the search pipeline via the pipeline combine (starts new search)
     *
     * RACE CONDITION — WHY THIS IS SAFE:
        * The query goes into stateHolder.query (Layer 1) and the search pipeline
        * reads it as INPUT. The pipeline writes to stateHolder.searchOutput
        * (Layer 3), which does NOT contain the query. The final uiState.query
        * always comes from stateHolder.query,
     * never from the pipeline output. So a slow search can never overwrite
     * what the user typed.
     *
     * @param newQuery The new query text from the TextField
     */
    fun onQueryChange(newQuery: String) {
        stateHolder.query.value = newQuery
    }

    /**
     * Update contacts permission status.
     * Called from Activity when permission state changes.
     *
        * This updates stateHolder.hasContactsPermission, which is one input to
        * stateHolder.backgroundState. That background state update triggers the
        * search pipeline to re-run. If the user is in contacts mode ("c "), the
        * search can now succeed instead of showing the permission prompt.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateContactsPermission(hasPermission: Boolean) {
        stateHolder.hasContactsPermission.value = hasPermission
    }

    /**
     * Update files permission status.
     * Called from Activity when permission state changes.
     *
     * Same mechanism as contacts — feeds into background state, triggers
     * pipeline re-run.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateFilesPermission(hasPermission: Boolean) {
        stateHolder.hasFilesPermission.value = hasPermission
    }

    /**
     * Save an app to recent apps.
     * Called by ActionExecutor after launching an app.
     *
     * IMPORTANT: We save the full ComponentName (package + activity), not just packageName.
     * This preserves which specific launcher activity was used when an app has multiple.
     *
     * @param componentName The flattened ComponentName from ComponentName.flattenToString()
     */
    fun saveRecentApp(componentName: String) {
        viewModelScope.launch {
            appRepository.saveRecentApp(componentName)
        }
    }

    /**
     * Save a phone number to recent contacts.
     * Called by ActionExecutor after making a call.
     *
     * @param phoneNumber The phone number to save
     */
    fun saveRecentContact(phoneNumber: String) {
        viewModelScope.launch {
            contactsRepository.saveRecentContact(phoneNumber)
        }
    }

    /**
     * Clear the search query and show recent apps.
     * Convenience method — equivalent to onQueryChange("").
     */
    fun clearQuery() {
        stateHolder.query.value = ""
    }
}
