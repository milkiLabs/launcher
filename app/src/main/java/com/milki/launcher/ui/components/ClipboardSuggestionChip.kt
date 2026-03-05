package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.search.ClipboardSuggestion
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Renders the single clipboard suggestion chip shown at the bottom of the search dialog.
 *
 * UX CONTRACT:
 * - Exactly one suggestion is shown.
 * - This element is rendered as the last dialog content block.
 * - Tapping the chip executes the context-specific action.
 *
 * WHY A DEDICATED COMPOSABLE:
 * Keeping this logic separate makes AppSearchDialog easier to read and isolates
 * all clipboard-specific display text and icon mapping in one educational place.
 */
@Composable
fun ClipboardSuggestionBottomChip(
    suggestion: ClipboardSuggestion,
    onSearchWithDefaultEngine: (String) -> Unit,
    onOpenUrl: (UrlSearchResult) -> Unit,
    onOpenDialer: (String) -> Unit,
    onComposeEmail: (String) -> Unit,
    onOpenMapLocation: (String) -> Unit
) {
    val content = remember(suggestion) {
        createChipContent(suggestion)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium)
    ) {
        Text(
            text = "From clipboard",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.small)
        )

        AssistChip(
            onClick = {
                when (suggestion) {
                    is ClipboardSuggestion.OpenUrl -> onOpenUrl(suggestion.urlResult)
                    is ClipboardSuggestion.DialNumber -> onOpenDialer(suggestion.phoneNumber)
                    is ClipboardSuggestion.ComposeEmail -> onComposeEmail(suggestion.emailAddress)
                    is ClipboardSuggestion.OpenMapLocation -> onOpenMapLocation(suggestion.locationQuery)
                    is ClipboardSuggestion.SearchText -> onSearchWithDefaultEngine(suggestion.queryText)
                }
            },
            label = {
                Text(
                    text = content.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = content.icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.small)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        content.supportingText?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small)
            )
        }
    }
}

/**
 * Simple immutable UI model for chip rendering.
 */
private data class ClipboardChipContent(
    val label: String,
    val supportingText: String?,
    val icon: ImageVector
)

/**
 * Maps typed suggestion data to compact chip text/icon content.
 */
private fun createChipContent(suggestion: ClipboardSuggestion): ClipboardChipContent {
    return when (suggestion) {
        is ClipboardSuggestion.OpenUrl -> {
            val actionLabel = suggestion.urlResult.handlerApp?.label?.let { handlerLabel ->
                "Open in $handlerLabel"
            } ?: "Open in browser"

            ClipboardChipContent(
                label = actionLabel,
                supportingText = suggestion.urlResult.displayUrl,
                icon = Icons.Filled.Language
            )
        }

        is ClipboardSuggestion.DialNumber -> {
            ClipboardChipContent(
                label = "Call ${suggestion.phoneNumber}",
                supportingText = null,
                icon = Icons.Filled.Call
            )
        }

        is ClipboardSuggestion.ComposeEmail -> {
            ClipboardChipContent(
                label = "Email ${suggestion.emailAddress}",
                supportingText = null,
                icon = Icons.Filled.Email
            )
        }

        is ClipboardSuggestion.OpenMapLocation -> {
            ClipboardChipContent(
                label = "Open in maps",
                supportingText = suggestion.locationQuery,
                icon = Icons.Filled.Map
            )
        }

        is ClipboardSuggestion.SearchText -> {
            ClipboardChipContent(
                label = "Search with default source",
                supportingText = suggestion.queryText,
                icon = Icons.Filled.Search
            )
        }
    }
}
