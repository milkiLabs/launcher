/**
 * AppSearchDialog.kt - Multi-mode search dialog with prefix shortcuts
 *
 * This dialog supports multiple search modes triggered by prefixes:
 * - No prefix: Search installed apps
 * - "s ": Web search (Google, DuckDuckGo)
 * - "c ": Search contacts (requires permission)
 * - "y ": YouTube search
 *
 * ACTION HANDLING:
 * Search result actions are handled via LocalSearchActionHandler (CompositionLocal),
 * not via callbacks. This eliminates prop drilling and simplifies the component hierarchy.
 *
 * RELATED FILES:
 * - SearchResultsList.kt: Contains the list/grid containers for results
 * - SearchResultItems.kt: Contains individual result item composables
 * - AppGridItem.kt: Grid item for displaying apps
 * - AppListItem.kt: List item for displaying apps
 */

package com.milki.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milki.launcher.domain.model.*
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlinx.coroutines.flow.first

/**
 * AppSearchDialog - Main search dialog component supporting multiple search modes.
 *
 * This is a stateless composable that receives all data via SearchUiState
 * and uses LocalSearchActionHandler for user actions.
 *
 * STATE MANAGEMENT:
 * - uiState: Contains all display data (query, results, provider config)
 * - onQueryChange: Emits when user types in search field
 * - onDismiss: Emits when dialog should close
 * - Actions: Handled via LocalSearchActionHandler (CompositionLocal)
 *
 * @param uiState Current search state from ViewModel
 * @param onQueryChange Called when user types in search field
 * @param onDismiss Called when dialog should close
 */
@Composable
fun AppSearchDialog(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    /**
     * Get the action handler from CompositionLocal.
     * This allows us to emit actions without prop drilling.
     */
    val actionHandler = LocalSearchActionHandler.current
    
    /**
     * FocusRequester allows us to programmatically request focus
     * on the text field when the dialog opens. This provides a
     * better user experience by immediately showing the keyboard.
     */
    val focusRequester = remember { FocusRequester() }
    
    /**
     * LocalWindowInfo provides information about the window state.
     * We use this to detect when the Dialog's window has received focus
     * from the Android OS. This is critical because Dialogs are rendered
     * in a separate window, and requesting focus before that window is
     * focused by the OS will be silently ignored.
     */
    val windowInfo = LocalWindowInfo.current

    /**
     * BackHandler intercepts the system back button.
     * When pressed, it calls onDismiss to close the dialog
     * instead of navigating back in the activity.
     */
    BackHandler { onDismiss() }

    /**
     * Dialog is the main container for the search UI.
     *
     * PROPERTIES:
     * - usePlatformDefaultWidth = false: Allows custom sizing
     * - decorFitsSystemWindows = true: Properly handles window insets
     */
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        /**
         * Surface provides the background and elevation for the dialog.
         * It uses the theme's surface color and has rounded corners
         * for a modern, card-like appearance.
         *
         * SIZE:
         * - 90% of screen width
         * - 80% of screen height
         * - Plus padding for system bars (IME, navigation, status)
         */
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding(),
            shape = RoundedCornerShape(CornerRadius.large),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Spacing.smallMedium
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                /**
                 * SearchTextFieldWithIndicator is the search input field
                 * with a color-coded indicator bar showing the active mode.
                 */
                SearchTextFieldWithIndicator(
                    searchQuery = uiState.query,
                    onSearchQueryChange = onQueryChange,
                    focusRequester = focusRequester,
                    activeProviderConfig = uiState.activeProviderConfig,
                    placeholderText = uiState.placeholderText,
                    onLaunchFirstResult = {
                        uiState.results.firstOrNull()?.let { result ->
                            actionHandler(SearchResultAction.Tap(result))
                        }
                    },
                    onClear = { onQueryChange("") }
                )

                /**
                 * Loading indicator shown when a search is in progress.
                 * This provides visual feedback to the user that the app is
                 * actively searching, especially important for slower providers
                 * like file search. Without this, users might think the app
                 * is frozen or unresponsive.
                 */
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.mediumLarge)
                    )
                }

                /**
                 * Show either the empty state or results list.
                 * The decision is based on whether results are empty.
                 */
                if (uiState.results.isEmpty()) {
                    EmptyState(
                        searchQuery = uiState.query,
                        activeProvider = uiState.activeProviderConfig,
                        prefixHint = uiState.prefixHint
                    )
                } else {
                    /**
                     * SearchResultsList handles the display of results.
                     * It automatically chooses between grid and list layouts.
                     * Actions are handled via LocalSearchActionHandler.
                     */
                    SearchResultsList(
                        results = uiState.results,
                        activeProviderConfig = uiState.activeProviderConfig
                    )
                }
            }
        }
    }

    /**
     * LaunchedEffect runs when the dialog is composed.
     * 
     * WHY WE CAN'T JUST CALL requestFocus() IMMEDIATELY:
     * Dialogs in Compose are rendered in a separate window. When LaunchedEffect
     * runs immediately upon composition, the new Dialog window hasn't actually
     * received focus from the Android OS yet. If we ask the TextField to focus
     * before its parent window is focused, the system simply ignores the request.
     * 
     * THE OLD (HACKY) APPROACH:
     * Previously, we used delay(10) to give the OS "enough time" to focus the window.
     * However, this is unreliable - 10ms might be enough on some devices but not others,
     * especially on slower devices or when the system is under load.
     * 
     * THE PROPER SOLUTION:
     * We use snapshotFlow to convert windowInfo.isWindowFocused into a Flow.
     * The .first { it } call suspends the coroutine until the window is actually focused.
     * This gives us the EXACT moment when focus is ready, with no guesswork involved.
     * 
     * This approach is:
     * - Reliable: Works on all devices regardless of speed
     * - Efficient: No unnecessary delays, focuses as soon as possible
     * - Correct: Uses the proper Compose/Android APIs
     */
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .first { isWindowFocused -> isWindowFocused }
        
        focusRequester.requestFocus()
    }
}

