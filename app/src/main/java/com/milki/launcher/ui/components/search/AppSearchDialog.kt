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
 * - SearchResultListItem.kt: Shared list row for app-like result types
 * - SearchResultContactItem.kt: Contact result row
 * - SearchResultFileItem.kt: File result row
 * - SearchResultPermissionItem.kt: Permission prompt result row
 * - SearchResultUrlItem.kt: Generic URL result row
 * - SearchResultWebItem.kt: Web search result row
 * - SearchResultYouTubeItem.kt: YouTube search result row
 * - SearchResultsEmptyState.kt: Empty-state UI when no results are available
 * - AppGridItem.kt: Grid item for displaying apps
 * - AppListItem.kt: List item for displaying apps
 */

package com.milki.launcher.ui.components.search

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.search.ClipboardSuggestion
import com.milki.launcher.domain.search.QuerySuggestion
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlinx.coroutines.delay

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
    val focusManager = LocalFocusManager.current

    /**
     * Software keyboard controller lets us explicitly ask Android to show
     * the IME after the text field receives focus.
     *
     * This is not strictly required for all devices, but helps improve
     * consistency across OEM keyboard implementations.
     */
    val keyboardController = LocalSoftwareKeyboardController.current
    val showLoadingIndicator by produceState(
        initialValue = false,
        key1 = uiState.isLoading
    ) {
        if (!uiState.isLoading) {
            value = false
            return@produceState
        }

        delay(300)
        value = uiState.isLoading
    }

    /**
     * Lifecycle owner to react to resume events for focus requests.
     */
    val lifecycleOwner = LocalLifecycleOwner.current

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
     * - decorFitsSystemWindows = false: We handle system/IME insets manually
     */
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .union(WindowInsets.ime)
                            .only(WindowInsetsSides.Bottom)
                    )
                    .padding(
                        start = Spacing.mediumLarge,
                        end = Spacing.mediumLarge,
                        top = Spacing.smallMedium,
                        bottom = Spacing.smallMedium
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .heightIn(max = maxHeight)
                        .animateContentSize(),
                    shape = RoundedCornerShape(CornerRadius.large),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = Spacing.smallMedium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .animateContentSize()
                    ) {
                        /**
                         * Shared unified search input with provider-aware visuals.
                         */
                        val providerVisual = rememberSearchProviderVisual(
                            providerId = uiState.activeProviderConfig?.providerId,
                            customAccentHex =
                                uiState.activeProviderConfig?.providerId?.let(
                                    uiState.providerAccentColorById::get
                                )
                        )
                        val indicatorColor by animateColorAsState(
                            targetValue = providerVisual?.accentColor
                                ?: MaterialTheme.colorScheme.primary,
                            label = "search_indicator_color"
                        )

                        UnifiedSearchInputField(
                            query = uiState.query,
                            onQueryChange = onQueryChange,
                            placeholderText = uiState.placeholderText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.mediumLarge)
                                .padding(top = Spacing.mediumLarge),
                            focusRequester = focusRequester,
                            leadingIcon = providerVisual?.icon ?: Icons.Default.Search,
                            leadingIconTint = providerVisual?.accentColor
                                ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            leadingIconContentDescription = uiState.activeProviderConfig?.name,
                            indicatorColor = indicatorColor,
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                uiState.results.firstOrNull()?.let { result ->
                                    actionHandler(SearchResultAction.Tap(result))
                                }
                            },
                            onClear = { onQueryChange("") },
                            supportingContent = {
                                if (uiState.activeProviderConfig != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.smallMedium, bottom = Spacing.smallMedium),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = providerVisual?.icon ?: Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(IconSize.extraSmall),
                                            tint = providerVisual?.accentColor
                                                ?: MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.smallMedium))
                                        Text(
                                            text = "${uiState.activeProviderConfig.name}: ${uiState.activeProviderConfig.description}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = providerVisual?.accentColor
                                                ?: MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )

                        SearchLoadingIndicatorSlot(
                            isVisible = showLoadingIndicator,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.mediumLarge)
                        )

                        SearchDialogBody(
                            uiState = uiState,
                            onExternalAppDragStart = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.autoFocusKeyboard) {
        if (!uiState.autoFocusKeyboard) return@LaunchedEffect
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            // Ignore if layout isn't ready
        }
    }

    DisposableEffect(lifecycleOwner, uiState.autoFocusKeyboard) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.autoFocusKeyboard) {
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                    // Ignore if layout isn't ready
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(focusManager, keyboardController) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
}

@Composable
private fun SearchLoadingIndicatorSlot(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(Spacing.small),
        contentAlignment = Alignment.Center
    ) {
        if (isVisible) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SearchDialogBody(
    uiState: SearchUiState,
    onExternalAppDragStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionHandler = LocalSearchActionHandler.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.smallMedium)
    ) {
        if (uiState.results.isEmpty() && !uiState.isLoading) {
            EmptyState(
                searchQuery = uiState.query,
                activeProvider = uiState.activeProviderConfig,
                prefixHint = uiState.prefixHint,
                providerAccentColorById = uiState.providerAccentColorById,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        } else if (uiState.results.isNotEmpty()) {
            /**
             * SearchResultsList handles the display of results.
             * It automatically chooses between grid and list layouts.
             * Actions are handled via LocalSearchActionHandler.
             */
            SearchResultsList(
                results = uiState.results,
                activeProviderConfig = uiState.activeProviderConfig,
                providerAccentColorById = uiState.providerAccentColorById,
                onExternalAppDragStart = onExternalAppDragStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        }

        /**
         * Suggestion chips are intentionally placed at the bottom of
         * the dialog, below recent apps/results, as requested.
         *
         * MUTUAL EXCLUSIVITY:
         * - Clipboard chip shows when query is BLANK
         * - Query chip shows when query is NOT BLANK
         * - They never both appear at the same time
         */
        if (uiState.shouldShowClipboardSuggestion) {
            val suggestionToShow = uiState.clipboardSuggestion

            if (suggestionToShow != null) {
                ClipboardSuggestionBottomChip(
                    suggestion = suggestionToShow,
                    onSearchTextInBrowser = { queryText ->
                        val encodedQuery = Uri.encode(queryText)
                        val url = "https://www.google.com/search?q=$encodedQuery"
                        actionHandler(
                            SearchResultAction.OpenUrlInBrowser(
                                url = url
                            )
                        )
                    },
                    onOpenUrl = { urlResult ->
                        actionHandler(SearchResultAction.Tap(urlResult))
                    },
                    onComposeEmail = { emailAddress ->
                        actionHandler(SearchResultAction.ComposeEmail(emailAddress))
                    }
                )
            }
        } else if (uiState.shouldShowQuerySuggestion) {
            val suggestionToShow = uiState.querySuggestion

            if (suggestionToShow != null) {
                QuerySuggestionBottomChip(
                    suggestion = suggestionToShow,
                    onSearchWeb = { searchQuery ->
                        val encodedQuery = Uri.encode(searchQuery)
                        val url = "https://www.google.com/search?q=$encodedQuery"
                        actionHandler(
                            SearchResultAction.OpenUrlInBrowser(
                                url = url
                            )
                        )
                    },
                    onOpenUrl = { urlResult ->
                        actionHandler(SearchResultAction.Tap(urlResult))
                    },
                    onComposeEmail = { emailAddress ->
                        actionHandler(SearchResultAction.ComposeEmail(emailAddress))
                    }
                )
            }
        }
    }
}
