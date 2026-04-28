package com.milki.launcher.ui.components.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.domain.search.ClipboardSuggestion
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

@Composable
fun ClipboardSuggestionChip(
    suggestion: ClipboardSuggestion,
    onSearchTextInBrowser: (String) -> Unit,
    onOpenUrl: (UrlSearchResult) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onComposeEmail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (suggestion) {
        is ClipboardSuggestion.OpenUrl -> {
            UrlSuggestionRow(
                urlResult = suggestion.urlResult,
                onOpenInBrowser = { onOpenInBrowser(suggestion.urlResult.url) },
                onOpenInApp = { onOpenUrl(suggestion.urlResult) },
                title = "From clipboard",
                modifier = modifier
            )
        }
        is ClipboardSuggestion.ComposeEmail -> {
            Column(
                modifier = modifier
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
                    onClick = { onComposeEmail(suggestion.emailAddress) },
                    label = {
                        Text(
                            text = "Email ${suggestion.emailAddress}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.small)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is ClipboardSuggestion.SearchText -> {
            Column(
                modifier = modifier
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
                    onClick = { onSearchTextInBrowser(suggestion.queryText) },
                    label = {
                        Text(
                            text = suggestion.queryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.small)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Search with default source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.small)
                )
            }
        }
    }
}