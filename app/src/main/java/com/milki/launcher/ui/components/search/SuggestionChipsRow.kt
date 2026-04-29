package com.milki.launcher.ui.components.search

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.model.UrlSearchResult
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

import com.milki.launcher.domain.search.ActionSuggestion
import com.milki.launcher.presentation.search.SearchResultAction

@Composable
fun SuggestionChipsRow(
    title: String,
    suggestion: ActionSuggestion,
    sources: List<SearchSource>,
    defaultSourceId: String?,
    actionHandler: (SearchResultAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.small)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            when (suggestion) {
                is ActionSuggestion.OpenUrl -> {
                    val url = suggestion.urlResult
                    if (url.handlerApp != null) {
                        AssistChip(
                            onClick = { actionHandler(SearchResultAction.Tap(url)) },
                            label = { Text(url.handlerApp.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Filled.Language, null, Modifier.size(IconSize.small))
                            }
                        )
                        AssistChip(
                            onClick = { actionHandler(SearchResultAction.OpenUrlInExternalBrowser(url.url)) },
                            label = { Text("Open in browser", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(IconSize.small))
                            }
                        )
                    } else {
                        AssistChip(
                            onClick = { actionHandler(SearchResultAction.OpenUrlInExternalBrowser(url.url)) },
                            label = { Text("Open in browser", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Filled.Language, null, Modifier.size(IconSize.small))
                            }
                        )
                    }
                }
                is ActionSuggestion.ComposeEmail -> {
                    AssistChip(
                        onClick = { actionHandler(SearchResultAction.ComposeEmail(suggestion.emailAddress)) },
                        label = { Text("Email ${suggestion.emailAddress}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(Icons.Filled.Email, null, Modifier.size(IconSize.small))
                        }
                    )
                }
                is ActionSuggestion.SearchText -> {
                    sources.forEach { source ->
                        val isDefault = source.id == defaultSourceId
                        val accentColor = remember(source.accentColorHex) {
                            parseColor(source.accentColorHex)
                        }
                        val encodedText = remember(suggestion.queryText) { Uri.encode(suggestion.queryText) }
                        val searchUrl = source.buildUrl(encodedText)

                        if (isDefault) {
                            ElevatedFilterChip(
                                selected = true,
                                onClick = { actionHandler(SearchResultAction.OpenUrlInBrowser(searchUrl)) },
                                label = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, null, Modifier.size(IconSize.small))
                                },
                                colors = FilterChipDefaults.elevatedFilterChipColors(
                                    selectedContainerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = if (accentColor != null) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = if (accentColor != null) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        } else {
                            AssistChip(
                                onClick = { actionHandler(SearchResultAction.OpenUrlInBrowser(searchUrl)) },
                                label = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, null, Modifier.size(IconSize.small))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    leadingIconContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = BorderStroke(1.dp, accentColor?.copy(alpha = 0.5f) ?: MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }
            }
        }

        val footerText = when (suggestion) {
            is ActionSuggestion.OpenUrl -> suggestion.urlResult.displayUrl
            is ActionSuggestion.ComposeEmail -> "" // Handled in chip
            is ActionSuggestion.SearchText -> suggestion.queryText
        }

        if (footerText.isNotBlank()) {
            Text(
                text = footerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small)
            )
        }
    }
}

private fun parseColor(hex: String): Color? {
    return runCatching {
        val n = hex.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        if (!n.matches(Regex("^#[0-9A-F]{6}$"))) null
        else Color(red = n.substring(1, 3).toInt(16), green = n.substring(3, 5).toInt(16), blue = n.substring(5, 7).toInt(16))
    }.getOrNull()
}