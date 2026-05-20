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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing

private const val SEARCH_DIALOG_SCRIM_ALPHA = 0.16f
private const val SEARCH_DIALOG_MAX_WIDTH_DP = 720

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
    val showLoadingIndicator = rememberDelayedSearchLoadingIndicator(uiState.isLoading)
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
