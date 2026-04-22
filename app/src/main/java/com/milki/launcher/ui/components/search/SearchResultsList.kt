/**
 * SearchResultsList.kt - Search results display containers
 *
 * This file contains the composable functions that display search results
 * in different layouts depending on the result types.
 *
 * LAYOUT DECISION:
 * - If ALL results are AppSearchResult → Show 2×4 grid (8 apps max)
 * - If MIXED result types → Show traditional vertical list
 *
 * This design choice prioritizes:
 * - Grid for quick app access (most common use case)
 * - List for mixed results (web search, contacts, etc.)
 *
 * ACTION HANDLING:
 * Search result actions are handled via LocalSearchActionHandler (CompositionLocal),
 * not via callbacks. This eliminates prop drilling and simplifies the component hierarchy.
 */

package com.milki.launcher.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.model.WebSearchResult
import com.milki.launcher.domain.model.YouTubeSearchResult
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.common.AppGridItem
import com.milki.launcher.ui.components.common.AppListItem
import com.milki.launcher.ui.theme.Spacing

/**
 * SearchResultsList - Displays search results in either a grid or list layout.
 *
 * This is the main container that decides which layout to use based on
 * the types of search results. It acts as a "smart" dispatcher that
 * chooses the appropriate visual representation.
 *
 * LAYOUT LOGIC:
 * - All AppSearchResult → Grid layout (compact, fast scanning)
 * - Mixed types → List layout (more space for details)
 *
 * ACTION HANDLING:
 * All result clicks are handled via LocalSearchActionHandler, which is
 * provided by MainActivity. This eliminates the need for callback props.
 *
 * @param results List of search results to display
 * @param activeProviderConfig Current search provider (for theming)
 */
@Composable
fun SearchResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    providerAccentColorById: Map<String, String> = emptyMap(),
    onExternalAppDragStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    /**
     * Get the action handler from CompositionLocal.
     * This allows us to emit actions without prop drilling.
     */
    val actionHandler = LocalSearchActionHandler.current
    
    /**
     * Check if all results are app results.
     * If true, we can display them in a compact grid layout.
     * If false (mixed types including URL results), we use the traditional list layout.
     * 
     * URL results are displayed in list format because they have additional
     * information (the full URL) that benefits from the wider list item layout.
     */
    val allAppResults = results.all { it is AppSearchResult }

    if (allAppResults && results.isNotEmpty()) {
        /**
         * GRID LAYOUT for app-only results.
         *
         * This is the primary use case: user searches for apps
         * or views recent apps. The grid shows 8 apps in a
         * compact 2-row × 4-column layout.
         *
         * Benefits:
         * - More apps visible at once
         * - Faster visual scanning (grid pattern is easier to scan)
         * - Takes up less vertical space
         */
        AppResultsGrid(
            appResults = results.filterIsInstance<AppSearchResult>(),
            actionHandler = actionHandler,
            onExternalAppDragStart = onExternalAppDragStart,
            modifier = modifier
        )
    } else {
        /**
         * LIST LAYOUT for mixed result types.
         *
         * Used when results include web search, contacts, YouTube, etc.
         * These result types have more information and need more
         * horizontal space, so a list is more appropriate.
         */
        MixedResultsList(
            results = results,
            activeProviderConfig = activeProviderConfig,
            providerAccentColorById = providerAccentColorById,
            actionHandler = actionHandler,
            onExternalAppDragStart = onExternalAppDragStart,
            modifier = modifier
        )
    }
}

/**
 * AppResultsGrid - Displays app results in a 2×4 grid layout.
 *
 * GRID CONFIGURATION:
 * - 4 columns (fixed width, evenly distributed)
 * - 2 rows (implicit, based on number of items)
 * - Maximum 8 items (limited by ViewModel)
 *
 * The grid is rendered as a simple 4-column layout because search
 * app results are capped at 8 items. This lets the dialog wrap to
 * content height instead of reserving unnecessary empty space.
 *
 * @param appResults List of app search results to display (max 8)
 * @param actionHandler The action handler to emit actions when user interacts
 */
