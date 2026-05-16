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

private const val HEX_COLOR_LENGTH = 7
private const val HEX_RED_START = 1
private const val HEX_RED_END = 3
private const val HEX_GREEN_START = 3
private const val HEX_GREEN_END = 5
private const val HEX_BLUE_START = 5
private const val HEX_BLUE_END = 7
private val hexColorPattern = Regex("^#[0-9A-F]{6}$")

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
            SuggestionActionChips(
                suggestion = suggestion,
                sources = sources,
                defaultSourceId = defaultSourceId,
                actionHandler = actionHandler
            )
        }

        SuggestionFooter(suggestion)
    }
}

@Composable
private fun SuggestionActionChips(
    suggestion: ActionSuggestion,
    sources: List<SearchSource>,
    defaultSourceId: String?,
    actionHandler: (SearchResultAction) -> Unit
) {
    when (suggestion) {
        is ActionSuggestion.OpenUrl -> OpenUrlSuggestionChips(
            url = suggestion.urlResult,
            actionHandler = actionHandler
        )
        is ActionSuggestion.ComposeEmail -> ComposeEmailSuggestionChip(
            emailAddress = suggestion.emailAddress,
            actionHandler = actionHandler
        )
        is ActionSuggestion.SearchText -> SearchTextSuggestionChips(
            queryText = suggestion.queryText,
            sources = sources,
            defaultSourceId = defaultSourceId,
            actionHandler = actionHandler
        )
    }
}

@Composable
private fun OpenUrlSuggestionChips(
    url: UrlSearchResult,
    actionHandler: (SearchResultAction) -> Unit
) {
    if (url.handlerApp != null) {
        AssistChip(
            onClick = { actionHandler(SearchResultAction.Tap(url)) },
            label = {
                ChipLabel(url.handlerApp.label)
            },
            leadingIcon = {
                ChipIcon(Icons.Filled.Language)
            }
        )
    }

    AssistChip(
        onClick = { actionHandler(SearchResultAction.OpenUrlInExternalBrowser(url.url)) },
        label = {
            ChipLabel("Open in browser")
        },
        leadingIcon = {
            val icon = if (url.handlerApp != null) {
                Icons.AutoMirrored.Filled.OpenInNew
            } else {
                Icons.Filled.Language
            }
            ChipIcon(icon)
        }
    )
}

@Composable
private fun ComposeEmailSuggestionChip(
    emailAddress: String,
    actionHandler: (SearchResultAction) -> Unit
) {
    AssistChip(
        onClick = { actionHandler(SearchResultAction.ComposeEmail(emailAddress)) },
        label = {
            ChipLabel("Email $emailAddress")
        },
        leadingIcon = {
            ChipIcon(Icons.Filled.Email)
        }
    )
}

@Composable
private fun SearchTextSuggestionChips(
    queryText: String,
    sources: List<SearchSource>,
    defaultSourceId: String?,
    actionHandler: (SearchResultAction) -> Unit
) {
    val encodedText = remember(queryText) { Uri.encode(queryText) }

    sources.forEach { source ->
        val accentColor = remember(source.accentColorHex) {
            parseColor(source.accentColorHex)
        }
        val searchUrl = source.buildUrl(encodedText)

        if (source.id == defaultSourceId) {
            DefaultSearchSourceChip(
                source = source,
                accentColor = accentColor,
                searchUrl = searchUrl,
                actionHandler = actionHandler
            )
        } else {
            SecondarySearchSourceChip(
                source = source,
                accentColor = accentColor,
                searchUrl = searchUrl,
                actionHandler = actionHandler
            )
        }
    }
}

@Composable
private fun DefaultSearchSourceChip(
    source: SearchSource,
    accentColor: Color?,
    searchUrl: String,
    actionHandler: (SearchResultAction) -> Unit
) {
    ElevatedFilterChip(
        selected = true,
        onClick = { actionHandler(SearchResultAction.OpenUrlInBrowser(searchUrl)) },
        label = {
            ChipLabel(source.name)
        },
        leadingIcon = {
            ChipIcon(Icons.Filled.Search)
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
    actionHandler: (SearchResultAction) -> Unit
) {
    AssistChip(
        onClick = { actionHandler(SearchResultAction.OpenUrlInBrowser(searchUrl)) },
        label = {
            ChipLabel(source.name)
        },
        leadingIcon = {
            ChipIcon(Icons.Filled.Search)
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
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = Spacing.small)
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

private fun parseColor(hex: String): Color? {
    return runCatching {
        val n = hex.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        if (n.length != HEX_COLOR_LENGTH || !n.matches(hexColorPattern)) {
            null
        } else {
            Color(
                red = n.substring(HEX_RED_START, HEX_RED_END).toInt(radix = 16),
                green = n.substring(HEX_GREEN_START, HEX_GREEN_END).toInt(radix = 16),
                blue = n.substring(HEX_BLUE_START, HEX_BLUE_END).toInt(radix = 16)
            )
        }
    }.getOrNull()
}
