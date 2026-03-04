package com.milki.launcher.presentation.search

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.domain.model.ProviderPrefixConfiguration
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.search.FilterAppsUseCase
import com.milki.launcher.domain.search.ParsedQuery
import com.milki.launcher.domain.search.SearchProviderRegistry
import com.milki.launcher.domain.search.UrlHandlerResolver
import com.milki.launcher.domain.search.parseSearchQuery
import com.milki.launcher.util.UrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Coordinates the asynchronous search pipeline for SearchViewModel.
 *
 * This class intentionally owns only search orchestration concerns:
 * - combines input flows (query, visibility, background state, prefix config)
 * - emits loading + final search output
 * - delegates provider-based and app-based searches
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SearchViewModelPipelineCoordinator(
    private val providerRegistry: SearchProviderRegistry,
    private val filterAppsUseCase: FilterAppsUseCase,
    private val urlHandlerResolver: UrlHandlerResolver
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
        prefixConfigurations: StateFlow<ProviderPrefixConfiguration>,
        existingOutput: MutableStateFlow<SearchPipelineOutput>
    ): StateFlow<SearchPipelineOutput> {
        return combine(
            query,
            isSearchVisible,
            backgroundState,
            prefixConfigurations
        ) { currentQuery, visible, background, _ ->
            Triple(currentQuery, visible, background)
        }
            .mapLatest { (currentQuery, visible, background) ->
                if (!visible) {
                    SearchPipelineOutput()
                } else {
                    val parsed = parseSearchQuery(currentQuery, providerRegistry)

                    existingOutput.update { current ->
                        current.copy(
                            isLoading = true,
                            activeProviderConfig = parsed.config
                        )
                    }

                    val results = executeSearch(
                        parsed = parsed,
                        installedApps = background.installedApps,
                        recentApps = background.recentApps
                    )

                    SearchPipelineOutput(
                        results = results,
                        activeProviderConfig = parsed.config,
                        isLoading = false
                    )
                }
            }
            .onEach { output ->
                existingOutput.value = output
            }
            .stateIn(scope, SharingStarted.Eagerly, existingOutput.value)
    }

    /**
     * Executes one search request using either provider mode (prefix detected)
     * or default app search mode.
     */
    private suspend fun executeSearch(
        parsed: ParsedQuery,
        installedApps: List<AppInfo>,
        recentApps: List<AppInfo>
    ): List<SearchResult> {
        if (parsed.provider != null) {
            return runProviderSearch(parsed.provider, parsed.query)
        }

        val urlResult = detectUrl(parsed.query)
        val filteredApps = filterAppsUseCase(
            query = parsed.query,
            installedApps = installedApps,
            recentApps = recentApps
        )

        val appResults = filteredApps
            .take(8)
            .map { app -> AppSearchResult(appInfo = app) }

        return if (urlResult != null) {
            listOf(urlResult) + appResults
        } else {
            appResults
        }
    }

    /**
     * Executes provider-specific search with defensive exception handling.
     */
    private suspend fun runProviderSearch(provider: SearchProvider, query: String): List<SearchResult> {
        return try {
            provider.search(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Detects URL-like queries and resolves a preferred handler app.
     */
    private fun detectUrl(query: String): UrlSearchResult? {
        val validationResult = UrlValidator.validateUrl(query) ?: return null
        val handlerApp = urlHandlerResolver.resolveUrlHandler(validationResult.url)

        return UrlSearchResult(
            url = validationResult.url,
            displayUrl = validationResult.displayUrl,
            handlerApp = handlerApp,
            browserFallback = true
        )
    }
}
