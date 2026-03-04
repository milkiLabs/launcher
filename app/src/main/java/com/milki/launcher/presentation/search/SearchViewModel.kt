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
 *     _query, _isSearchVisible, _hasContactsPermission, _hasFilesPermission
 *
 *   LAYER 2 — Background data (loaded async, changes infrequently):
 *     _installedApps, _recentApps (combined into _backgroundState)
 *
 *   LAYER 3 — Search pipeline output (async, may be slow):
 *     _searchOutput (results, provider config, loading flag)
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
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
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
     * Groups background data that changes infrequently (installed apps list,
     * recent apps list, permission flags).
     *
     * WHY THIS EXISTS:
     * These four values change independently but are all needed by the search
     * pipeline. Grouping them into one data class lets us:
     * - Use a single combine() call instead of chaining multiple combines
     * - Avoid the 5-argument combine() limit problem entirely
     * - Make the pipeline inputs self-documenting
     */
    private data class BackgroundState(
        val installedApps: List<AppInfo> = emptyList(),
        val recentApps: List<AppInfo> = emptyList(),
        val hasContactsPermission: Boolean = false,
        val hasFilesPermission: Boolean = false
    )

    /**
     * Output from the search pipeline.
     *
     * This is what the async search produces. Crucially, it does NOT contain
     * the query text — that's managed separately in _query so it can never
     * be overwritten by a slow search completing.
     *
     * @property results The search results to display
     * @property activeProviderConfig Which provider mode is active (null = app search)
     * @property isLoading Whether a search is currently running
     */
    private data class SearchOutput(
        val results: List<SearchResult> = emptyList(),
        val activeProviderConfig: SearchProviderConfig? = null,
        val isLoading: Boolean = false
    )

    // ========================================================================
    // LAYER 1: INPUT STATE (user-driven, updated immediately)
    // ========================================================================

    /**
     * The raw query text. Updated immediately on every keystroke in onQueryChange().
     * This is the source of truth for what the TextField displays.
     */
    private val _query = MutableStateFlow("")

    /**
     * Whether the search dialog is currently open.
     * Updated by showSearch() and hideSearch().
     */
    private val _isSearchVisible = MutableStateFlow(false)

    /**
     * Whether the READ_CONTACTS permission is granted.
     * Updated from Activity when permission state changes.
     * Feeds into the search pipeline so contact searches re-run when granted.
     */
    private val _hasContactsPermission = MutableStateFlow(false)

    /**
     * Whether file storage permission is granted.
     * Updated from Activity when permission state changes.
     * Feeds into the search pipeline so file searches re-run when granted.
     */
    private val _hasFilesPermission = MutableStateFlow(false)

    // ========================================================================
    // LAYER 2: BACKGROUND DATA (system-driven, changes infrequently)
    // ========================================================================

    /**
     * All installed apps on the device.
     * Loaded once at startup. Used for app search filtering.
     */
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Recently used apps. Observed from the repository as a Flow.
     * Automatically updates when the user launches a new app.
     */
    private val _recentApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Combined background state as a single StateFlow.
     *
     * Using stateIn(Eagerly) so this is always active and hot — it doesn't
     * wait for a collector. This is correct because both the search pipeline
     * and the final uiState combine depend on it.
     */
    private val _backgroundState: StateFlow<BackgroundState> = combine(
        _installedApps,
        _recentApps,
        _hasContactsPermission,
        _hasFilesPermission
    ) { installed, recent, contactsPerm, filesPerm ->
        BackgroundState(installed, recent, contactsPerm, filesPerm)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackgroundState())

    // ========================================================================
    // LAYER 3: SEARCH PIPELINE OUTPUT (async, may be slow)
    // ========================================================================

    /**
     * The latest output from the search pipeline.
     *
     * Contains results, active provider config, and loading state.
     * Updated asynchronously when searches complete.
     *
     * IMPORTANT: This does NOT contain the query text. The query flows into
     * the pipeline as input but is never written back. This prevents slow
     * searches from overwriting the TextField with stale text.
     */
    private val _searchOutput = MutableStateFlow(SearchOutput())

    /**
     * Prefix configuration values from settings.
     *
     * WHY THIS IS A PIPELINE INPUT:
     * When the user changes prefix settings, the same query text might now
     * parse differently (e.g., "x " might become a new provider). We include
     * this in the pipeline's combine() so it naturally re-runs when config changes.
     * This replaces the old prefixConfigVersion counter hack.
     */
    private val _prefixConfigs = MutableStateFlow<ProviderPrefixConfiguration>(emptyMap())

    // ========================================================================
    // LAYER 4: FINAL UI STATE (combines all layers for Compose)
    // ========================================================================

    /**
     * The final, complete UI state that Compose observes.
     *
     * This is produced by combining:
     * - _query (always the live, latest text — never stale)
     * - _isSearchVisible (dialog open/closed)
     * - _searchOutput (results, provider config, loading from the async pipeline)
     *
     * WHY QUERY IS ALWAYS CORRECT:
     * The query comes directly from _query, which is updated synchronously
     * in onQueryChange(). The search pipeline writes to _searchOutput but
     * NEVER touches _query. So even if a slow file search completes with
     * results from an old query, the TextField value stays current.
     *
     * SharingStarted.Eagerly because the Activity collects this in setContent{},
     * and we want the state to be computed immediately (not lazily on first
     * collector).
     */
    val uiState: StateFlow<SearchUiState> = combine(
        _query,
        _isSearchVisible,
        _searchOutput
    ) { query, visible, output ->
        if (!visible) {
            // When the dialog is hidden, return a minimal state.
            // We clear results to avoid showing stale data on next open.
            SearchUiState(isSearchVisible = false)
        } else {
            SearchUiState(
                query = query,
                isSearchVisible = true,
                results = output.results,
                activeProviderConfig = output.activeProviderConfig,
                isLoading = output.isLoading
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        startSearchPipeline()
        loadInstalledApps()
        observeRecentApps()
        observePrefixConfiguration()
    }

    // ========================================================================
    // DATA LOADING
    // ========================================================================

    /**
     * Load all installed apps from the repository.
     * This runs once at startup and populates _installedApps.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = appRepository.getInstalledApps()
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
                    _recentApps.value = updatedRecentApps
                }
        }
    }

    // ========================================================================
    // SEARCH PIPELINE
    // ========================================================================

    /**
     * The core search pipeline. Produces SearchOutput from input state changes.
     *
     * HOW IT WORKS:
     * 1. combine() merges four input signals: query, visibility, background state,
     *    and prefix config. When ANY of these change, a new search is triggered.
     *
     * 2. mapLatest {} cancels the previous search coroutine when new input arrives.
     *    This is the "latest-only" behavior — if the user types faster than the
     *    search can complete, only the most recent query is processed.
     *
     * 3. Inside mapLatest:
     *    a) We immediately parse the query to determine the active provider. This
     *       is a fast, synchronous operation, so the mode indicator updates instantly.
     *    b) We set _searchOutput with isLoading=true and the new provider config,
     *       KEEPING the previous results visible. This avoids the empty-results
     *       flicker that the old architecture had.
     *    c) We run the actual search (which may be slow for files/contacts).
     *    d) We return the final SearchOutput with results and isLoading=false.
     *
     * 4. onEach {} writes the pipeline's output to _searchOutput.
     *
     * WHY mapLatest INSTEAD OF flatMapLatest:
     * The old code used flatMapLatest with a flow { emit(loading); emit(ready) }
     * pattern. This required baking the query into both emissions, which caused
     * the stale-query race. mapLatest is simpler — one coroutine per input change,
     * one output value. Loading state is handled via a side-effect inside the
     * coroutine, not via multiple emissions.
     *
     * WHY PREVIOUS RESULTS ARE KEPT DURING LOADING:
     * The old architecture set results=emptyList() during loading, causing a flicker
     * on every keystroke (results vanish → reappear). Now we do:
     *   _searchOutput.update { it.copy(isLoading = true, activeProviderConfig = newConfig) }
     * This keeps the old results visible while the new search runs. The user sees
     * the loading bar appear, but results stay on screen until replaced.
     */
    private fun startSearchPipeline() {
        combine(
            _query,
            _isSearchVisible,
            _backgroundState,
            _prefixConfigs
        ) { query, visible, bg, _ ->
            // Package all inputs into a simple Triple.
            // _prefixConfigs is included so the pipeline re-runs when config changes,
            // but we don't read it directly — the providerRegistry is already updated
            // by observePrefixConfiguration().
            Triple(query, visible, bg)
        }
            .mapLatest { (query, visible, bg) ->
                if (!visible) {
                    // Dialog is hidden — clear everything.
                    // This ensures stale results don't flash when the dialog reopens.
                    SearchOutput()
                } else {
                    // STEP 1: Parse the query to determine the active provider.
                    // This is fast (just string prefix checking), so the mode indicator
                    // (color bar, icon) updates immediately even for slow providers.
                    val parsed = parseSearchQuery(query, providerRegistry)

                    // STEP 2: Show loading state immediately, but KEEP previous results
                    // visible. This avoids the empty-results flicker.
                    _searchOutput.update { current ->
                        current.copy(
                            isLoading = true,
                            activeProviderConfig = parsed.config
                        )
                    }

                    // STEP 3: Run the actual search. This may be slow for files/contacts
                    // (MediaStore/ContentResolver queries). mapLatest will cancel this
                    // coroutine if the user types another character before it completes.
                    val results = executeSearch(
                        parsed = parsed,
                        installedApps = bg.installedApps,
                        recentApps = bg.recentApps
                    )

                    // STEP 4: Return the final output with results and loading=false.
                    SearchOutput(
                        results = results,
                        activeProviderConfig = parsed.config,
                        isLoading = false
                    )
                }
            }
            .onEach { output ->
                _searchOutput.value = output
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observe settings for prefix configuration changes.
     *
     * When the user changes their prefix settings, the SearchProviderRegistry
     * needs to be updated to reflect the new prefixes. We also update
     * _prefixConfigs so the search pipeline naturally re-runs.
     *
     * WHY THIS REPLACES prefixConfigVersion:
     * The old design used an artificial version counter to force pipeline
     * recomputation. Now we feed the actual prefix config list into the
     * pipeline's combine(). When it changes, the pipeline naturally re-runs.
     * This is cleaner because:
     * - It uses real data instead of an artificial counter
     * - distinctUntilChanged() on the config list dedups naturally
     * - It's self-documenting (you can see WHAT triggered the re-run)
     */
    private fun observePrefixConfiguration() {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.prefixConfigurations }
                .distinctUntilChanged()
                .collect { prefixConfigurations ->
                    // Update the registry with new prefix configurations.
                    // This rebuilds the prefix-to-provider mappings.
                    providerRegistry.updatePrefixConfigurations(prefixConfigurations)

                    // Update the flow so the search pipeline re-runs.
                    // The pipeline's combine() includes _prefixConfigs, so this
                    // triggers a new search with the same query but new prefixes.
                    _prefixConfigs.value = prefixConfigurations
                }
        }
    }

    // ========================================================================
    // PUBLIC API - Called from UI
    // ========================================================================

    /**
     * Show the search dialog.
     *
     * Setting _isSearchVisible to true triggers:
     * 1. The search pipeline re-runs (via combine), producing results for
     *    the current query (which is "" after hideSearch)
     * 2. The uiState combine emits a new state with isSearchVisible=true
     * 3. Compose shows the AppSearchDialog
     */
    fun showSearch() {
        _isSearchVisible.value = true
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
        _isSearchVisible.value = false
        _query.value = ""
    }

    /**
     * Update the search query.
     *
     * This is called on every keystroke from the OutlinedTextField's onValueChange.
     * It updates _query immediately, which:
     * 1. Updates uiState.query via the uiState combine (next Compose frame)
     * 2. Triggers the search pipeline via the pipeline combine (starts new search)
     *
     * RACE CONDITION — WHY THIS IS SAFE:
     * The query goes into _query (Layer 1) and the search pipeline reads it
     * as INPUT. The pipeline writes to _searchOutput (Layer 3), which does
     * NOT contain the query. The final uiState.query always comes from _query,
     * never from the pipeline output. So a slow search can never overwrite
     * what the user typed.
     *
     * @param newQuery The new query text from the TextField
     */
    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    /**
     * Update contacts permission status.
     * Called from Activity when permission state changes.
     *
     * This feeds into _backgroundState, which triggers the search pipeline
     * to re-run. If the user is in contacts mode ("c "), the search will
     * now succeed instead of showing the permission prompt.
     *
     * @param hasPermission Whether permission is granted
     */
    fun updateContactsPermission(hasPermission: Boolean) {
        _hasContactsPermission.value = hasPermission
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
        _hasFilesPermission.value = hasPermission
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
        _query.value = ""
    }

    // ========================================================================
    // SEARCH LOGIC
    // ========================================================================

    /**
     * Execute the actual search and return a list of results.
     *
     * This is a pure-ish function: it reads from providers/repositories but
     * does not write to any ViewModel state. It's called from the search
     * pipeline's mapLatest, which handles cancellation automatically.
     *
     * FLOW:
     * 1. If a provider prefix was detected (e.g., "f ", "c "), delegate to
     *    that provider's search() method.
     * 2. If no prefix, check if query is a URL. If so, include a URL result.
     * 3. Filter installed apps by query (5-tier priority matching).
     * 4. Limit app results to 8 for the grid layout (2 rows × 4 columns).
     *
     * THREADING:
     * This runs inside mapLatest on viewModelScope (Main dispatcher).
     * Slow operations (file search, contact search) use withContext(Dispatchers.IO)
     * inside their respective repositories. Fast operations (app filtering)
     * run in-place.
     *
     * @param parsed The parsed search query (provider + actual query text)
     * @param installedApps Current snapshot of installed apps
     * @param recentApps Current snapshot of recent apps
     * @return List of search results to display
     */
    private suspend fun executeSearch(
        parsed: com.milki.launcher.domain.search.ParsedQuery,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<SearchResult> {
        // If a provider prefix was detected, delegate entirely to that provider.
        // The provider handles permissions, empty queries, etc. internally.
        if (parsed.provider != null) {
            return try {
                parsed.provider.search(parsed.query)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // No provider prefix — default app search with optional URL detection.

        // Check if the query looks like a URL (e.g., "github.com", "https://...")
        val urlResult = detectUrl(parsed.query)

        // Filter apps using 5-tier priority matching:
        // exact → starts-with → word-boundary → contains → fuzzy (subsequence)
        val filteredApps = filterAppsUseCase(
            query = parsed.query,
            installedApps = installedApps,
            recentApps = recentApps
        )

        // Limit to 8 results for the 2×4 grid layout.
        // Users can refine their search if the desired app isn't shown.
        val appResults = filteredApps.take(8).map { app ->
            AppSearchResult(appInfo = app)
        }

        // If a URL was detected, show it first, followed by app results.
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
}
