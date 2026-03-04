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
 */

package com.milki.launcher.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.repository.SettingsRepository
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerResolver
import com.milki.launcher.domain.search.parseSearchQuery
import com.milki.launcher.util.UrlValidator
import kotlinx.coroutines.flow.*
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
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val appRepository: AppRepository,
    private val contactsRepository: ContactsRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val urlHandlerResolver: UrlHandlerResolver
) : ViewModel() {

    // ========================================================================
    // INTERNAL MODELS
    // ========================================================================

    /**
     * Consolidated inputs used by the derived-state pipeline.
     *
     * WHY THIS EXISTS:
     * The ViewModel now follows a "single derived state" architecture.
     * Instead of mutating UI state from many places, we first gather all source
     * inputs (query, visibility, recent apps, installed apps, permissions, etc.)
     * into this immutable snapshot and then compute UI state from it.
     *
     * BENEFITS:
     * - Deterministic behavior (same inputs => same output)
     * - Easier debugging (inspect one snapshot)
     * - Eliminates stale-state races
     */
    private data class SearchInputs(
        val query: String,
        val isSearchVisible: Boolean,
        val installedApps: List<AppInfo>,
        val recentApps: List<AppInfo>,
        val hasContactsPermission: Boolean,
        val hasFilesPermission: Boolean
    )

    /**
     * Encapsulates pure search computation output.
     *
     * We keep provider config and results together because both are produced
     * by the same parsing/search pass and should be updated atomically.
     */
    private data class SearchComputation(
        val activeProviderConfig: SearchProviderConfig?,
        val results: List<SearchResult>
    )

    /**
     * Intermediate typed input snapshot used to avoid heterogeneous vararg-combine
     * inference issues when building SearchInputs.
     */
    private data class SearchInputsWithoutFilesPermission(
        val query: String,
        val isSearchVisible: Boolean,
        val installedApps: List<AppInfo>,
        val recentApps: List<AppInfo>,
        val hasContactsPermission: Boolean
    )

    // ========================================================================
    // PRIVATE STATE
    // ========================================================================

    /**
     * Final derived UI state observed by Compose.
     */
    private val _uiState = MutableStateFlow(SearchUiState())

    /**
     * Public immutable state flow for UI to collect.
     */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Query input source. */
    private val searchQuery = MutableStateFlow("")

    /** Dialog visibility source. */
    private val isSearchVisible = MutableStateFlow(false)

    /** Installed-apps data source. */
    private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Recent-apps data source. */
    private val recentApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Contacts permission source. */
    private val hasContactsPermission = MutableStateFlow(false)

    /** Files permission source. */
    private val hasFilesPermission = MutableStateFlow(false)

    /**
     * Prefix configuration version source.
     *
     * WHY THIS EXISTS:
     * Prefix changes can alter parsing behavior for the SAME query text.
     * Because the query string may not change, we expose a small version token
     * that increments whenever prefix mapping changes, forcing recomputation
     * from the derived-state pipeline.
     */
    private val prefixConfigVersion = MutableStateFlow(0)

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        observeDerivedUiState()
        loadInstalledApps()
        observeRecentApps()
        observePrefixConfiguration()
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
            installedApps.value = appRepository.getInstalledApps()
        }
    }

    /**
     * Observe recent apps from the repository.
     * Updates automatically when recent apps change.
     */
    private fun observeRecentApps() {
        viewModelScope.launch {
            appRepository.getRecentApps()
                .collect { updatedRecentApps ->
                    recentApps.value = updatedRecentApps
                }
        }
    }

    /**
     * Build UI state from source flows.
     *
     * ARCHITECTURE CHANGE:
     * Old approach: Imperative updates from multiple methods + manual refresh trigger.
     * New approach: Derived-state pipeline.
     *
     * We combine all source flows into SearchInputs and then compute SearchUiState.
     * This makes recent-app updates, installed-app updates, permission updates,
     * and query updates all behave consistently.
     *
     * RELIABILITY IMPROVEMENT:
     * The empty-state race is removed because recent-app updates are first-class
     * inputs to this pipeline. When recent apps arrive, the combined flow emits,
     * and results recompute even if query is still "".
     */
    private fun observeDerivedUiState() {
        combine(
            searchQuery,
            isSearchVisible,
            installedApps,
            recentApps,
            hasContactsPermission
        ) { query, visible, installed, recent, contactsPermission ->
            SearchInputsWithoutFilesPermission(
                query = query,
                isSearchVisible = visible,
                installedApps = installed,
                recentApps = recent,
                hasContactsPermission = contactsPermission
            )
        }
            .combine(hasFilesPermission) { partialInputs, filesPermission ->
                SearchInputs(
                    query = partialInputs.query,
                    isSearchVisible = partialInputs.isSearchVisible,
                    installedApps = partialInputs.installedApps,
                    recentApps = partialInputs.recentApps,
                    hasContactsPermission = partialInputs.hasContactsPermission,
                    hasFilesPermission = filesPermission
                )
            }
            .combine(prefixConfigVersion) { inputs, _ ->
                // Prefix changes may alter parsing for the same query.
                // Passing inputs through this combine forces recomputation.
                inputs
            }
            .flatMapLatest { inputs ->
                if (!inputs.isSearchVisible) {
                    flowOf(buildHiddenUiState(inputs))
                } else {
                    flow {
                        emit(buildLoadingUiState(inputs))
                        val computation = executeSearchLogic(
                            query = inputs.query,
                            installedApps = inputs.installedApps,
                            recentApps = inputs.recentApps
                        )
                        emit(buildReadyUiState(inputs, computation))
                    }
                }
            }
            .onEach { derivedState ->
                // IMPORTANT: Always use the LIVE searchQuery value, not the
                // snapshot captured in the flow's inputs. The snapshot may be
                // stale if the user typed additional characters while a slow
                // search (files, contacts) was still running. Using the live
                // value ensures the TextField is never reverted to old text.
                _uiState.value = derivedState.copy(query = searchQuery.value)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observe settings for prefix configuration changes.
     *
     * When the user changes their prefix settings, the SearchProviderRegistry
     * needs to be updated to reflect the new prefixes. This method observes
     * the settings flow and updates the registry whenever prefix configurations change.
     *
     * REACTIVE APPROACH:
     * Instead of manually updating the registry on each settings change,
     * we use a Flow-based approach that automatically reacts to changes.
     * This ensures the registry is always in sync with the user's preferences.
     */
    private fun observePrefixConfiguration() {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.prefixConfigurations }
                .distinctUntilChanged()
                .collect { prefixConfigurations ->
                    // Update the registry with new prefix configurations
                    // This rebuilds the prefix-to-provider mappings
                    providerRegistry.updatePrefixConfigurations(prefixConfigurations)

                    // Increment version to force recompute for current query,
                    // even if text did not change.
                    prefixConfigVersion.update { current -> current + 1 }
                }
        }
    }

    // ========================================================================
    // PUBLIC API - Called from UI
    // ========================================================================

    /**
     * Show the search dialog.
     *
     * Derived-state pipeline handles recomputation automatically.
     */
    fun showSearch() {
        isSearchVisible.value = true
    }

    /**
     * Hide the search dialog and reset query text.
     */
    fun hideSearch() {
        isSearchVisible.value = false
        searchQuery.value = ""
    }

    /**
     * Update the search query.
     * Triggers a new search automatically through the reactive pipeline.
     *
     * IMMEDIATE UI UPDATE:
     * We update _uiState.query immediately (synchronously on the Main thread)
     * so the OutlinedTextField sees the correct value on the very next
     * Compose frame. Without this, there is a race condition:
     *
     *   1. User types a character → onValueChange fires with "f so"
     *   2. searchQuery StateFlow emits "f so"
     *   3. The reactive pipeline (combine → flatMapLatest) starts processing
     *   4. BUT the previous search may complete and emit a readyState with
     *      the OLD query ("f s") before the pipeline cancels the old flow
     *   5. _uiState.query is overwritten with "f s"
     *   6. Compose recomposes the TextField with value="f s", REVERTING the
     *      user's keystroke — the "o" disappears and the cursor jumps
     *
     * By updating _uiState.query immediately here, step 6 never happens
     * because the TextField always sees the latest typed value.
     *
     * This primarily affects slow providers (file search via MediaStore,
     * contact search via ContentResolver). In-memory app search is too
     * fast for the race to be noticeable.
     *
     * @param newQuery The new query text
     */
    fun onQueryChange(newQuery: String) {
        searchQuery.value = newQuery
        // Immediately reflect the typed text in the UI state so the
        // TextField is never reverted to a stale query by a completing search.
        _uiState.update { it.copy(query = newQuery) }
    }

    /**
     * Update contacts permission status.
     * Called from Activity when permission state changes.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateContactsPermission(hasPermission: Boolean) {
        hasContactsPermission.value = hasPermission
    }

    /**
     * Update files permission status.
     * Called from Activity when permission state changes.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateFilesPermission(hasPermission: Boolean) {
        hasFilesPermission.value = hasPermission
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
     * We emit empty string to searchQuery to trigger FilterAppsUseCase which
     * returns recentApps when query is blank.
     */
    fun clearQuery() {
        searchQuery.value = ""
    }

    // ========================================================================
    // SEARCH LOGIC
    // ========================================================================

    /**
     * Execute the search logic and return results.
     * 
     * This is a pure function that performs the search computation.
     * It's called from the reactive pipeline (mapLatest) and returns
     * results to be collected and displayed.
     *
     * Flow:
     * 1. Parse query to detect provider prefix
     * 2. If provider prefix found, use that provider
     * 3. If no prefix, check if query is a valid URL
     * 4. If URL detected, show URL result along with matching apps
     * 5. Otherwise, filter apps (limited to 8 results for grid display)
     *
     * PERFORMANCE NOTE:
     * App results are limited to 8 because:
     * - The grid layout shows 2 rows × 4 columns = 8 items
     * - Limiting results improves performance (less data to process)
     * - Users can refine their search if the desired app isn't shown
     *
     * THREADING NOTE:
     * This function is called within mapLatest which runs on Dispatchers.Default
     * by default when using the default context. Heavy operations (Regex matching,
     * app filtering) don't block the UI thread.
     *
     * @param query The search query
     * @param installedApps Latest installed apps snapshot
     * @param recentApps Latest recent apps snapshot
     * @return SearchComputation containing active provider + results
     */
    private suspend fun executeSearchLogic(
        query: String,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): SearchComputation {
        val parsed = parseSearchQuery(query, providerRegistry)

        if (parsed.provider != null) {
            // Provider search - delegate to the provider
            val providerResults = try {
                parsed.provider.search(parsed.query)
            } catch (e: Exception) {
                emptyList()
            }

            return SearchComputation(
                activeProviderConfig = parsed.config,
                results = providerResults
            )
        }

        // No provider prefix detected - search apps and check for URLs
        // Check if the query looks like a URL
        val urlResult = detectUrl(parsed.query)
        
        // Filter apps using the use case
        val filteredApps = filterAppsUseCase(
            query = parsed.query,
            installedApps = installedApps,
            recentApps = recentApps
        )

        val limitedApps = filteredApps.take(8)

        val appResults = limitedApps.map { app ->
            AppSearchResult(appInfo = app)
        }

        // Combine URL result with app results
        val combinedResults = if (urlResult != null) {
            listOf(urlResult) + appResults
        } else {
            appResults
        }

        return SearchComputation(
            activeProviderConfig = parsed.config,
            results = combinedResults
        )
    }

    // ========================================================================
    // URL DETECTION
    // ========================================================================

    /**
     * Detect if the query looks like a URL and create a UrlSearchResult.
     *
     * DELEGATION PATTERN:
     * This function delegates all URL validation logic to UrlValidator.
     * The UrlValidator handles:
     * - Fast-fail checks (empty, spaces)
     * - Prefix normalization (www., http://, https://)
     * - URL validation (Android's WEB_URL pattern + fallback)
     * - Scheme normalization (ensuring https://)
     *
     * WHY DELEGATE TO UrlValidator:
     * - Single Responsibility: UrlValidator handles all URL logic
     * - Testability: URL validation can be tested independently
     * - Maintainability: Changes to URL logic go in one place
     * - Reusability: Other components can use UrlValidator
     *
     * @param query The search query to check
     * @return UrlSearchResult if query looks like a URL, null otherwise
     */
    private fun detectUrl(query: String): UrlSearchResult? {
        // Delegate URL validation to the centralized UrlValidator utility
        // This keeps URL detection logic in one maintainable place
        val validationResult = UrlValidator.validateUrl(query) ?: return null
        
        // Resolve which app should handle this URL
        // This queries Android's PackageManager to find apps that can open the URL
        val handlerApp = urlHandlerResolver.resolveUrlHandler(validationResult.url)
        
        return UrlSearchResult(
            url = validationResult.url,
            displayUrl = validationResult.displayUrl,
            handlerApp = handlerApp,
            browserFallback = true
        )
    }

    // ========================================================================
    // HELPER FUNCTIONS
    // ========================================================================

    /**
     * Build hidden-state UI snapshot.
     *
     * We intentionally clear results when hidden to keep semantics consistent
     * with previous behavior and avoid showing stale data on next open.
     */
    private fun buildHiddenUiState(inputs: SearchInputs): SearchUiState {
        return SearchUiState(
            query = "",
            isSearchVisible = false,
            results = emptyList(),
            activeProviderConfig = null,
            isLoading = false,
            recentApps = inputs.recentApps,
            installedApps = inputs.installedApps,
            hasContactsPermission = inputs.hasContactsPermission,
            hasFilesPermission = inputs.hasFilesPermission
        )
    }

    /**
     * Build loading-state UI snapshot.
     */
    private fun buildLoadingUiState(inputs: SearchInputs): SearchUiState {
        return SearchUiState(
            query = inputs.query,
            isSearchVisible = true,
            results = emptyList(),
            activeProviderConfig = null,
            isLoading = true,
            recentApps = inputs.recentApps,
            installedApps = inputs.installedApps,
            hasContactsPermission = inputs.hasContactsPermission,
            hasFilesPermission = inputs.hasFilesPermission
        )
    }

    /**
     * Build ready-state UI snapshot from computation output.
     */
    private fun buildReadyUiState(
        inputs: SearchInputs,
        computation: SearchComputation
    ): SearchUiState {
        return SearchUiState(
            query = inputs.query,
            isSearchVisible = true,
            results = computation.results,
            activeProviderConfig = computation.activeProviderConfig,
            isLoading = false,
            recentApps = inputs.recentApps,
            installedApps = inputs.installedApps,
            hasContactsPermission = inputs.hasContactsPermission,
            hasFilesPermission = inputs.hasFilesPermission
        )
    }
}
