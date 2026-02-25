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
     * 
     * RACE CONDITION FIX:
     * When the search dialog opens, showSearch() triggers a search with empty query.
     * However, recentApps might not be loaded yet from DataStore. Since StateFlow
     * doesn't emit duplicate values (searchQuery.value = "" when already "" does nothing),
     * we directly compute and update results when recentApps are loaded AND the search
     * is visible with an empty query.
     */
    private fun observeRecentApps() {
        viewModelScope.launch {
            appRepository.getRecentApps()
                .collect { recentApps ->
                    updateState { copy(recentApps = recentApps) }
                    
                    // If search is visible with empty query, directly update results
                    // We can't rely on searchQuery.value = "" because StateFlow
                    // doesn't emit duplicate values
                    val currentState = _uiState.value
                    if (currentState.isSearchVisible && currentState.query.isBlank()) {
                        val appResults = recentApps.map { app -> AppSearchResult(appInfo = app) }
                        updateState { copy(results = appResults) }
                    }
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

    // ========================================================================
    // PUBLIC API - Called from UI
    // ========================================================================

    /**
     * Show the search dialog.
     * Also triggers an initial search to show recent apps.
     */
    fun showSearch() {
        updateState { copy(isSearchVisible = true) }
        // Trigger an empty search to show recent apps.
        // Without this, the results list would be empty until
        // the user types something.
        searchQuery.value = ""
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
     * URL DETECTION STRATEGY:
     * This function uses a multi-stage approach to detect URLs:
     *
     * 1. FAST-FAIL: Empty queries or queries with spaces are immediately rejected.
     *    Spaces indicate the user is searching for apps, not typing a URL.
     *
     * 2. SCHEME PREFIX CHECK: Queries starting with http://, https://, or www.
     *    are treated as URL candidates and validated.
     *
     * 3. ANDROID WEB_URL PATTERN: Uses the built-in Patterns.WEB_URL matcher
     *    which handles most common URL formats.
     *
     * 4. FALLBACK REGEX: For newer/regional TLDs (like .ai, .eg, .io) that
     *    older Android versions might not recognize, we use a generic pattern
     *    that matches: domain.tld or domain.tld/path
     *
     * WHY NO HARDCODED TLD LIST:
     * - New TLDs are constantly being added (there are now 1500+ TLDs)
     * - Hardcoded lists become outdated quickly
     * - The fallback regex handles any 2+ letter TLD
     *
     * @param query The search query to check
     * @return UrlSearchResult if query looks like a URL, null otherwise
     */
    private fun detectUrl(query: String): UrlSearchResult? {
        val trimmed = query.trim()
        
        // FAST-FAIL: Empty or space-containing queries aren't URLs
        // Users searching for apps will type single words without spaces
        // like "youtube" or "maps". URLs typed by users don't have spaces.
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            return null
        }
        
        var finalUrl: String? = null
        val displayUrl = trimmed
        
        // STAGE 1: Check for explicit URL prefixes
        // These are strong indicators the user intends to visit a URL
        val hasSchemePrefix = trimmed.startsWith("http://") || 
                              trimmed.startsWith("https://") || 
                              trimmed.startsWith("www.")
        
        if (hasSchemePrefix) {
            // Normalize www. to https://www.
            val urlToCheck = if (trimmed.startsWith("www.")) {
                "https://$trimmed"
            } else {
                trimmed
            }
            
            if (Patterns.WEB_URL.matcher(urlToCheck).matches()) {
                finalUrl = urlToCheck
            }
        }
        
        // STAGE 2: Try Android's built-in WEB_URL pattern
        // This handles most standard URL formats
        if (finalUrl == null && Patterns.WEB_URL.matcher(trimmed).matches()) {
            finalUrl = trimmed
        }
        
        // STAGE 3: Fallback regex for newer/regional TLDs
        // Older Android versions may not recognize newer TLDs like .ai, .eg, .shop
        // This pattern matches: domain.tld or domain.tld/path with any 2+ letter TLD
        if (finalUrl == null) {
            // Regex explanation:
            // ^                          - Start of string
            // [a-zA-Z0-9]                - First char must be alphanumeric
            // [a-zA-Z0-9-]*              - Domain can have alphanumeric and hyphens
            // \.                         - Literal dot before TLD
            // [a-zA-Z]{2,}               - TLD must be at least 2 letters
            // (?:/.*)?                   - Optional path starting with /
            // $                          - End of string
            val fallbackUrlPattern = Regex(
                "^[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,}(?:/.*)?$"
            )
            
            if (fallbackUrlPattern.matches(trimmed)) {
                finalUrl = trimmed
            }
        }
        
        // Return null if no URL pattern matched
        if (finalUrl == null) return null
        
        // Ensure the URL has a scheme for Intent.ACTION_VIEW
        // Without https://, the intent won't open a browser
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = "https://$finalUrl"
        }
        
        // Resolve which app should handle this URL
        val handlerApp = urlHandlerResolver.resolveUrlHandler(finalUrl)
        
        return UrlSearchResult(
            url = finalUrl,
            displayUrl = displayUrl,
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
