/**
 * SearchResultListItem.kt - Reusable wrapper for search result list items
 *
 * WHY THIS EXISTS:
 * Five different search result components (WebSearchResultItem, YouTubeSearchResultItem,
 * ContactSearchResultItem, UrlSearchResultItem, FileDocumentSearchResultItem) all follow
 * the exact same pattern:
 * - Material3 ListItem with leading icon
 * - Headline text
 * - Supporting text with onSurfaceVariant color
 * - Optional trailing content
 * - Clickable modifier
 * - Icon tinted with accent color
 *
 * This duplication leads to:
 * - Repeated code across 5+ components
 * - Inconsistent styling if one is updated but not others
 * - Maintenance burden (changes needed in multiple places)
 *
 * By extracting this pattern, we ensure:
 * - Single source of truth for search result styling
 * - Consistent appearance across all result types
 * - Easier maintenance and updates
 * - Less code to write for new result types
 *
 * USAGE:
 * ```kotlin
 * SearchResultListItem(
 *     headlineText = "Search Google",
 *     supportingText = "Google",
 *     leadingIcon = Icons.Default.Search,
 *     accentColor = Color.Blue,
 *     onClick = { /* handle click */ }
 * )
 * ```
 *
 * HOW IT WORKS:
 * 1. Takes common parameters (texts, icon, colors)
 * 2. Handles accent color fallback to theme primary
 * 3. Wraps Material3 ListItem with consistent styling
 * 4. Applies clickable modifier (or combinedClickable if onLongClick provided)
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SearchResultListItem displays a search result in a consistent list format.
 *
 * This component wraps Material3's ListItem and provides a standardized
 * appearance for all search result types. It handles:
 * - Icon tinting with accent colors
 * - Text styling (headline and supporting)
 * - Click handling (single tap and optional long-press)
 * - Optional trailing content
 *
 * PARAMETERS:
 * @param headlineText The main text to display (e.g., "Search Google", "John Doe")
 * @param leadingIcon The icon to show on the left (e.g., Search, Person, YouTube)
 * @param onClick Called when the user taps this item
 * @param modifier Optional modifier for external customization
 * @param supportingText Optional secondary text (e.g., "Google", "555-1234")
 * @param accentColor Optional color for the icon (falls back to theme primary)
 * @param trailingContent Optional composable for the right side (e.g., call button)
 * @param onLongClick Optional callback for long-press (e.g., to pin item)
 *
 * Example usage:
 * ```kotlin
 * // Web search result
 * SearchResultListItem(
 *     headlineText = "Search Google",
 *     supportingText = "Google",
 *     leadingIcon = Icons.Default.Search,
 *     accentColor = Color.Blue,
 *     onClick = { openWebSearch() }
 * )
 *
 * // File result with long-press to pin
 * SearchResultListItem(
 *     headlineText = "document.pdf",
 *     supportingText = "Downloads • 1.2 MB",
 *     leadingIcon = Icons.Outlined.PictureAsPdf,
 *     onClick = { openFile() },
 *     onLongClick = { pinFile() }
 * )
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultListItem(
    headlineText: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    accentColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    val clickModifier = if (onLongClick != null) {
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        modifier.clickable { onClick() }
    }

    ListItem(
        headlineContent = {
            Text(text = headlineText)
        },

        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },

        leadingContent = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconColor
            )
        },

        trailingContent = trailingContent,

        modifier = clickModifier
    )
}
