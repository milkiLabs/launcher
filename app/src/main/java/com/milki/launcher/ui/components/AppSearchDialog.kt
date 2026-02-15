/**
 * AppSearchDialog.kt - Multi-mode search dialog with prefix shortcuts
 *
 * This dialog supports multiple search modes triggered by prefixes:
 * - No prefix: Search installed apps
 * - "s ": Web search (Google, DuckDuckGo)
 * - "c ": Search contacts (requires permission)
 * - "y ": YouTube search
 *
 * ARCHITECTURE:
 * This component is a "dumb" UI component - it only displays what it's given.
 * - State comes from SearchViewModel via SearchUiState
 * - User interactions emit callbacks that the ViewModel handles
 * - No business logic in this file
 *
 * This follows the Unidirectional Data Flow (UDF) pattern:
 * State flows down, Events flow up.
 */

package com.milki.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milki.launcher.domain.model.*
import com.milki.launcher.presentation.search.SearchUiState
import kotlinx.coroutines.delay

/**
 * AppSearchDialog - Main search dialog component supporting multiple search modes.
 *
 * This is a stateless composable that receives all data via SearchUiState
 * and communicates user actions via callbacks.
 *
 * @param uiState Current search state from ViewModel
 * @param onQueryChange Called when user types in search field
 * @param onDismiss Called when dialog should close
 * @param onResultClick Called when user clicks a search result
 */
@Composable
fun AppSearchDialog(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchTextFieldWithIndicator(
                    searchQuery = uiState.query,
                    onSearchQueryChange = onQueryChange,
                    focusRequester = focusRequester,
                    activeProviderConfig = uiState.activeProviderConfig,
                    placeholderText = uiState.placeholderText,
                    onLaunchFirstResult = {
                        uiState.results.firstOrNull()?.let { onResultClick(it) }
                    },
                    onClear = { onQueryChange("") }
                )

                if (uiState.results.isEmpty()) {
                    EmptyState(
                        searchQuery = uiState.query,
                        activeProvider = uiState.activeProviderConfig,
                        prefixHint = uiState.prefixHint
                    )
                } else {
                    SearchResultsList(
                        results = uiState.results,
                        activeProviderConfig = uiState.activeProviderConfig,
                        onResultClick = onResultClick
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(10)
        focusRequester.requestFocus()
    }
}

/**
 * SearchTextFieldWithIndicator - Search input with mode indicator bar.
 */
@Composable
private fun SearchTextFieldWithIndicator(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    activeProviderConfig: SearchProviderConfig?,
    placeholderText: String,
    onLaunchFirstResult: () -> Unit,
    onClear: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = activeProviderConfig?.color ?: MaterialTheme.colorScheme.primary,
        label = "indicator_color"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(placeholderText) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLaunchFirstResult() }),
            leadingIcon = {
                activeProviderConfig?.let { config ->
                    Icon(
                        imageVector = config.icon,
                        contentDescription = config.name,
                        tint = config.color
                    )
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
                .height(if (activeProviderConfig != null) 4.dp else 2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )

        if (activeProviderConfig != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = activeProviderConfig.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = activeProviderConfig.color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${activeProviderConfig.name}: ${activeProviderConfig.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = activeProviderConfig.color
                )
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * SearchResultsList - Displays search results in either a grid or list layout.
 *
 * LAYOUT DECISION:
 * - If ALL results are AppSearchResult → Show 2x4 grid (8 apps max)
 * - If MIXED result types → Show traditional vertical list
 *
 * This design choice prioritizes:
 * - Grid for quick app access (most common use case)
 * - List for mixed results (web search, contacts, etc.)
 *
 * PERFORMANCE:
 * - Grid uses LazyVerticalGrid with fixed 4 columns
 * - Both use stable keys for efficient recomposition
 * - Only the first 8 app results are shown in grid
 */
@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    onResultClick: (SearchResult) -> Unit
) {
    /**
     * Check if all results are app results.
     * If true, we can display them in a compact grid layout.
     * If false (mixed types), we use the traditional list layout.
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
 * The grid is non-scrollable because:
 * - We only show 8 items max
 * - This keeps the UI simple and predictable
 * - Users can refine their search if they need different apps
 *
 * @param appResults List of app search results to display
 * @param onResultClick Callback when an app is clicked
 */
@Composable
private fun AppResultsGrid(
    appResults: List<AppSearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    /**
     * LazyVerticalGrid arranges items in a grid pattern.
     * Unlike a regular Row/Column arrangement, LazyVerticalGrid:
     * - Handles items lazily (only composes visible items)
     * - Provides consistent spacing
     * - Is more performant for larger datasets
     *
     * Even though we only have 8 items, LazyVerticalGrid
     * provides a clean API for grid layouts.
     */
    LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
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
 * @param results List of search results (can be any type)
 * @param activeProviderConfig Current search provider configuration
 * @param onResultClick Callback when a result is clicked
 */
@Composable
private fun MixedResultsList(
    results: List<SearchResult>,
    activeProviderConfig: SearchProviderConfig?,
    onResultClick: (SearchResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
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
                        accentColor = activeProviderConfig?.color ?: MaterialTheme.colorScheme.primary,
                        onClick = { onResultClick(result) }
                    )
                }
                is ContactSearchResult -> {
                    ContactSearchResultItem(
                        result = result,
                        accentColor = activeProviderConfig?.color ?: MaterialTheme.colorScheme.primary,
                        onClick = { onResultClick(result) }
                    )
                }
                is PermissionRequestResult -> {
                    PermissionRequestItem(
                        result = result,
                        accentColor = activeProviderConfig?.color ?: MaterialTheme.colorScheme.primary,
                        onClick = { onResultClick(result) }
                    )
                }
                is YouTubeSearchResult -> {
                    YouTubeSearchResultItem(
                        result = result,
                        accentColor = activeProviderConfig?.color ?: MaterialTheme.colorScheme.primary,
                        onClick = { onResultClick(result) }
                    )
                }
            }
        }
    }
}

