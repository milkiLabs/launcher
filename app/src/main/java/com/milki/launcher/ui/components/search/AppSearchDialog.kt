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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.util.Log
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.search.ActionSuggestion
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val SEARCH_LOADING_INDICATOR_DELAY_MS = 300L
private const val SEARCH_DIALOG_SCRIM_ALPHA = 0.16f
private const val SEARCH_DIALOG_MAX_WIDTH_DP = 720
private const val APP_SEARCH_DIALOG_LOG_TAG = "AppSearchDialog"

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
    val actionHandler = LocalSearchActionHandler.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val showLoadingIndicator by produceState(
        initialValue = false,
        key1 = uiState.isLoading
    ) {
        if (!uiState.isLoading) {
            value = false
            return@produceState
        }

        delay(SEARCH_LOADING_INDICATOR_DELAY_MS)
        value = uiState.isLoading
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler { onDismiss() }
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
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SEARCH_DIALOG_SCRIM_ALPHA))
        ) {
            SearchDialogDismissLayer(onDismiss = onDismiss)
            SearchDialogSheet(
                uiState = uiState,
                onQueryChange = onQueryChange,
                onDismiss = onDismiss,
                showLoadingIndicator = showLoadingIndicator,
                focusRequester = focusRequester,
                actionHandler = actionHandler
            )
        }
    }

    SearchDialogFocusEffects(
        autoFocusKeyboard = uiState.autoFocusKeyboard,
        focusRequester = focusRequester,
        keyboardController = keyboardController,
        lifecycleOwner = lifecycleOwner
    )

    DisposableEffect(focusManager, keyboardController) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
}

@Composable
private fun SearchDialogDismissLayer(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    )
}

@Composable
private fun SearchDialogSheet(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    showLoadingIndicator: Boolean,
    focusRequester: FocusRequester,
    actionHandler: (SearchResultAction) -> Unit
) {
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
                .widthIn(max = SEARCH_DIALOG_MAX_WIDTH_DP.dp)
                .heightIn(max = maxHeight)
                .animateContentSize(),
            shape = RoundedCornerShape(CornerRadius.large),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Spacing.smallMedium
        ) {
            SearchDialogContent(
                uiState = uiState,
                onQueryChange = onQueryChange,
                onDismiss = onDismiss,
                showLoadingIndicator = showLoadingIndicator,
                focusRequester = focusRequester,
                actionHandler = actionHandler,
                maxHeight = maxHeight
            )
        }
    }
}

@Composable
private fun SearchDialogContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    showLoadingIndicator: Boolean,
    focusRequester: FocusRequester,
    actionHandler: (SearchResultAction) -> Unit,
    maxHeight: Dp
) {
    val providerVisual = rememberSearchProviderVisual(
        providerId = uiState.activeProviderConfig?.providerId,
        customAccentHex = uiState.activeProviderConfig?.providerId?.let(
            uiState.providerAccentColorById::get
        )
    )
    val indicatorColor by animateColorAsState(
        targetValue = providerVisual?.accentColor ?: MaterialTheme.colorScheme.primary,
        label = "search_indicator_color"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .animateContentSize()
    ) {
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
                ActiveProviderSupportingContent(
                    uiState = uiState,
                    providerVisual = providerVisual
                )
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

@Composable
private fun ActiveProviderSupportingContent(
    uiState: SearchUiState,
    providerVisual: SearchProviderVisual?
) {
    val activeProvider = uiState.activeProviderConfig ?: return
    val accentColor = providerVisual?.accentColor ?: MaterialTheme.colorScheme.primary
    val providerDescription = "${activeProvider.name}: ${activeProvider.description}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = providerVisual?.icon ?: Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(IconSize.extraSmall),
            tint = accentColor
        )
        Spacer(modifier = Modifier.width(Spacing.smallMedium))
        Text(
            text = providerDescription,
            style = MaterialTheme.typography.bodySmall,
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchDialogFocusEffects(
    autoFocusKeyboard: Boolean,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    lifecycleOwner: LifecycleOwner
) {
    LaunchedEffect(autoFocusKeyboard) {
        if (autoFocusKeyboard) {
            requestDialogFocus(
                focusRequester = focusRequester,
                keyboardController = keyboardController
            )
        }
    }

    DisposableEffect(lifecycleOwner, autoFocusKeyboard) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && autoFocusKeyboard) {
                requestDialogFocus(
                    focusRequester = focusRequester,
                    keyboardController = keyboardController
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun requestDialogFocus(
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?
) {
    runCatching {
        focusRequester.requestFocus()
        keyboardController?.show()
    }.onFailure { error ->
        Log.w(APP_SEARCH_DIALOG_LOG_TAG, "Search dialog focus request skipped", error)
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
    val onSearchInBrowser: (String) -> Unit = { queryText ->
        // Use the first ordered source (default) for the clipboard chip search action
        val encodedQuery = Uri.encode(queryText)
        val url = uiState.orderedSuggestedSources.firstOrNull()
            ?.buildUrl(encodedQuery)
            ?: buildFallbackSearchUrl(queryText)
        actionHandler(
            SearchResultAction.OpenUrlInBrowser(url = url)
        )
    }
    val onOpenUrlSuggestion: (UrlSearchResult) -> Unit = { urlResult ->
        actionHandler(SearchResultAction.Tap(urlResult))
    }
    val onComposeEmailSuggestion: (String) -> Unit = { emailAddress ->
        actionHandler(SearchResultAction.ComposeEmail(emailAddress))
    }

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
         * Suggestion area at the bottom of the dialog.
         */
        val suggestionToShow = when {
            uiState.shouldShowClipboardSuggestion -> uiState.clipboardSuggestion to "From clipboard"
            uiState.shouldShowQuerySuggestion -> uiState.querySuggestion to "Suggested actions"
            else -> null
        }

        if (suggestionToShow != null) {
            val (suggestion, title) = suggestionToShow
            if (suggestion != null) {
                SuggestionChipsRow(
                    title = title,
                    suggestion = suggestion,
                    sources = uiState.orderedSuggestedSources,
                    defaultSourceId = uiState.defaultSearchSourceId,
                    actionHandler = actionHandler
                )
            }
        }
    }
}

private fun buildFallbackSearchUrl(query: String): String {
    val encodedQuery = Uri.encode(query)
    return "https://www.google.com/search?q=$encodedQuery"
}
