/**
 * AppSearchDialog.kt - Multi-mode search dialog with prefix shortcuts and contact support
 *
 * This dialog supports multiple search modes triggered by prefixes:
 * - No prefix: Search installed apps
 * - "s ": Web search (Google, DuckDuckGo)
 * - "c ": Search contacts (requires permission)
 * - "y ": YouTube search
 *
 * The contacts search feature includes permission handling - if permission is not
 * granted, it shows a button to request permission instead of search results.
 *
 * When contacts are found, clicking a contact initiates a phone call.
 */

package com.milki.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.delay

/**
 * AppSearchDialog - Main search dialog component supporting multiple search modes.
 *
 * This dialog provides a unified search interface with prefix-based mode switching:
 * - Default mode (no prefix): Searches installed apps
 * - "s " prefix: Web search mode
 * - "c " prefix: Contacts search mode (with permission handling)
 * - "y " prefix: YouTube search mode
 *
 * Each mode has visual indicators (colored bars, icons, hints) to help users
 * understand which search type is active.
 *
 * For contacts mode, if permission is not granted, shows a permission request button
 * instead of search results. Once granted, displays actual contacts from the device.
 *
 * @param searchQuery Current search text (controlled from parent)
 * @param onSearchQueryChange Called when user types (parent updates state)
 * @param installedApps List of all installed apps for default search mode
 * @param recentApps List of recently launched apps (shown when search empty)
 * @param onDismiss Called when dialog should close
 * @param onLaunchApp Called when user selects an app to launch
 * @param onSearchWeb Called when user selects a web search result
 * @param onSearchYouTube Called when user selects a YouTube search result
 * @param hasContactsPermission Whether READ_CONTACTS permission is granted
 * @param onRequestContactsPermission Called when user clicks permission request button
 * @param searchContacts Function to search contacts (requires permission)
 * @param onCallContact Called when user clicks a contact to make a phone call
 */
