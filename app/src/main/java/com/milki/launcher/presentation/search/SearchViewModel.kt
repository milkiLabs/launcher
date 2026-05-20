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
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.repository.SearchRequest
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.ParsedQuery
import com.milki.launcher.domain.search.SearchProviderFactory
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.SuggestionResolver
import com.milki.launcher.domain.search.parseSearchQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
 * @property settingsRepository Repository for settings (including prefix configs)
 * @property providerRegistry Registry of search providers
 * @property filterAppsUseCase Use case for filtering apps
 * @property suggestionResolver Resolver that classifies text into one smart action suggestion
 */
class SearchViewModel(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsReader,
    private val providerRegistry: SearchProviderRegistry,
    private val searchProviderFactory: SearchProviderFactory,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val suggestionResolver: SuggestionResolver
) : ViewModel() {
    private val stateHolder = SearchState(viewModelScope)
    private val searchPrefixConfigurations = MutableStateFlow<ProviderPrefixConfiguration>(emptyMap())

    /**
     * Shared installed-app stream scoped to this ViewModel.
     *
     * WHY stateIn HERE:
     * - Replays the latest app list to any future collector immediately.
     * - Prevents repeated upstream collection setup if this ViewModel grows
     *   additional internal collectors in the future.
     * - Keeps startup/app-update behavior deterministic in one hot stream.
     */
    private val installedAppsStream = appRepository.observeInstalledApps().stateIn(
        scope = viewModelScope,
        started = ViewModelSharingStarted,
        initialValue = emptyList()
    )

    val uiState = stateHolder.uiState

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        bindSearchPipeline()
        observeInstalledApps()
        observeRecentApps()
        observeSearchSettings()
        observeQuerySuggestions()
    }

    // ========================================================================
    // DATA LOADING
    // ========================================================================

    /**
     * Observes installed apps from the repository's reactive stream.
     *
     * HOW THIS WORKS:
     * This is now the ONLY startup path for installed app data in SearchViewModel.
     * We intentionally do not perform a separate one-shot getInstalledApps() call.
     *
     * WHY SINGLE PATH:
     * - observeInstalledApps() already emits an initial full app list.
     * - Keeping one path avoids duplicate PackageManager scans at cold start.
     * - Keeping one path also avoids duplicate icon preload passes for the same list.
     *
     * RUNTIME BEHAVIOR:
     * After the initial emission, this collector stays active and receives updates
     * whenever packages are installed, removed, replaced, or changed.
     */
    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsStream.collect { apps ->
                stateHolder.installedApps.value = apps
            }
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

    private fun observeSearchSettings() {
        viewModelScope.launch {
            settingsRepository.settings
                .map { settings ->
                    SearchRuntimeSettingsSnapshot(
                        searchSources = settings.searchSources,
                        contactsSearchEnabled = settings.contactsSearchEnabled,
                        filesSearchEnabled = settings.filesSearchEnabled,
                        prefixConfigurations = settings.prefixConfigurations,
                        defaultSearchSourceId = settings.defaultSearchSourceId
                    )
                }
                .distinctUntilChanged()
                .collect { settings ->
                    applySearchSettings(settings)
                }
        }
    }

    private fun applySearchSettings(settings: SearchRuntimeSettingsSnapshot) {
        val enabledSources = settings.searchSources.filter { it.isEnabled }

        val dynamicProviderIds = providerRegistry
            .getAllConfigs()
            .map { it.providerId }
            .filter { it.startsWith("source_") }
            .toSet()

        val nextDynamicProviderIds = enabledSources.map { it.id }.toSet()

        dynamicProviderIds
            .filter { it !in nextDynamicProviderIds }
            .forEach(providerRegistry::unregister)

        enabledSources.forEach { source ->
            providerRegistry.register(searchProviderFactory.create(source))
        }

        val sourcePrefixConfigurations = enabledSources.associate { source ->
            source.id to PrefixConfig(source.prefixes)
        }

        val fixedProviderConfigurations = buildMap {
            if (settings.contactsSearchEnabled) {
                put(
                    ProviderId.CONTACTS,
                    settings.prefixConfigurations[ProviderId.CONTACTS] ?: PrefixConfig.single("c")
                )
            }
            if (settings.filesSearchEnabled) {
                put(
                    ProviderId.FILES,
                    settings.prefixConfigurations[ProviderId.FILES] ?: PrefixConfig.single("f")
                )
            }
        }

        val mergedConfigurations: ProviderPrefixConfiguration =
            fixedProviderConfigurations + sourcePrefixConfigurations

        providerRegistry.updatePrefixConfigurations(mergedConfigurations)
        stateHolder.runtimeSettings.value = SearchRuntimeSettings(
            searchSources = settings.searchSources.filter { it.showAsSuggestedAction },
            defaultSearchSourceId = settings.defaultSearchSourceId
        )
        searchPrefixConfigurations.value = mergedConfigurations
        stateHolder.providerAccentColorById.value = settings.searchSources.associate { it.id to it.accentColorHex }
    }

    private fun bindSearchPipeline() {
        viewModelScope.launch {
            combine(
                stateHolder.query,
                stateHolder.isSearchVisible,
                stateHolder.backgroundState,
                stateHolder.runtimeSettings,
                searchPrefixConfigurations
            ) { currentQuery, visible, background, runtimeSettings, _ ->
                SearchPipelineInput(
                    query = currentQuery,
                    visible = visible,
                    background = background,
                    runtimeSettings = runtimeSettings
                )
            }
                .collectLatest { input ->
                    if (!input.visible) {
                        stateHolder.searchOutput.value = SearchPipelineOutput()
                        return@collectLatest
                    }

                    val parsed = parseSearchQuery(input.query, providerRegistry)

                    stateHolder.searchOutput.update { current ->
                        current.copy(
                            isLoading = true,
                            activeProviderConfig = parsed.config
                        )
                    }

                    val results = executeSearch(
                        parsed = parsed,
                        installedApps = input.background.installedApps,
                        recentApps = input.background.recentApps,
                        contactsPermissionState = input.background.contactsPermissionState,
                        filesPermissionState = input.background.filesPermissionState,
                        settings = input.runtimeSettings
                    )

                    stateHolder.searchOutput.value = SearchPipelineOutput(
                        results = results,
                        activeProviderConfig = parsed.config,
                        isLoading = false
                    )
                }
        }
    }

    private fun observeQuerySuggestions() {
        viewModelScope.launch {
            stateHolder.query.collectLatest { currentQuery ->
                stateHolder.querySuggestion.value = if (currentQuery.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        suggestionResolver.resolveFromText(currentQuery)
                    }
                } else {
                    null
                }
            }
        }
    }

    private suspend fun executeSearch(
        parsed: ParsedQuery,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>,
        contactsPermissionState: PermissionAccessState,
        filesPermissionState: PermissionAccessState,
        settings: SearchRuntimeSettings
    ): List<SearchResult> {
        if (parsed.provider != null) {
            return runProviderSearch(
                provider = parsed.provider,
                request = SearchRequest(
                    query = parsed.query,
                    contactsPermissionState = contactsPermissionState,
                    filesPermissionState = filesPermissionState
                )
            )
        }

        val filteredApps = filterAppsUseCase(
            query = parsed.query,
            installedApps = installedApps,
            recentApps = recentApps
        )

        return filteredApps
            .map { app -> AppSearchResult(appInfo = app) }
    }

    private suspend fun runProviderSearch(
        provider: SearchProvider,
        request: SearchRequest
    ): List<SearchResult> {
        return try {
            withContext(Dispatchers.IO) {
                provider.search(request)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            emptyList()
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
        stateHolder.searchOutput.update { currentOutput ->
            currentOutput.copy(
                results = emptyList(),
                isLoading = true
            )
        }
        stateHolder.isSearchVisible.value = true
        stateHolder.clipboardSuggestion.value = suggestionResolver.resolveFromClipboard()
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
        stateHolder.querySuggestion.value = null
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
     * This updates stateHolder.contactsPermissionState, which is one input to
     * stateHolder.backgroundState. That background state update triggers the
     * search pipeline to re-run. If the user is in contacts mode ("c "), the
     * search can now succeed instead of showing the permission prompt.
     *
     * @param state User-relevant permission access state
     */
    fun updateContactsPermission(state: PermissionAccessState) {
        stateHolder.contactsPermissionState.value = state
    }

    /**
     * Update files permission status.
     * Called from Activity when permission state changes.
     *
     * Same mechanism as contacts — feeds into background state, triggers
     * pipeline re-run.
     *
     * @param state User-relevant permission access state
     */
    fun updateFilesPermission(state: PermissionAccessState) {
        stateHolder.filesPermissionState.value = state
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
     * Clear the search query and show recent apps.
     * Convenience method — equivalent to onQueryChange("").
     */
    fun clearQuery() {
        stateHolder.query.value = ""
    }
}

private data class SearchPipelineInput(
    val query: String,
    val visible: Boolean,
    val background: SearchBackgroundState,
    val runtimeSettings: SearchRuntimeSettings
)

private data class SearchRuntimeSettingsSnapshot(
    val searchSources: List<SearchSource>,
    val contactsSearchEnabled: Boolean,
    val filesSearchEnabled: Boolean,
    val prefixConfigurations: ProviderPrefixConfiguration,
    val defaultSearchSourceId: String?
)

internal data class SearchBackgroundState(
    val installedApps: List<AppInfo> = emptyList(),
    val recentApps: List<AppInfo> = emptyList(),
    val contactsPermissionState: PermissionAccessState = PermissionAccessState.CAN_REQUEST,
    val filesPermissionState: PermissionAccessState = PermissionAccessState.CAN_REQUEST
)

internal data class SearchPipelineOutput(
    val results: List<SearchResult> = emptyList(),
    val activeProviderConfig: SearchProviderConfig? = null,
    val isLoading: Boolean = false
)

internal data class SearchRuntimeSettings(
    val searchSources: List<SearchSource> = emptyList(),
    val defaultSearchSourceId: String? = null
)
