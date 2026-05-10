package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.repository.SearchRequest
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.ParsedQuery
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.parseSearchQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates the asynchronous search pipeline for SearchViewModel.
 *
 * This class intentionally owns only search orchestration concerns:
 * - combines input flows (query, visibility, background state, prefix config)
 * - emits loading + final search output
 * - delegates provider-based and app-based searches
 */
internal class SearchViewModelPipelineCoordinator(
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase
) {

    /**
     * Creates and launches the "latest only" search pipeline.
     *
     * The returned StateFlow is hot and eagerly started so downstream UI state can
     * safely combine against it at all times.
     */
    fun bind(
        scope: CoroutineScope,
        query: StateFlow<String>,
        isSearchVisible: StateFlow<Boolean>,
        backgroundState: StateFlow<SearchBackgroundState>,
        runtimeSettings: StateFlow<SearchRuntimeSettings>,
        prefixConfigurations: StateFlow<ProviderPrefixConfiguration>,
        existingOutput: MutableStateFlow<SearchPipelineOutput>
    ): StateFlow<SearchPipelineOutput> {
        val searchGeneration = AtomicLong(0L)
        var activeSearchJob: kotlinx.coroutines.Job? = null

        combine(
            query,
            isSearchVisible,
            backgroundState,
            runtimeSettings,
            prefixConfigurations
        ) { currentQuery, visible, background, runtimeSettings, _ ->
            SearchPipelineInput(
                query = currentQuery,
                visible = visible,
                background = background,
                runtimeSettings = runtimeSettings
            )
        }
            .onEach { input ->
                activeSearchJob?.cancel()

                if (!input.visible) {
                    searchGeneration.incrementAndGet()
                    existingOutput.value = SearchPipelineOutput()
                } else {
                    val parsed = parseSearchQuery(input.query, providerRegistry)
                    val generation = searchGeneration.incrementAndGet()

                    existingOutput.update { current ->
                        current.copy(
                            isLoading = true,
                            activeProviderConfig = parsed.config
                        )
                    }

                    activeSearchJob = scope.launch(Dispatchers.Default) {
                        val results = executeSearch(
                            parsed = parsed,
                            installedApps = input.background.installedApps,
                            recentApps = input.background.recentApps,
                            contactsPermissionState = input.background.contactsPermissionState,
                            filesPermissionState = input.background.filesPermissionState,
                            settings = input.runtimeSettings
                        )

                        if (generation == searchGeneration.get()) {
                            existingOutput.value = SearchPipelineOutput(
                                results = results,
                                activeProviderConfig = parsed.config,
                                isLoading = false
                            )
                        }
                    }
                }
            }
            .launchIn(scope)

        return existingOutput.asStateFlow()
    }

    /**
     * Executes one search request using either provider mode (prefix detected)
     * or default app search mode.
     */
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
                    maxResults = settings.maxSearchResults,
                    contactsPermissionState = contactsPermissionState,
                    filesPermissionState = filesPermissionState
                )
            )
        }

        val recentAppsToUse = if (settings.showRecentApps) recentApps else emptyList()

        val filteredApps = filterAppsUseCase(
            query = parsed.query,
            installedApps = installedApps,
            recentApps = recentAppsToUse
        )

        val appResults = filteredApps
            .take(settings.maxSearchResults)
            .map { app -> AppSearchResult(appInfo = app) }

        return appResults
    }

    /**
     * Executes provider-specific search with defensive exception handling.
     */
    private suspend fun runProviderSearch(
        provider: SearchProvider,
        request: SearchRequest
    ): List<SearchResult> {
        return try {
            withContext(Dispatchers.IO) {
                provider.search(request)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

}

/**
 * Input snapshot for one pipeline execution pass.
 */
private data class SearchPipelineInput(
    val query: String,
    val visible: Boolean,
    val background: SearchBackgroundState,
    val runtimeSettings: SearchRuntimeSettings
)