@Composable
fun AppSearchDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onSearchWeb: (query: String, engine: String) -> Unit = { _, _ -> },
    onSearchYouTube: (query: String) -> Unit = {},
    hasContactsPermission: Boolean = false,
    onRequestContactsPermission: () -> Unit = {},
    searchContacts: suspend (query: String) -> List<Contact> = { emptyList() },
    onCallContact: (contact: Contact) -> Unit = {}
) {
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================

    /**
     * FocusRequester allows us to programmatically control focus.
     * We use this to automatically open the keyboard when dialog opens.
     */
    val focusRequester = remember { FocusRequester() }

    /**
     * Search providers configuration.
     *
     * These define the available search modes. The contacts provider
     * is configured with permission state and callbacks for proper
     * permission handling.
     */
    val searchProviders = remember(hasContactsPermission) {
        listOf(
            SearchProviders.webProvider(onSearchWeb),
            SearchProviders.contactsProvider(
                hasPermission = hasContactsPermission,
                onRequestPermission = onRequestContactsPermission,
                searchContacts = searchContacts,
                onCallContact = onCallContact
            ),
            SearchProviders.youtubeProvider(onSearchYouTube)
        )
    }

    /**
     * Parse the current query to detect provider prefix.
     *
     * This runs when searchQuery changes.
     * It separates the prefix (if any) from the actual search query.
     */
    val (activeProvider, actualQuery) = remember(searchQuery, searchProviders) {
        parseSearchQuery(searchQuery, searchProviders)
    }

    /**
     * Search results state for provider-based searches.
     *
     * For app search (no prefix), results are computed synchronously.
     * For provider searches, results are computed and stored here.
     */
    var providerResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    /**
     * Compute app search results (only when no provider prefix is active).
     *
     * Uses remember to cache results and avoid recomputing on every recomposition.
     * Only recomputes when: searchQuery, installedApps, or recentApps change.
     */
    val appResults = remember(searchQuery, installedApps, recentApps, activeProvider) {
        if (activeProvider == null) {
            // No provider prefix - search apps
            filterApps(searchQuery, installedApps, recentApps)
                .map { app ->
                    AppSearchResult(
                        appInfo = app,
                        onClick = { onLaunchApp(app) }
                    )
                }
        } else {
            // Provider is active - don't show app results
            emptyList()
        }
    }

    /**
     * Launch effect to perform provider searches.
     *
     * When a provider is active and there's a query, we call the provider's
     * search function. This allows operations like querying contacts.
     */
    LaunchedEffect(activeProvider, actualQuery, hasContactsPermission) {
        if (activeProvider != null && actualQuery.isNotBlank()) {
            // Perform search using the active provider
            providerResults = activeProvider.search(actualQuery)
        } else if (activeProvider != null && activeProvider.prefix == "c" && !hasContactsPermission) {
            // Special case: contacts provider is active but no permission
            // Show permission request even without a query
            providerResults = activeProvider.search("")
        } else {
            // Clear provider results when no provider or empty query
            providerResults = emptyList()
        }
    }

    // ========================================================================
    // BACK BUTTON HANDLING
    // ========================================================================

    /**
     * BackHandler intercepts the Android back button.
     * Without this, pressing back would exit the launcher entirely.
     * With this, pressing back closes the search dialog.
     */
    BackHandler { onDismiss() }

    // ========================================================================
    // DIALOG UI
    // ========================================================================

    Dialog(
        // Called when user taps outside dialog or presses back
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Don't use platform default width (too narrow)
            usePlatformDefaultWidth = false,
            // Ensure dialog respects system windows (status bar, nav bar)
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                // Pad for on-screen keyboard
                .imePadding()
                // Pad for navigation bar (gesture area)
                .navigationBarsPadding()
                // Pad for status bar (time, battery)
                .statusBarsPadding(),

            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            /**
             * Column stacks the search field and results vertically.
             */
            Column(modifier = Modifier.fillMaxSize()) {

                // ============================================================
                // SEARCH TEXT FIELD WITH MODE INDICATOR
                // ============================================================
                SearchTextFieldWithIndicator(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    activeProvider = activeProvider,
                    onLaunchFirstResult = {
                        // Launch first result when "Done" pressed on keyboard
                        val firstResult = if (activeProvider != null) {
                            providerResults.firstOrNull()
                        } else {
                            appResults.firstOrNull()
                        }
                        firstResult?.onClick?.invoke()
                    },
                    onClear = { onSearchQueryChange("") }
                )

                // ============================================================
                // CONTENT AREA (Results List or Empty State)
                // ============================================================
                val resultsToShow = if (activeProvider != null) {
                    providerResults
                } else {
                    appResults
                }

                if (resultsToShow.isEmpty()) {
                    // No results to show - display empty state
                    EmptyState(
                        searchQuery = searchQuery,
                        activeProvider = activeProvider,
                        actualQuery = actualQuery
                    )
                } else {
                    // Show scrollable list of results
                    SearchResultsList(
                        results = resultsToShow,
                        activeProvider = activeProvider
                    )
                }
            }
        }
    }

    // ========================================================================
    // AUTO-FOCUS EFFECT
    // ========================================================================

    /**
     * LaunchedEffect runs a coroutine when the composable enters composition.
     * The 'Unit' key means this runs once (when dialog first opens).
     *
     * We add a small delay to ensure the UI is fully rendered,
     * then request focus on the text field to open the keyboard.
     */
    LaunchedEffect(Unit) {
        delay(10)
        focusRequester.requestFocus()
    }
}

/**
 * SearchTextFieldWithIndicator - Search input with mode indicator bar.
 *
 * This component shows:
 * - The text input field
 * - A colored bar below indicating the active search mode
 * - A hint text showing the mode description
 * - Clear button (X) when there's text
 *
 * @param searchQuery Current text value
 * @param onSearchQueryChange Callback when text changes
 * @param focusRequester Used to auto-focus the field
 * @param activeProvider The currently active search provider (null = app search)
 * @param onLaunchFirstResult Callback when "Done" pressed on keyboard
 * @param onClear Callback when clear button is clicked
 */
