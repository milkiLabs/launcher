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

import android.util.Patterns
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
            is UrlSearchResult -> SearchAction.OpenUrl(result.url)
            is ContactSearchResult -> {
                val phone = result.contact.phoneNumbers.firstOrNull()
                if (phone != null) {
                    SearchAction.CallContact(result.contact, phone)
                } else {
                    SearchAction.CloseSearch
                }
            }
            is FileDocumentSearchResult -> {
                // Check if this is a placeholder/hint result (id == -1)
                if (result.file.id == -1L) {
                    SearchAction.CloseSearch
                } else {
                    SearchAction.OpenFile(result.file)
                }
            }
            is PermissionRequestResult -> {
                // Determine which permission is being requested based on the prefix
                when (result.providerPrefix) {
                    "c" -> SearchAction.RequestContactsPermission
                    "f" -> SearchAction.RequestFilesPermission
                    else -> SearchAction.CloseSearch
                }
            }
        }

        emitAction(action)

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
            // No provider prefix detected
            val state = _uiState.value
            
            // Check if the query looks like a URL
            val urlResult = detectUrl(parsed.query)
            
            // Filter apps (limited to 8 results for grid display)
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
            // URL result comes first for easy access
            val results = if (urlResult != null) {
                listOf(urlResult) + appResults
            } else {
                appResults
            }

            updateState { copy(results = results) }
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
 * NORMALIZATION:
 * - If no scheme is provided, "https://" is prepended
 * - The display URL shows the original input for clarity
 *
 * @param query The search query to check
 * @return UrlSearchResult if query looks like a URL, null otherwise
 */
private fun detectUrl(query: String): UrlSearchResult? {
    val trimmed = query.trim()
    
    // Empty query is not a URL
    if (trimmed.isEmpty()) return null
    
    // Check for full URL with scheme (http:// or https://)
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        // Use Android's built-in URL pattern matcher
        if (Patterns.WEB_URL.matcher(trimmed).matches()) {
            return UrlSearchResult(
                url = trimmed,
                displayUrl = trimmed
            )
        }
    }
    
    // Check for domain-like patterns without scheme
    // Common TLDs that users might type
    val commonTlds = listOf(
        ".com", ".org", ".net", ".io", ".co", ".edu", ".gov", 
        ".dev", ".app", ".me", ".tech", ".xyz", ".info", ".biz",
        ".online", ".site", ".store", ".blog"
    )
    
    // Check if query contains a common TLD
    val hasTld = commonTlds.any { tld -> 
        trimmed.contains(tld, ignoreCase = true) 
    }
    
    if (hasTld) {
        // Validate with Android's URL pattern
        // Prepend https:// for validation
        val urlWithScheme = if (!trimmed.startsWith("http")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        
        if (Patterns.WEB_URL.matcher(urlWithScheme).matches()) {
            return UrlSearchResult(
                url = urlWithScheme,
                displayUrl = trimmed
            )
        }
    }
    
    return null
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
