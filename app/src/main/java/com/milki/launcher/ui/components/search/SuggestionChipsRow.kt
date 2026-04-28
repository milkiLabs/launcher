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

@Composable
fun SuggestionChipsRow(
    title: String,
    sources: List<SearchSource>,
    defaultSourceId: String?,
    text: String,
    urlResult: UrlSearchResult?,
    emailAddress: String?,
    onSearchWithSource: (SearchSource, String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onOpenInApp: (() -> Unit)?,
    onComposeEmail: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val hasUrlResult = urlResult != null
    val hasEmail = emailAddress != null
    val hasSources = sources.isNotEmpty()

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
            when {
                hasUrlResult -> {
                    val url = urlResult!!
                    if (url.handlerApp != null) {
                        AssistChip(
                            onClick = { onOpenInApp?.invoke() },
                            label = { Text(url.handlerApp.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Filled.Language, null, Modifier.size(IconSize.small))
                            }
                        )
                        AssistChip(
                            onClick = { onOpenInBrowser(url.url) },
                            label = { Text("Open in browser", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(IconSize.small))
                            }
                        )
                    } else {
                        AssistChip(
                            onClick = { onOpenInBrowser(url.url) },
                            label = { Text("Open in browser", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Filled.Language, null, Modifier.size(IconSize.small))
                            }
                        )
                    }
                }
                hasEmail -> {
                    AssistChip(
                        onClick = { onComposeEmail?.invoke(emailAddress!!) },
                        label = { Text("Email $emailAddress", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(Icons.Filled.Email, null, Modifier.size(IconSize.small))
                        }
                    )
                }
                else -> {
                    sources.forEachIndexed { index, source ->
                        val isDefault = source.id == defaultSourceId
                        val accentColor = remember(source.accentColorHex) {
                            parseColor(source.accentColorHex)
                        }
                        val encodedText = remember(text) { Uri.encode(text) }
                        val searchUrl = source.buildUrl(encodedText)

                        if (isDefault) {
                            ElevatedFilterChip(
                                selected = true,
                                onClick = { onSearchWithSource(source, searchUrl) },
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
                                onClick = { onSearchWithSource(source, searchUrl) },
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

        when {
            hasUrlResult -> {
                Text(
                    text = urlResult!!.displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.small)
                )
            }
            hasEmail == false && hasUrlResult == false && text.isNotBlank() -> {
                Text(
                    text = text,
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

private fun parseColor(hex: String): Color? {
    return runCatching {
        val n = hex.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        if (!n.matches(Regex("^#[0-9A-F]{6}$"))) null
        else Color(red = n.substring(1, 3).toInt(16), green = n.substring(3, 5).toInt(16), blue = n.substring(5, 7).toInt(16))
    }.getOrNull()
}