@Composable
private fun SearchTextFieldWithIndicator(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    activeProvider: SearchProvider?,
    onLaunchFirstResult: () -> Unit,
    onClear: () -> Unit
) {
    /**
     * Animated color for the mode indicator bar.
     * Smoothly transitions between colors when switching modes.
     */
    val indicatorColor by animateColorAsState(
        targetValue = activeProvider?.color ?: MaterialTheme.colorScheme.primary,
        label = "indicator_color"
    )

    /**
     * Dynamic placeholder text based on active mode.
     * Helps users understand what they're searching.
     */
    val placeholderText = when (activeProvider?.prefix) {
        "s" -> "Search the web..."
        "c" -> "Search contacts..."
        "y" -> "Search YouTube..."
        else -> "Search apps..."
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ============================================================
        // TEXT INPUT FIELD
        // ============================================================
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
                // Attach focus requester for programmatic focus control
                .focusRequester(focusRequester),

            // Placeholder text shown when empty (changes based on mode)
            placeholder = { Text(placeholderText) },

            // Prevent multiline input
            singleLine = true,

            // Keyboard configuration
            keyboardOptions = KeyboardOptions(
                // Show "Done" button instead of newline
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                // When "Done" pressed, launch first matching result
                onDone = { onLaunchFirstResult() }
            ),

            // Leading icon shows current mode
            leadingIcon = {
                activeProvider?.let { provider ->
                    Icon(
                        imageVector = provider.icon,
                        contentDescription = provider.name,
                        tint = provider.color
                    )
                }
            },

            // Clear button (X) shown when there's text
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

        // ============================================================
        // MODE INDICATOR BAR
        // ============================================================
        /**
         * Colored bar that visually indicates the active search mode.
         * Height changes based on whether a provider is active:
         * - Provider active: 4dp bar
         * - No provider: 2dp subtle line (app search mode)
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
                .height(if (activeProvider != null) 4.dp else 2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )

        // ============================================================
        // MODE HINT TEXT
        // ============================================================
        /**
         * Text hint showing the current mode and provider description.
         * Only shown when a provider is active.
         */
        if (activeProvider != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = activeProvider.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = activeProvider.color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${activeProvider.name}: ${activeProvider.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = activeProvider.color
                )
            }
        } else {
            // Spacer when no provider to maintain consistent spacing
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * SearchResultsList - Displays search results in a scrollable list.
 *
 * Handles app results, web results, contact results, and permission requests.
 *
 * @param results List of SearchResult objects to display
 * @param activeProvider The currently active provider (for styling)
 */
@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    activeProvider: SearchProvider?
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
                    // App result - use existing AppListItem
                    AppListItem(
                        appInfo = result.appInfo,
                        onClick = result.onClick
                    )
                }
                is WebSearchResult -> {
                    // Web search result
                    WebSearchResultItem(
                        result = result,
                        accentColor = activeProvider?.color ?: MaterialTheme.colorScheme.primary
                    )
                }
                is ContactSearchResult -> {
                    // Contact search result
                    ContactSearchResultItem(
                        result = result,
                        accentColor = activeProvider?.color ?: MaterialTheme.colorScheme.primary
                    )
                }
                is PermissionRequestResult -> {
                    // Permission request button
                    PermissionRequestItem(
                        result = result,
                        accentColor = activeProvider?.color ?: MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * WebSearchResultItem - Displays a web search result.
 *
 * Shows the search engine name and the search query.
 *
 * @param result The WebSearchResult to display
 * @param accentColor Color for icons and highlights
 */
@Composable
private fun WebSearchResultItem(
    result: WebSearchResult,
    accentColor: Color
) {
    ListItem(
        headlineContent = {
            Text(text = result.title)
        },
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
        modifier = Modifier.clickable { result.onClick() }
    )
}

/**
 * ContactSearchResultItem - Displays a contact search result.
 *
 * Shows contact name and primary phone number.
 * Clicking initiates a phone call.
 *
 * @param result The ContactSearchResult to display
 * @param accentColor Color for icons and highlights
 */
@Composable
private fun ContactSearchResultItem(
    result: ContactSearchResult,
    accentColor: Color
) {
    ListItem(
        headlineContent = {
            Text(text = result.contact.displayName)
        },
        supportingContent = {
            // Show primary phone number if available
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
            // Show phone icon to indicate clicking will call
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call contact",
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier.clickable { result.onClick() }
    )
}

/**
 * PermissionRequestItem - Displays a button to request permission.
 *
 * Shown when a search provider requires permission that hasn't been granted.
 *
 * @param result The PermissionRequestResult to display
 * @param accentColor Color for icons and highlights
 */
@Composable
private fun PermissionRequestItem(
    result: PermissionRequestResult,
    accentColor: Color
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
            // Warning icon
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Message explaining why permission is needed
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission request button
            Button(
                onClick = result.onClick,
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
 *
 * Shows different messages based on:
 * - Whether search is empty (show recent apps message)
 * - Whether a provider is active (show provider-specific hint)
 *
 * @param searchQuery Current search text
 * @param activeProvider Currently active search provider (null = app search)
 * @param actualQuery The query after removing the prefix
 */
@Composable
private fun EmptyState(
    searchQuery: String,
    activeProvider: SearchProvider?,
    actualQuery: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon indicating current mode
            val icon = activeProvider?.icon ?: Icons.Default.Search
            val tint = activeProvider?.color ?: MaterialTheme.colorScheme.onSurfaceVariant

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = tint.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Message text
            val message = when {
                // Empty search - show hint about recent apps
                searchQuery.isBlank() -> "No recent apps\nType to search"

                // Provider active but no query yet - waiting for input
                activeProvider != null && actualQuery.isBlank() -> {
                    "Type your ${activeProvider.name.lowercase()} query"
                }

                // Provider active with query but no results
                activeProvider != null -> "No ${activeProvider.name.lowercase()} results found"

                // App search with no matches
                else -> "No apps found"
            }

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            // Show available prefixes hint when search is empty
            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Prefix shortcuts:\n" +
                           "s - Web search\n" +
                           "c - Contacts\n" +
                           "y - YouTube",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Filter apps using a smart matching algorithm.
 *
 * This is a pure function with no side effects.
 *
 * Priority order for results:
 * 1. Exact matches (highest priority)
 * 2. Starts with matches (medium priority)
 * 3. Contains matches (lowest priority)
 *
 * When search is empty, returns recent apps instead of all apps.
 *
 * @param searchQuery The user's search text
 * @param installedApps All installed apps (for when user is searching)
 * @param recentApps Recently launched apps (for when search is empty)
 * @return Filtered and prioritized list of apps
 */
private fun filterApps(
    searchQuery: String,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>
): List<AppInfo> {
    // When search is empty, show recent apps
    if (searchQuery.isBlank()) {
        return recentApps
    }

    val queryLower = searchQuery.trim().lowercase()

    // Three lists for different match priorities
    val exactMatches = mutableListOf<AppInfo>()
    val startsWithMatches = mutableListOf<AppInfo>()
    val containsMatches = mutableListOf<AppInfo>()

    // Categorize each app based on match type
    installedApps.forEach { app ->
        when {
            // Exact match: name or package equals query exactly
            app.nameLower == queryLower || app.packageLower == queryLower -> {
                exactMatches.add(app)
            }
            // Starts with: name or package starts with query
            app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                startsWithMatches.add(app)
            }
            // Contains: name or package contains query anywhere
            app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
                containsMatches.add(app)
            }
        }
    }

    // Combine in priority order using the + operator (creates new list)
    return exactMatches + startsWithMatches + containsMatches
}
