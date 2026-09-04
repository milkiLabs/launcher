package com.milki.launcher.ui.components.search

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milki.launcher.core.util.hexToColor
import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.search.ActionSuggestion
import com.milki.launcher.domain.search.FaviconHost
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

@Composable
fun SuggestionChipsRow(
    title: String,
    suggestion: ActionSuggestion,
    sources: List<SearchSource>,
    defaultSourceId: String?,
    actionHandler: (SearchResultAction) -> Unit,
    isOneHanded: Boolean = false,
    modifier: Modifier = Modifier,
    appPackageBySourceId: Map<String, String> = emptyMap()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SuggestionFooter(suggestion)
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            reverseLayout = isOneHanded,
            horizontalArrangement = Arrangement.spacedBy(
                Spacing.small,
                if (isOneHanded) Alignment.End else Alignment.Start
            )
        ) {
            when (suggestion) {
                is ActionSuggestion.OpenUrl -> {
                    val url = suggestion.urlResult
                    if (url.handlerApp != null) {
                        item(key = "app_handler") {
                            AssistChip(
                                onClick = { actionHandler(SearchResultAction.Tap(url)) },
                                label = { ChipLabel(url.handlerApp.label) },
                                leadingIcon = {
                                    AppIcon(
                                        packageName = url.handlerApp.packageName,
                                        size = IconSize.small
                                    )
                                }
                            )
                        }
                    }

                    item(key = "browser_fallback") {
                        val browserApp = url.browserApp
                        AssistChip(
                            onClick = { actionHandler(SearchResultAction.OpenUrlInExternalBrowser(url.url)) },
                            label = { ChipLabel(browserApp?.label ?: "Browser") },
                            leadingIcon = {
                                if (browserApp != null) {
                                    AppIcon(
                                        packageName = browserApp.packageName,
                                        size = IconSize.small
                                    )
                                } else {
                                    ChipIcon(Icons.Filled.Language)
                                }
                            }
                        )
                    }
                }
                is ActionSuggestion.ComposeEmail -> {
                    item(key = "compose_email") {
                        AssistChip(
                            onClick = { actionHandler(SearchResultAction.ComposeEmail(suggestion.emailAddress)) },
                            label = { ChipLabel("Email ${suggestion.emailAddress}") },
                            leadingIcon = { ChipIcon(Icons.Filled.Email) }
                        )
                    }
                }
                is ActionSuggestion.SearchText -> {
                    items(
                        items = sources,
                        key = { it.id }
                    ) { source ->
                        val encodedText = remember(suggestion.queryText) { Uri.encode(suggestion.queryText) }
                        val accentColor = remember(source.accentColorHex) { hexToColor(source.accentColorHex) }
                        val searchUrl = source.buildUrl(encodedText)
                        val appPackage = appPackageBySourceId[source.id]
                        // Favicon only matters when no native app matched; the
                        // icon chain falls back to monogram while loading / on fail.
                        val faviconHost = remember(source.urlTemplate) {
                            FaviconHost.fromSource(source)
                        }

                        if (source.id == defaultSourceId) {
                            DefaultSearchSourceChip(
                                source = source,
                                accentColor = accentColor,
                                searchUrl = searchUrl,
                                appPackage = appPackage,
                                faviconHost = faviconHost,
                                actionHandler = actionHandler
                            )
                        } else {
                            SecondarySearchSourceChip(
                                source = source,
                                accentColor = accentColor,
                                searchUrl = searchUrl,
                                appPackage = appPackage,
                                faviconHost = faviconHost,
                                actionHandler = actionHandler
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSearchSourceChip(
    source: SearchSource,
    accentColor: Color?,
    searchUrl: String,
    appPackage: String?,
    faviconHost: String?,
    actionHandler: (SearchResultAction) -> Unit
) {
    val shortLabel = remember(source.name) { monogramInitials(source.name) }
    ElevatedFilterChip(
        selected = true,
        onClick = { actionHandler(SearchResultAction.OpenUrlInBrowser(searchUrl)) },
        modifier = Modifier.semantics { contentDescription = source.name },
        label = {
            ChipLabel(shortLabel)
        },
        leadingIcon = {
            AppOrMonogramIcon(
                packageName = appPackage,
                fallbackText = source.name,
                accentColor = accentColor,
                faviconHost = faviconHost
            )
        },
        colors = FilterChipDefaults.elevatedFilterChipColors(
            selectedContainerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = selectedSearchChipContentColor(accentColor),
            selectedLeadingIconColor = selectedSearchChipContentColor(accentColor)
        )
    )
}

@Composable
private fun SecondarySearchSourceChip(
    source: SearchSource,
    accentColor: Color?,
    searchUrl: String,
    appPackage: String?,
    faviconHost: String?,
    actionHandler: (SearchResultAction) -> Unit
) {
    val shortLabel = remember(source.name) { monogramInitials(source.name) }
    AssistChip(
        onClick = { actionHandler(SearchResultAction.OpenUrlInBrowser(searchUrl)) },
        modifier = Modifier.semantics { contentDescription = source.name },
        label = {
            ChipLabel(shortLabel)
        },
        leadingIcon = {
            AppOrMonogramIcon(
                packageName = appPackage,
                fallbackText = source.name,
                accentColor = accentColor,
                faviconHost = faviconHost
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            leadingIconContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(
            width = 1.dp,
            color = accentColor?.copy(alpha = 0.5f) ?: MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun selectedSearchChipContentColor(accentColor: Color?): Color {
    return if (accentColor != null) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
}

@Composable
private fun SuggestionFooter(suggestion: ActionSuggestion) {
    val footerText = when (suggestion) {
        is ActionSuggestion.OpenUrl -> suggestion.urlResult.displayUrl
        is ActionSuggestion.ComposeEmail -> ""
        is ActionSuggestion.SearchText -> suggestion.queryText
    }

    if (footerText.isBlank()) return

    Text(
        text = footerText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = Spacing.smallMedium)
    )
}

@Composable
private fun ChipLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChipIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(IconSize.small)
    )
}
