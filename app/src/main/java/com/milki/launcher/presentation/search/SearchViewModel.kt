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
     * Internal flow for search queries.
     * 
     * REACTIVE SEARCH PATTERN:
     * Instead of manually managing coroutine jobs, we use a Flow-based approach.
     * User input (queries) flows through this MutableStateFlow, and we react
     * to each query using mapLatest - which automatically cancels any previous
     * search when a new query arrives.
     * 
     * This is the standard modern Android pattern for "search-as-you-type" because:
     * - No manual job cancellation needed (mapLatest handles it)
     * - Can easily add debounce() to wait for user to stop typing
     * - Declarative and idiomatic Kotlin Flow code
     * - Race conditions are handled automatically
     */
    private val searchQuery = MutableStateFlow("")

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        // Load installed apps
        loadInstalledApps()

        // Observe recent apps
        observeRecentApps()

        // Set up reactive search pipeline
        observeSearchQueries()

        // Observe settings and update provider registry when prefix configs change
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

    /**
     * Set up the reactive search pipeline.
     * 
     * This replaces the manual searchJob cancellation approach with a declarative
     * Flow-based pipeline. The key operator is mapLatest, which automatically
     * cancels any ongoing search when a new query arrives.
     * 
     * PIPELINE STRUCTURE:
     * 1. searchQuery (MutableStateFlow) - receives queries from onQueryChange()
     * 2. onEach - sets loading state before search starts
     * 3. mapLatest - executes search, cancels previous if new query arrives
     * 4. onEach - updates results and clears loading state
     * 
     * WHY mapLatest:
     * - When user types "a" then "ab" quickly, the search for "a" might still
     *   be running when "ab" arrives. mapLatest automatically cancels the "a"
     *   search and starts "ab", preventing stale results.
     * - No manual Job tracking or cancellation needed.
     */
    private fun observeSearchQueries() {
        searchQuery
            .onEach { updateState { copy(isLoading = true) } }
            .mapLatest { query -> executeSearchLogic(query) }
            .onEach { results -> updateState { copy(results = results, isLoading = false) } }
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
                }
        }
    }

    // ========================================================================
    // PUBLIC API - Called from UI
    // ========================================================================

    /**
     * Show the search dialog.
     * Also triggers an initial search to show recent apps.
     * 
     * IMPORTANT: We cannot just set searchQuery.value = "" because:
     * 1. searchQuery is initialized to "" in the MutableStateFlow
     * 2. Setting a StateFlow to its current value does NOT trigger emission
     * 3. The initial "" was processed before recentApps was loaded
     * 
     * SOLUTION: Directly execute search for the current query.
     * This ensures recentApps (now loaded) are included in results.
     */
    fun showSearch() {
        updateState { copy(isSearchVisible = true) }
        // Force a search refresh to show recent apps.
        // We directly execute the search logic instead of relying on
        // the reactive pipeline because the query might already be "".
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            val results = executeSearchLogic(searchQuery.value)
            updateState { copy(results = results, isLoading = false) }
        }
    }

    /**
     * Hide the search dialog.
     * Also clears the query and results.
     * 
     * Note: No need to cancel any search job - the reactive pipeline
     * handles that automatically through mapLatest.
     */
    fun hideSearch() {
        updateState {
            copy(
                isSearchVisible = false,
                query = "",
                results = emptyList(),
                activeProviderConfig = null,
                isLoading = false
            )
        }
        searchQuery.value = ""
    }

    /**
     * Update the search query.
     * Triggers a new search automatically through the reactive pipeline.
     *
     * @param newQuery The new query text
     */
    fun onQueryChange(newQuery: String) {
        updateState { copy(query = newQuery) }
        // Emit to the search flow - this triggers the reactive pipeline
        // which will cancel any ongoing search and start a new one
        searchQuery.value = newQuery
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
        // Emit to searchQuery flow to trigger the reactive pipeline
        val currentState = _uiState.value
        if (currentState.activeProviderConfig?.prefix == "c") {
            searchQuery.value = currentState.query
        }
    }

    /**
     * Update files permission status.
     * Called from Activity when permission state changes.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateFilesPermission(hasPermission: Boolean) {
        updateState { copy(hasFilesPermission = hasPermission) }

        // Re-run search if we're in files mode
        // Emit to searchQuery flow to trigger the reactive pipeline
        val currentState = _uiState.value
        if (currentState.activeProviderConfig?.prefix == "f") {
            searchQuery.value = currentState.query
        }
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
        updateState {
            copy(query = "", activeProviderConfig = null)
        }
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
     * @return List of search results
     */
    private suspend fun executeSearchLogic(query: String): List<SearchResult> {
        val parsed = parseSearchQuery(query, providerRegistry)

        // Update the active provider config in state
        // This needs to happen separately since we're returning results
        updateState { copy(activeProviderConfig = parsed.config) }

        if (parsed.provider != null) {
            // Provider search - delegate to the provider
            return try {
                parsed.provider.search(parsed.query)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // No provider prefix detected - search apps and check for URLs
        val state = _uiState.value
        
        // Check if the query looks like a URL
        val urlResult = detectUrl(parsed.query)
        
        // Filter apps using the use case
        val filteredApps = filterAppsUseCase(
            query = parsed.query,
            installedApps = state.installedApps,
            recentApps = state.recentApps
        )

        val limitedApps = filteredApps.take(8)

        val appResults = limitedApps.map { app ->
            AppSearchResult(appInfo = app)
        }

        // Combine URL result with app results
        return if (urlResult != null) {
            listOf(urlResult) + appResults
        } else {
            appResults
        }
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
     * Update state with a transformation.
     * Provides a cleaner API for state updates.
     */
    private inline fun updateState(transform: SearchUiState.() -> SearchUiState) {
        _uiState.update { it.transform() }
    }
}
