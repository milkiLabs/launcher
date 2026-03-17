/**
 * QuerySuggestionChip.kt - Renders a suggestion chip for the current search query
 *
 * This composable displays a single actionable suggestion based on what the user
 * is currently typing in the search field. It appears at the bottom of the search
 * dialog, replacing the clipboard chip when the user starts typing.
 *
 * MUTUAL EXCLUSIVITY WITH CLIPBOARD CHIP:
 * - Clipboard chip shows when query is BLANK (user hasn't typed anything)
 * - Query chip shows when query is NOT BLANK (user is actively typing)
 * - They never both appear at the same time
 *
 * TYPES OF SUGGESTIONS:
 * - OpenUrl: "Open in [App]" for URLs (e.g., "youtube.com" → "Open in YouTube")
 * - DialNumber: "Call [number]" for phone numbers
 * - ComposeEmail: "Email [address]" for email addresses
 * - OpenMapLocation: "Open in maps" for location-like text
 * - SearchWeb: "Search with Google" for plain text queries
 *
 * WHY A DEDICATED COMPOSABLE:
 * Keeping this logic separate makes AppSearchDialog easier to read and isolates
 * all query-specific display text and icon mapping in one educational place.
 */
package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.search.QuerySuggestion
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Renders the single query suggestion chip shown at the bottom of the search dialog.
 *
 * UX CONTRACT:
 * - Exactly one suggestion is shown.
 * - This element is rendered as the last dialog content block.
 * - Tapping the chip executes the context-specific action.
 * - This chip appears when the user is typing (query is not blank).
 * - The clipboard chip appears when the query is blank.
 * - They are mutually exclusive.
 *
 * @param suggestion The query-derived suggestion to display
 * @param onSearchWeb Callback to search the query on the web
 * @param onOpenUrl Callback to open a URL in its handler app
 * @param onOpenDialer Callback to open the dialer with a phone number
 * @param onComposeEmail Callback to compose an email
 * @param onOpenMapLocation Callback to open a location in maps
 */
@Composable
fun QuerySuggestionBottomChip(
    suggestion: QuerySuggestion,
    onSearchWeb: (String) -> Unit,
    onOpenUrl: (UrlSearchResult) -> Unit,
    onOpenDialer: (String) -> Unit,
    onComposeEmail: (String) -> Unit,
    onOpenMapLocation: (String) -> Unit
) {
    val content = remember(suggestion) {
        createQueryChipContent(suggestion)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium)
    ) {
        Text(
            text = "Suggested action",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.small)
        )

        AssistChip(
            onClick = {
                when (suggestion) {
                    is QuerySuggestion.OpenUrl -> onOpenUrl(suggestion.urlResult)
                    is QuerySuggestion.DialNumber -> onOpenDialer(suggestion.phoneNumber)
                    is QuerySuggestion.ComposeEmail -> onComposeEmail(suggestion.emailAddress)
                    is QuerySuggestion.OpenMapLocation -> onOpenMapLocation(suggestion.locationQuery)
                    is QuerySuggestion.SearchWeb -> onSearchWeb(suggestion.searchQuery)
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
 *
 * @property label The main text shown on the chip (e.g., "Open in YouTube")
 * @property supportingText Optional secondary text shown below the chip (e.g., "youtube.com")
 * @property icon The icon shown at the start of the chip
 */
private data class QueryChipContent(
    val label: String,
    val supportingText: String?,
    val icon: ImageVector
)

/**
 * Maps typed suggestion data to compact chip text/icon content.
 *
 * This function determines what text and icon to show based on the type
 * of suggestion. Each suggestion type has a specific visual representation:
 *
 * - OpenUrl: Language icon, shows "Open in [App Name]" or "Open in browser"
 * - DialNumber: Call icon, shows "Call [number]"
 * - ComposeEmail: Email icon, shows "Email [address]"
 * - OpenMapLocation: Map icon, shows "Open in maps"
 * - SearchWeb: Search icon, shows "Search with Google"
 *
 * @param suggestion The suggestion to convert to chip content
 * @return QueryChipContent with label, supporting text, and icon
 */
private fun createQueryChipContent(suggestion: QuerySuggestion): QueryChipContent {
    return when (suggestion) {
        is QuerySuggestion.OpenUrl -> {
            val actionLabel = suggestion.urlResult.handlerApp?.label?.let { handlerLabel ->
                "Open in $handlerLabel"
            } ?: "Open in browser"

            QueryChipContent(
                label = actionLabel,
                supportingText = suggestion.urlResult.displayUrl,
                icon = Icons.Filled.Language
            )
        }

        is QuerySuggestion.DialNumber -> {
            QueryChipContent(
                label = "Call ${suggestion.phoneNumber}",
                supportingText = null,
                icon = Icons.Filled.Call
            )
        }

        is QuerySuggestion.ComposeEmail -> {
            QueryChipContent(
                label = "Email ${suggestion.emailAddress}",
                supportingText = null,
                icon = Icons.Filled.Email
            )
        }

        is QuerySuggestion.OpenMapLocation -> {
            QueryChipContent(
                label = "Open in maps",
                supportingText = suggestion.locationQuery,
                icon = Icons.Filled.Map
            )
        }

        is QuerySuggestion.SearchWeb -> {
            QueryChipContent(
                label = "Search with Google",
                supportingText = suggestion.searchQuery,
                icon = Icons.Filled.Search
            )
        }
    }
}
