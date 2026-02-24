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
 * │  Functions ◄──────────── UI calls these on user actions    │
 * └─────────────────────────────────────────────────────────────┘
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

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.ContactsRepository
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerResolver
import com.milki.launcher.domain.search.parseSearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the search feature.
 *
 * This ViewModel is the single source of truth for search state.
 * It coordinates between:
 * - UI input (query changes)
 * - Search providers (app, web, contacts, YouTube)
 * - Data sources (installed apps, recent apps)
 *
 * ACTION HANDLING:
 * User actions (launching apps, making calls, etc.) are handled by
 * ActionExecutor via LocalSearchActionHandler. This separation keeps
 * the ViewModel focused on state management.
 *
 * @property appRepository Repository for app data
 * @property contactsRepository Repository for contacts data (for recent contacts)
 * @property providerRegistry Registry of search providers
 * @property filterAppsUseCase Use case for filtering apps
 * @property urlHandlerResolver Resolver for URL handler apps
 */
class SearchViewModel(
    private val appRepository: AppRepository,
    private val contactsRepository: ContactsRepository,
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
     * Tracks the current search coroutine job.
     * 
     * RACE CONDITION FIX:
     * When the user types quickly (e.g., "a" then "ab"), multiple search
     * coroutines could be running simultaneously. If the search for "a"
     * takes longer than "ab", the "a" results would overwrite "ab" results.
     * 
     * By storing the job and cancelling it before starting a new search,
     * we ensure only the most recent query's results are displayed.
     */
    private var searchJob: Job? = null

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
     * Also clears the query and cancels any ongoing search.
     */
    fun hideSearch() {
        // Cancel any ongoing search to prevent stale results
        searchJob?.cancel()
        searchJob = null
        
        updateState {
            copy(
                isSearchVisible = false,
                query = "",
                results = emptyList(),
                activeProviderConfig = null,
                isLoading = false
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
     * Update files permission status.
     * Called from Activity when permission state changes.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateFilesPermission(hasPermission: Boolean) {
        updateState { copy(hasFilesPermission = hasPermission) }

        // Re-run search if we're in files mode
        val currentState = _uiState.value
        if (currentState.activeProviderConfig?.prefix == "f") {
            performSearch(currentState.query)
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
     * We call performSearch("") to trigger FilterAppsUseCase which
     * returns recentApps when query is blank.
     */
    fun clearQuery() {
        updateState {
            copy(query = "", activeProviderConfig = null)
        }
        performSearch("")
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
     * This function runs heavy operations (Regex matching, app filtering)
     * on Dispatchers.Default to prevent UI thread blocking.
     *
     * @param query The search query
     */
    private fun performSearch(query: String) {
        // Cancel any previous search before starting a new one
        searchJob?.cancel()

        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val parsed = parseSearchQuery(query, providerRegistry)

            withContext(Dispatchers.Main) {
                updateState {
                    copy(activeProviderConfig = parsed.config)
                }
            }

            if (parsed.provider != null) {
                // Provider search
                withContext(Dispatchers.Main) {
                    updateState { copy(isLoading = true) }
                }

                try {
                    val results = parsed.provider.search(parsed.query)
                    
                    withContext(Dispatchers.Main) {
                        updateState {
                            copy(results = results, isLoading = false)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateState { copy(isLoading = false, results = emptyList()) }
                    }
                }
            } else {
                // No provider prefix detected
                val state = _uiState.value
                
                // Check if the query looks like a URL
                val urlResult = detectUrl(parsed.query)
                
                // Filter apps
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
                val results = if (urlResult != null) {
                    listOf(urlResult) + appResults
                } else {
                    appResults
                }

                withContext(Dispatchers.Main) {
                    updateState { copy(results = results) }
                }
            }
        }
    }

    // ========================================================================
    // URL DETECTION
    // ========================================================================

    /**
     * Detect if the query looks like a URL and create a UrlSearchResult.
     *
     * URL PATTERNS RECOGNIZED:
     * 1. Full URLs: "https://example.com", "http://example.com/path"
     * 2. Domain-only: "example.com", "sub.domain.org", "github.com/user/repo"
     * 3. Common TLDs: .com, .org, .net, .io, .co, .edu, .gov, .dev, .app
     *
     * @param query The search query to check
     * @return UrlSearchResult if query looks like a URL, null otherwise
     */
    private fun detectUrl(query: String): UrlSearchResult? {
        val trimmed = query.trim()
        
        if (trimmed.isEmpty()) return null
        
        var finalUrl: String? = null
        var displayUrl = trimmed
        
        // Check for full URL with scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (Patterns.WEB_URL.matcher(trimmed).matches()) {
                finalUrl = trimmed
            }
        }
        
        // Check for domain-like patterns without scheme
        if (finalUrl == null) {
            val commonTlds = listOf(
                ".com", ".org", ".net", ".io", ".co", ".edu", ".gov", 
                ".dev", ".app", ".me", ".tech", ".xyz", ".info", ".biz",
                ".online", ".site", ".store", ".blog"
            )
            
            val hasTld = commonTlds.any { tld -> 
                trimmed.contains(tld, ignoreCase = true) 
            }
            
            if (hasTld) {
                val urlWithScheme = if (!trimmed.startsWith("http")) {
                    "https://$trimmed"
                } else {
                    trimmed
                }
                
                if (Patterns.WEB_URL.matcher(urlWithScheme).matches()) {
                    finalUrl = urlWithScheme
                }
            }
        }
        
        return finalUrl?.let { url ->
            val handlerApp = urlHandlerResolver.resolveUrlHandler(url)
            
            UrlSearchResult(
                url = url,
                displayUrl = displayUrl,
                handlerApp = handlerApp,
                browserFallback = true
            )
        }
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
