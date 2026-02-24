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
 * - UI input (query changes, result clicks)
 * - Search providers (app, web, contacts, YouTube)
 * - Data sources (installed apps, recent apps, contacts)
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
     * Private mutable shared flow for one-time actions.
     * Actions are consumed and not replayed.
     */
    private val _action = MutableSharedFlow<SearchAction>()

    /**
     * Public shared flow for UI to observe actions.
     * Use collect() to receive actions.
     */
    val action: SharedFlow<SearchAction> = _action.asSharedFlow()

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
     * 
     * Example timeline without this fix:
     * - User types "a" -> searchJob1 starts
     * - User types "ab" -> searchJob2 starts
     * - searchJob1 completes -> shows results for "a" (WRONG!)
     * - searchJob2 completes -> shows results for "ab" (too late, user confused)
     * 
     * With this fix:
     * - User types "a" -> searchJob1 starts
     * - User types "ab" -> searchJob1.cancel(), searchJob2 starts
     * - searchJob2 completes -> shows results for "ab" (CORRECT!)
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
            is UrlSearchResult -> {
                /**
                 * Handle URL result click based on whether there's a handler app.
                 *
                 * If a handler app exists (like YouTube for youtube.com URLs):
                 * - Use OpenUrlWithApp to open in that specific app
                 *
                 * If no handler app exists (browser fallback):
                 * - Use OpenUrl to open in the default browser
                 */
                result.handlerApp?.let { handler ->
                    SearchAction.OpenUrlWithApp(result.url, handler)
                } ?: SearchAction.OpenUrl(result.url)
            }
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
     * Update call permission status.
     * Called from Activity when permission state changes.
     *
     * @param hasPermission Whether CALL_PHONE permission is granted
     */
    fun updateCallPermission(hasPermission: Boolean) {
        updateState { copy(hasCallPermission = hasPermission) }
    }

    /**
     * Handle dial icon click on a contact result.
     *
     * FLOW:
     * 1. Check if CALL_PHONE permission is granted
     * 2. If granted, emit CallContactDirect action immediately
     * 3. If not granted, store pending call and emit RequestCallPermission action
     *
     * The pending call will be executed in onCallPermissionResult() when
     * permission is granted.
     *
     * @param contact The contact to call
     * @param phoneNumber The phone number to call
     */
    fun onDialClick(contact: Contact, phoneNumber: String) {
        val currentState = _uiState.value

        if (currentState.hasCallPermission) {
            // Permission already granted - make direct call
            emitAction(SearchAction.CallContactDirect(contact, phoneNumber))
            hideSearch()
        } else {
            // Permission not granted - store pending call and request permission
            updateState {
                copy(pendingDirectCall = PendingDirectCall(contact, phoneNumber))
            }
            emitAction(SearchAction.RequestCallPermission)
        }
    }

    /**
     * Handle call permission result.
     * Called from PermissionHandler when the user responds to the permission request.
     *
     * FLOW:
     * 1. Update the permission state
     * 2. If granted and there's a pending call, execute it
     * 3. Clear the pending call regardless of result
     *
     * @param isGranted Whether CALL_PHONE permission was granted
     */
    fun onCallPermissionResult(isGranted: Boolean) {
        updateState { copy(hasCallPermission = isGranted) }

        val pendingCall = _uiState.value.pendingDirectCall
        if (isGranted && pendingCall != null) {
            // Permission granted and there's a pending call - execute it
            emitAction(SearchAction.CallContactDirect(pendingCall.contact, pendingCall.phoneNumber))
            // Clear pending call and close search
            updateState { copy(pendingDirectCall = null) }
            hideSearch()
        } else {
            // Permission denied or no pending call - just clear pending call
            updateState { copy(pendingDirectCall = null) }
        }
    }

    /**
     * Save a phone number to recent contacts.
     * Called after making a call (either direct or via dialer).
     *
     * @param phoneNumber The phone number to save
     */
    fun saveRecentContact(phoneNumber: String) {
        viewModelScope.launch {
            contactsRepository.saveRecentContact(phoneNumber)
        }
    }

    /**
     * Save an app to recent apps.
     * Called after launching an app.
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
 * - Dispatchers.Main: UI updates only
 * - Dispatchers.Default: CPU-intensive work (pattern matching, filtering)
 *
 * RACE CONDITION HANDLING:
 * Each call cancels the previous searchJob before starting a new one.
 * This ensures that only the results for the most recent query are shown.
 *
 * @param query The search query
 */
private fun performSearch(query: String) {
    /**
     * RACE CONDITION FIX:
     * Cancel any previous search before starting a new one.
     * This prevents outdated results from overwriting newer ones.
     */
    searchJob?.cancel()

    /**
     * Store the new job so we can cancel it if the user types again.
     * Using Dispatchers.Default for CPU-intensive work:
     * - Regex pattern matching (Patterns.WEB_URL.matcher)
     * - App filtering (filterAppsUseCase)
     * - List operations and iterations
     * 
     * This keeps the Main thread free for smooth UI rendering at 60/120fps.
     */
    searchJob = viewModelScope.launch(Dispatchers.Default) {
        val parsed = parseSearchQuery(query, providerRegistry)

        /**
         * Switch to Main thread for UI state updates.
         * StateFlow updates must happen on the Main thread.
         */
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
                // Provider search runs on Default dispatcher (already here)
                val results = parsed.provider.search(parsed.query)
                
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(results = results, isLoading = false)
                    }
                }
            } catch (e: Exception) {
                // Handle error (could emit error action)
                withContext(Dispatchers.Main) {
                    updateState { copy(isLoading = false, results = emptyList()) }
                }
            }
        } else {
            // No provider prefix detected
            val state = _uiState.value
            
            // Check if the query looks like a URL (CPU-intensive regex work)
            val urlResult = detectUrl(parsed.query)
            
            // Filter apps (CPU-intensive work)
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

            // Update UI on Main thread
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
 * NORMALIZATION:
 * - If no scheme is provided, "https://" is prepended
 * - The display URL shows the original input for clarity
 *
 * URL HANDLER RESOLUTION:
 * After detecting a URL, we use UrlHandlerResolver to determine which
 * installed app can handle it. For example:
 * - youtube.com URLs → YouTube app (if installed)
 * - twitter.com URLs → Twitter/X app (if installed)
 * - maps.google.com → Google Maps (if installed)
 * - generic URLs → Browser (always available)
 *
 * @param query The search query to check
 * @return UrlSearchResult if query looks like a URL, null otherwise
 */
private fun detectUrl(query: String): UrlSearchResult? {
    val trimmed = query.trim()
    
    // Empty query is not a URL
    if (trimmed.isEmpty()) return null
    
    // Variable to hold the final URL
    var finalUrl: String? = null
    var displayUrl = trimmed
    
    // Check for full URL with scheme (http:// or https://)
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        // Use Android's built-in URL pattern matcher
        if (Patterns.WEB_URL.matcher(trimmed).matches()) {
            finalUrl = trimmed
        }
    }
    
    // Check for domain-like patterns without scheme
    if (finalUrl == null) {
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
            // Prepend https:// for validation and use
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
    
    // If we found a valid URL, resolve the handler app
    return finalUrl?.let { url ->
        /**
         * Use UrlHandlerResolver to find which app can handle this URL.
         * This runs on the Default dispatcher (background thread).
         *
         * The resolver checks:
         * 1. Is there a specific app for this domain (e.g., YouTube for youtube.com)
         * 2. Is there a user-set default for this type of URL
         * 3. Fall back to browser if no specific app
         */
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
