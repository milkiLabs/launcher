/**
 * SuggestedActionsChipRow.kt - Horizontal chip row for one-tap search shortcuts
 *
 * Shown at the bottom of the search dialog when the user is typing (query is not blank)
 * and no provider prefix is active.
 *
 * DESIGN:
 * - One chip per enabled SearchSource (from user-configurable list in Settings)
 * - Chips are horizontally scrollable so they fit in a single row
 * - The default/primary source chip uses a filled style (ElevatedFilterChip selected)
 *   so it is visually distinct from the rest (AssistChip)
 * - Each chip is tinted with the source's accentColorHex
 * - Tapping a chip immediately opens the browser with the source URL for the current query
 *
 * MUTUAL EXCLUSIVITY WITH CLIPBOARD CHIP:
 * - This row shows when query is NOT BLANK (user is actively typing)
 * - Clipboard chip shows when query is BLANK
 * - They never appear simultaneously
 */
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
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Horizontal scrollable row of suggested search action chips.
 *
 * @param sources Ordered list of sources — the first one is treated as the default.
 * @param query   The current search query text to embed in the search URL.
 * @param onOpenUrl Callback invoked with the final search URL when a chip is tapped.
 */
@Composable
fun SuggestedActionsChipRow(
    sources: List<SearchSource>,
    query: String,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium)
    ) {
        Text(
            text = "Suggested action",
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
            sources.forEachIndexed { index, source ->
                val isDefault = index == 0
                SuggestedActionChip(
                    source = source,
                    isDefault = isDefault,
                    onClick = {
                        val encodedQuery = Uri.encode(query)
                        onOpenUrl(source.buildUrl(encodedQuery))
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestedActionChip(
    source: SearchSource,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    val accentColor = remember(source.accentColorHex) {
        parseSearchSourceColor(source.accentColorHex)
    }

    if (isDefault) {
        // Filled/elevated chip for the default engine — visually prominent
        ElevatedFilterChip(
            selected = true,
            onClick = onClick,
            label = {
                Text(
                    text = source.name,
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
            colors = FilterChipDefaults.elevatedFilterChipColors(
                selectedContainerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = if (accentColor != null)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = if (accentColor != null)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    } else {
        // Outlined assist chip for secondary sources — subtler
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    text = source.name,
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
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = BorderStroke(
                width = 1.dp,
                color = accentColor?.copy(alpha = 0.5f) ?: MaterialTheme.colorScheme.outline
            )
        )
    }
}

/**
 * Parses #RRGGBB hex into Compose Color.
 * Returns null for invalid input, letting callers fall back to theme colors.
 */
private fun parseSearchSourceColor(hex: String): Color? {
    return runCatching {
        val normalized = hex.trim().uppercase().let {
            if (it.startsWith("#")) it else "#$it"
        }
        if (!normalized.matches(Regex("^#[0-9A-F]{6}$"))) return null
        Color(
            red = normalized.substring(1, 3).toInt(16),
            green = normalized.substring(3, 5).toInt(16),
            blue = normalized.substring(5, 7).toInt(16)
        )
    }.getOrNull()
}
