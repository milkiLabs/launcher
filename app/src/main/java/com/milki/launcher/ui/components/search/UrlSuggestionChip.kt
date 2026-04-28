package com.milki.launcher.ui.components.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

@Composable
fun UrlSuggestionChipRow(
    urlResult: UrlSearchResult,
    onOpenInBrowser: () -> Unit,
    onOpenInApp: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val hasHandlerApp = urlResult.handlerApp != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium)
    ) {
        Text(
            text = "URL detected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.small)
        )

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (hasHandlerApp) {
                val handlerApp = urlResult.handlerApp
                AssistChip(
                    onClick = { onOpenInApp?.invoke() },
                    label = {
                        Text(
                            text = handlerApp.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.small)
                        )
                    },
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(Spacing.small))

                AssistChip(
                    onClick = onOpenInBrowser,
                    label = {
                        Text(
                            text = "Open in browser",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.small)
                        )
                    },
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                AssistChip(
                    onClick = onOpenInBrowser,
                    label = {
                        Text(
                            text = "Open in browser",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.small)
                        )
                    },
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Text(
            text = urlResult.displayUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.small)
        )
    }
}