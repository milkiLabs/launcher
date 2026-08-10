/**
 * SearchResultsList.kt - Search results display containers
 *
 * This file contains the composable functions that display search results
 * in different layouts depending on the result types.
 *
 * LAYOUT DECISION:
 * - If ALL results are AppSearchResult → Show 2×5 grid (10 apps max)
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.AppSearchResult
import com.milki.launcher.domain.model.ContactSearchResult
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.PhoneNumberSearchResult
import com.milki.launcher.domain.model.SearchLayout
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

private const val APP_RESULTS_GRID_COLUMNS = 5

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
    searchLayout: SearchLayout = SearchLayout.CLASSIC,
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

    /**
     * In ONE_HANDED mode the results are anchored to the bottom of the dialog,
     * so we flip the display order: the most relevant result (the first item)
     * ends up at the bottom, close to the thumb, and scrolling up reveals more.
     */
    val reverseOrder = searchLayout == SearchLayout.ONE_HANDED

    if (allAppResults && results.isNotEmpty()) {
        /**
         * GRID LAYOUT for app-only results.
         *
         * This is the primary use case: user searches for apps
         * or views recent apps. The grid shows 10 apps in a
         * compact 2-row × 5-column layout.
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
            reverseOrder = reverseOrder,
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
            reverseOrder = reverseOrder,
            modifier = modifier
        )
    }
}

/**
 * AppResultsGrid - Displays app results in a 2×5 grid layout.
 *
 * GRID CONFIGURATION:
 * - 5 columns (fixed width, evenly distributed)
 * - 2 rows (implicit, based on number of items)
 * - Maximum 10 items (limited by ViewModel)
 *
 * The grid is rendered as a simple 5-column layout because search
 * app results are capped at 10 items. This lets the dialog wrap to
 * content height instead of reserving unnecessary empty space.
 *
 * @param appResults List of app search results to display (max 10)
 * @param actionHandler The action handler to emit actions when user interacts
 * @param reverseOrder When true (ONE_HANDED layout), the grid is flipped so the
 *                     most relevant apps appear on the bottom row, with the top
 *                     result in the bottom-right corner (closest to the thumb).
 */
@Composable
private fun AppResultsGrid(
    appResults: List<AppSearchResult>,
    actionHandler: (SearchResultAction) -> Unit,
    onExternalAppDragStart: () -> Unit,
    reverseOrder: Boolean = false,
    modifier: Modifier = Modifier
) {
    val displayOrder = if (reverseOrder) appResults.asReversed() else appResults

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.smallMedium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        displayOrder.chunked(APP_RESULTS_GRID_COLUMNS).forEach { rowResults ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                val fillers = APP_RESULTS_GRID_COLUMNS - rowResults.size

                if (reverseOrder) {
                    repeat(fillers) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                rowResults.forEach { result ->
                    Box(modifier = Modifier.weight(1f)) {
                        AppGridItem(
                            appInfo = result.appInfo,
                            onExternalDragStarted = onExternalAppDragStart,
                            onClick = { actionHandler(SearchResultAction.Tap(result)) }
                        )
                    }
                }

                if (!reverseOrder) {
                    repeat(fillers) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
 * @param reverseOrder When true (ONE_HANDED layout), the list is bottom-anchored:
 *                     the most relevant result is pinned to the bottom edge and
 *                     scrolling upward reveals more results.
 */
@Composable
private fun MixedResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    providerAccentColorById: Map<String, String>,
    actionHandler: (SearchResultAction) -> Unit,
    onExternalAppDragStart: () -> Unit,
    reverseOrder: Boolean = false,
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
     * top (or bottom in ONE_HANDED mode) when new results arrive.
     */
    val scrollState = rememberScrollState()

    /**
     * In ONE_HANDED mode the list is flipped so the most relevant result
     * (the first item) is displayed last and anchored to the bottom edge.
     */
    val displayResults = if (reverseOrder) results.asReversed() else results

    /**
     * LaunchedEffect with results as the key ensures this effect runs
     * whenever the results list changes. In classic mode we scroll to the
     * top so the most relevant results are visible. In ONE_HANDED mode we
     * scroll to the bottom so the most relevant result is pinned to the
     * bottom edge and the user scrolls upward to see more.
     */
    LaunchedEffect(results, reverseOrder) {
        if (reverseOrder) {
            snapshotFlow { scrollState.maxValue }
                .collect { maxValue -> scrollState.scrollTo(maxValue) }
        } else {
            scrollState.scrollTo(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = if (reverseOrder) Arrangement.Bottom else Arrangement.Top
    ) {
        /**
         * Each result type gets its own dedicated composable.
         * This allows for type-specific layouts and interactions.
         *
         * The when expression ensures exhaustive handling of all
         * SearchResult subtypes - if a new type is added, the
         * compiler will warn about missing branches.
         */
        if (reverseOrder) {
            Spacer(modifier = Modifier.height(Spacing.smallMedium))
        }

        displayResults.forEach { result ->
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
                is PhoneNumberSearchResult -> {
                    PhoneNumberSearchResultItem(
                        result = result,
                        accentColor = accentColor,
                        onCallClick = {
                            actionHandler(SearchResultAction.DialPhoneNumber(result.phoneNumber))
                        },
                        onSaveClick = {
                            actionHandler(SearchResultAction.SavePhoneNumber(result.phoneNumber))
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

        if (!reverseOrder) {
            Spacer(modifier = Modifier.height(Spacing.smallMedium))
        }
    }
}