@Composable
private fun AppResultsGrid(
    appResults: List<AppSearchResult>,
    actionHandler: (SearchResultAction) -> Unit,
    onExternalAppDragStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.smallMedium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        appResults.chunked(4).forEach { rowResults ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                rowResults.forEach { result ->
                    Box(modifier = Modifier.weight(1f)) {
                        AppGridItem(
                            appInfo = result.appInfo,
                            onExternalDragStarted = onExternalAppDragStart,
                            onClick = { actionHandler(SearchResultAction.Tap(result)) }
                        )
                    }
                }

                repeat(4 - rowResults.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * MixedResultsList - Displays mixed result types in a scrollable list.
 *
 * This is the fallback layout when results contain non-app types.
 * Uses the traditional vertical list with larger items that can
 * display additional information (like contact phone numbers).
 *
 * SCROLL BEHAVIOR:
 * The list automatically scrolls to the top whenever the results change.
 * This ensures that when the user modifies their search query, they see
 * the most relevant results at the top of the list, not stuck at a
 * previous scroll position from an older query.
 *
 * ITEM TYPES SUPPORTED:
 * - AppSearchResult → AppListItem (defined in AppListItem.kt)
 * - WebSearchResult → WebSearchResultItem
 * - YouTubeSearchResult → YouTubeSearchResultItem
 * - UrlSearchResult → UrlSearchResultItem
 * - ContactSearchResult → ContactSearchResultItem
 * - FileDocumentSearchResult → FileDocumentSearchResultItem
 * - PermissionRequestResult → PermissionRequestItem
 *
 * @param results List of search results (can be any type)
 * @param activeProviderConfig Current search provider configuration (for theming)
 * @param actionHandler The action handler to emit actions when user interacts
 */
@Composable
private fun MixedResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    providerAccentColorById: Map<String, String>,
    actionHandler: (SearchResultAction) -> Unit,
    onExternalAppDragStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val providerVisual = rememberSearchProviderVisual(
        providerId = activeProviderConfig?.providerId,
        customAccentHex = activeProviderConfig?.providerId?.let(providerAccentColorById::get)
    )
    val accentColor = providerVisual?.accentColor

    /**
     * Scroll state allows us to control and observe the scroll position
     * of the results container. We use this to programmatically scroll to the
     * top when new results arrive.
     */
    val scrollState = rememberScrollState()
    
    /**
     * LaunchedEffect with results as the key ensures this effect runs
     * whenever the results list changes. We use this to scroll to the
     * top of the list so the user sees the most relevant new results.
     *
     * This is important for the user experience because:
     * - When the user types more characters, results get filtered
     * - Without this, the scroll position stays at a random offset
     * - The user might miss the best matches that are now at the top
     */
    LaunchedEffect(results) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        /**
         * Each result type gets its own dedicated composable.
         * This allows for type-specific layouts and interactions.
         *
         * The when expression ensures exhaustive handling of all
         * SearchResult subtypes - if a new type is added, the
         * compiler will warn about missing branches.
         */
        results.forEach { result ->
            when (result) {
                is AppSearchResult -> {
                    AppListItem(
                        appInfo = result.appInfo,
                        onExternalDragStarted = onExternalAppDragStart,
                        onClick = { actionHandler(SearchResultAction.Tap(result)) }
                    )
                }
                is WebSearchResult -> {
                    val mappedProviderAccentHex = result.providerId?.let(providerAccentColorById::get)
                    val webResultAccentColor = if (result.providerId != null && mappedProviderAccentHex != null) {
                        rememberSearchProviderVisual(result.providerId, mappedProviderAccentHex)?.accentColor
                            ?: accentColor
                    } else {
                        accentColor
                    }

                    WebSearchResultItem(
                        result = result,
                        accentColor = webResultAccentColor,
                        onClick = { actionHandler(SearchResultAction.Tap(result)) }
                    )
                }
                is UrlSearchResult -> {
                    UrlSearchResultItem(
                        result = result,
                        onOpenInApp = { actionHandler(SearchResultAction.Tap(result)) }
                    )
                }
                is ContactSearchResult -> {
                    ContactSearchResultItem(
                        result = result,
                        accentColor = accentColor,
                        onClick = { actionHandler(SearchResultAction.Tap(result)) },
                        onExternalDragStarted = onExternalAppDragStart,
                        onDialClick = {
                            val phone = result.contact.phoneNumbers.firstOrNull()
                            if (phone != null) {
                                actionHandler(SearchResultAction.DialContact(result.contact, phone))
                            }
                        }
                    )
                }
                is FileDocumentSearchResult -> {
                    FileDocumentSearchResultItem(
                        result = result,
                        accentColor = accentColor,
                        onClick = { actionHandler(SearchResultAction.Tap(result)) },
                        onExternalDragStarted = onExternalAppDragStart
                    )
                }
                is PermissionRequestResult -> {
                    PermissionRequestItem(
                        result = result,
                        accentColor = accentColor,
                        onClick = { 
                            actionHandler(SearchResultAction.RequestPermission(
                                result.permission,
                                result.providerPrefix
                            ))
                        }
                    )
                }
                is YouTubeSearchResult -> {
                    YouTubeSearchResultItem(
                        result = result,
                        accentColor = accentColor,
                        onClick = { actionHandler(SearchResultAction.Tap(result)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.smallMedium))
    }
}