/**
 * WebSearchResultItem - Displays a web search result.
 */
@Composable
private fun WebSearchResultItem(
    result: WebSearchResult,
    accentColor: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = result.title) },
        supportingContent = {
            Text(
                text = result.engine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = accentColor
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * YouTubeSearchResultItem - Displays a YouTube search result.
 */
@Composable
private fun YouTubeSearchResultItem(
    result: YouTubeSearchResult,
    accentColor: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = result.title) },
        supportingContent = {
            Text(
                text = "YouTube",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = accentColor
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * ContactSearchResultItem - Displays a contact search result.
 */
@Composable
private fun ContactSearchResultItem(
    result: ContactSearchResult,
    accentColor: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = result.contact.displayName) },
        supportingContent = {
            val primaryPhone = result.contact.phoneNumbers.firstOrNull()
            if (primaryPhone != null) {
                Text(
                    text = primaryPhone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = accentColor
            )
        },
        trailingContent = {
            if (result.contact.phoneNumbers.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call contact",
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * PermissionRequestItem - Displays a button to request permission.
 */
@Composable
private fun PermissionRequestItem(
    result: PermissionRequestResult,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                )
            ) {
                Text(result.buttonText)
            }
        }
    }
}

/**
 * EmptyState - Displayed when no results match the search.
 */
@Composable
private fun EmptyState(
    searchQuery: String,
    activeProvider: SearchProviderConfig?,
    prefixHint: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val icon = activeProvider?.icon ?: Icons.Default.Search
            val tint = activeProvider?.color ?: MaterialTheme.colorScheme.onSurfaceVariant

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = tint.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val message = when {
                searchQuery.isBlank() -> "No recent apps\nType to search"
                activeProvider != null -> "No ${activeProvider.name.lowercase()} results found"
                else -> "No apps found"
            }

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = prefixHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
