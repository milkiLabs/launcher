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
 * ARCHITECTURE:
 * These are "dumb" UI components - they only display what they're given.
 * - No business logic in this file
 * - State comes from SearchViewModel via parameters
 * - User interactions emit callbacks
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.*

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
 * @param results List of search results to display
 * @param activeProviderConfig Current search provider (for theming)
 * @param onResultClick Callback when user clicks a result
 */
@Composable
fun SearchResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    onResultClick: (SearchResult) -> Unit
) {
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
            onResultClick = onResultClick
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
            onResultClick = onResultClick
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
 * The grid uses LazyVerticalGrid which:
 * - Handles items lazily (only composes visible items)
 * - Provides consistent spacing
 * - Is performant even for larger datasets
 *
 * Note: Even though we only have 8 items, LazyVerticalGrid
 * provides a clean API for grid layouts with proper key handling.
 *
 * @param appResults List of app search results to display (max 8)
 * @param onResultClick Callback when an app is clicked
 */
@Composable
private fun AppResultsGrid(
    appResults: List<AppSearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        /**
         * items() creates a grid item for each result.
         * key = { it.appInfo.packageName } ensures stable identity
         * across recompositions, which improves performance.
         *
         * Stable keys are important because:
         * - They prevent unnecessary recompositions
         * - They enable smooth animations when items change
         * - They maintain item state (like scroll position)
         */
        items(
            items = appResults,
            key = { it.appInfo.packageName }
        ) { result ->
            AppGridItem(
                appInfo = result.appInfo,
                onClick = { onResultClick(result) }
            )
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
 * @param onResultClick Callback when a result is clicked
 */
@Composable
private fun MixedResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    onResultClick: (SearchResult) -> Unit
) {
    /**
     * LazyListState allows us to control and observe the scroll position
     * of the LazyColumn. We use this to programmatically scroll to the
     * top when new results arrive.
     */
    val listState = rememberLazyListState()
    
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
        listState.animateScrollToItem(0)
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        /**
         * Each result type gets its own dedicated composable.
         * This allows for type-specific layouts and interactions.
         *
         * The when expression ensures exhaustive handling of all
         * SearchResult subtypes - if a new type is added, the
         * compiler will warn about missing branches.
         */
        items(
            items = results,
            key = { it.id }
        ) { result ->
            when (result) {
                is AppSearchResult -> {
                    AppListItem(
                        appInfo = result.appInfo,
                        onClick = { onResultClick(result) }
                    )
                }
                is WebSearchResult -> {
                    WebSearchResultItem(
                        result = result,
                        accentColor = activeProviderConfig?.color,
                        onClick = { onResultClick(result) }
                    )
                }
                is UrlSearchResult -> {
                    UrlSearchResultItem(
                        result = result,
                        onClick = { onResultClick(result) }
                    )
                }
                is ContactSearchResult -> {
                    ContactSearchResultItem(
                        result = result,
                        accentColor = activeProviderConfig?.color,
                        onClick = { onResultClick(result) }
                    )
                }
                is FileDocumentSearchResult -> {
                    FileDocumentSearchResultItem(
                        result = result,
                        accentColor = activeProviderConfig?.color,
                        onClick = { onResultClick(result) }
                    )
                }
                is PermissionRequestResult -> {
                    PermissionRequestItem(
                        result = result,
                        accentColor = activeProviderConfig?.color,
                        onClick = { onResultClick(result) }
                    )
                }
                is YouTubeSearchResult -> {
                    YouTubeSearchResultItem(
                        result = result,
                        accentColor = activeProviderConfig?.color,
                        onClick = { onResultClick(result) }
                    )
                }
            }
        }
    }
}