/**
 * SearchTextFieldWithIndicator - Search input with mode indicator bar.
 *
 * This composable provides:
 * 1. An OutlinedTextField for search input
 * 2. A color-coded indicator bar showing the active search mode
 * 3. Provider info text when a special mode is active
 *
 * VISUAL HIERARCHY:
 * ┌────────────────────────────────────┐
 * │ [icon] [Search field....] [X]     │
 * ├────────────────────────────────────┤
 * │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← Indicator bar (colored)
 * │ [icon] Provider: description       │ ← Mode info (when active)
 * └────────────────────────────────────┘
 *
 * @param searchQuery Current text in the search field
 * @param onSearchQueryChange Callback when text changes
 * @param focusRequester FocusRequester for the text field
 * @param activeProviderConfig Current search provider (null for default app search)
 * @param placeholderText Placeholder text to show when field is empty
 * @param onLaunchFirstResult Callback when user presses "Done" on keyboard
 * @param onClear Callback when user taps the clear button
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
    /**
     * Animate the indicator color when the provider changes.
     * This creates a smooth visual transition between search modes.
     */
    val indicatorColor by animateColorAsState(
        targetValue = activeProviderConfig?.color ?: MaterialTheme.colorScheme.primary,
        label = "indicator_color"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        /**
         * OutlinedTextField is the main search input.
         *
         * FEATURES:
         * - Leading icon: Shows the active provider's icon (or default search)
         * - Trailing icon: Clear button (only shown when text exists)
         * - Single line: Prevents multiline input
         * - ImeAction.Done: Shows "Done" button on keyboard
         */
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.mediumLarge)
                .padding(top = Spacing.mediumLarge)
                .focusRequester(focusRequester),
            placeholder = { Text(placeholderText) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLaunchFirstResult() }),
            leadingIcon = {
                /**
                 * Show the active provider's icon when a special mode is active.
                 * The icon is tinted with the provider's color for visual consistency.
                 */
                activeProviderConfig?.let { config ->
                    Icon(
                        imageVector = config.icon,
                        contentDescription = config.name,
                        tint = config.color
                    )
                }
            },
            trailingIcon = {
                /**
                 * Clear button only appears when there's text to clear.
                 * This reduces visual clutter when the field is empty.
                 */
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

        /**
         * Indicator bar below the text field.
         *
         * PURPOSE:
         * - Provides a visual cue for the active search mode
         * - Height changes based on whether a special mode is active
         * - Color animates smoothly when switching modes
         *
         * The slightly taller bar (4dp vs 2dp) when a provider is active
         * draws more attention to the active mode.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.mediumLarge)
                .padding(top = Spacing.small)
                .height(if (activeProviderConfig != null) Spacing.small else Spacing.extraSmall)
                .clip(RoundedCornerShape(CornerRadius.extraSmall))
                .background(indicatorColor)
        )

        /**
         * Provider info row - only shown when a special mode is active.
         *
         * This provides additional context about what the current mode does,
         * helping users understand the different search options.
         */
        if (activeProviderConfig != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.mediumLarge)
                    .padding(top = Spacing.smallMedium, bottom = Spacing.smallMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = activeProviderConfig.icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.extraSmall),
                    tint = activeProviderConfig.color
                )
                Spacer(modifier = Modifier.width(Spacing.smallMedium))
                Text(
                    text = "${activeProviderConfig.name}: ${activeProviderConfig.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = activeProviderConfig.color
                )
            }
        } else {
            /**
             * When no special mode is active, add spacing to maintain
             * consistent layout height between mode switches.
             */
            Spacer(modifier = Modifier.height(Spacing.smallMedium))
        }
    }
}
