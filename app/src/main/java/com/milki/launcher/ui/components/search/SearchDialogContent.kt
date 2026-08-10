package com.milki.launcher.ui.components.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.milki.launcher.domain.model.SearchLayout
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.presentation.search.SearchUiState
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Content inside the search dialog sheet.
 *
 * Dialog/window behavior stays in AppSearchDialog; this file owns provider
 * visual state, result/empty rendering, and suggestion chip placement.
 */
@Composable
internal fun SearchDialogContent(
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
            .heightIn(max = maxHeight),
        verticalArrangement = Arrangement.Top
    ) {
        SearchInput(
            uiState = uiState,
            onQueryChange = onQueryChange,
            showLoadingIndicator = showLoadingIndicator,
            focusRequester = focusRequester,
            providerVisual = providerVisual,
            indicatorColor = indicatorColor,
            actionHandler = actionHandler
        )

        SearchResults(
            uiState = uiState,
            onExternalAppDragStart = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        )

        SearchSuggestion(uiState = uiState)
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
private fun SearchInput(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    showLoadingIndicator: Boolean,
    focusRequester: FocusRequester,
    providerVisual: SearchProviderVisual?,
    indicatorColor: androidx.compose.ui.graphics.Color,
    actionHandler: (SearchResultAction) -> Unit
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
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    onExternalAppDragStart: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            SearchResultsList(
                results = uiState.results,
                activeProviderConfig = uiState.activeProviderConfig,
                providerAccentColorById = uiState.providerAccentColorById,
                onExternalAppDragStart = onExternalAppDragStart,
                searchLayout = uiState.searchLayout,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun SearchSuggestion(uiState: SearchUiState) {
    val actionHandler = LocalSearchActionHandler.current
    val suggestionToShow = when {
        uiState.shouldShowClipboardSuggestion ->
            uiState.clipboardSuggestion to "From clipboard"
        uiState.shouldShowQuerySuggestion ->
            uiState.querySuggestion to "Suggested actions"
        else -> null
    }

    val (suggestion, title) = suggestionToShow ?: return
    suggestion ?: return

    SuggestionChipsRow(
        title = title,
        suggestion = suggestion,
        sources = uiState.orderedSuggestedSources,
        defaultSourceId = uiState.defaultSearchSourceId,
        actionHandler = actionHandler,
        isOneHanded = uiState.searchLayout == SearchLayout.ONE_HANDED
    )
}
