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
 * 4. Applies clickable modifier
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.clickable
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
 * - Click handling
 * - Optional trailing content
 *
 * PARAMETERS:
 * @param headlineText The main text to display (e.g., "Search Google", "John Doe")
 * @param supportingText Optional secondary text (e.g., "Google", "555-1234")
 * @param leadingIcon The icon to show on the left (e.g., Search, Person, YouTube)
 * @param accentColor Optional color for the icon (falls back to theme primary)
 * @param trailingContent Optional composable for the right side (e.g., call button)
 * @param onClick Called when the user taps this item
 * @param modifier Optional modifier for external customization
 *
 * DESIGN DECISIONS:
 * - Leading icon is always tinted with accent color for visual consistency
 * - Supporting text uses onSurfaceVariant color (Material Design standard for secondary text)
 * - The entire item is clickable (not just parts of it)
 * - Trailing content is optional (not all results need it)
 *
 * ACCESSIBILITY:
 * - The entire item is a single clickable target (easier to tap)
 * - Icon contentDescription is null because the text provides context
 * - Screen readers will read the headline and supporting text
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
 * // Contact result with trailing icon
 * SearchResultListItem(
 *     headlineText = contact.name,
 *     supportingText = contact.phoneNumber,
 *     leadingIcon = Icons.Default.Person,
 *     accentColor = Color.Green,
 *     trailingContent = {
 *         Icon(
 *             imageVector = Icons.Default.Call,
 *             contentDescription = "Call",
 *             tint = Color.Green.copy(alpha = 0.7f)
 *         )
 *     },
 *     onClick = { callContact() }
 * )
 * ```
 */
@Composable
fun SearchResultListItem(
    headlineText: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    accentColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    /**
     * Determine the icon color.
     *
     * If an accent color is provided (e.g., from the active search provider),
     * use it. Otherwise, fall back to the theme's primary color.
     *
     * This pattern appears 4+ times in the original code and is now
     * centralized here.
     *
     * WHY THIS MATTERS:
     * - Different search providers have different brand colors
     * - YouTube results should have red icons
     * - Web search results should have blue icons
     * - Contact results should have green icons
     * - But if no color is specified, we need a sensible default
     */
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    /**
     * Material3 ListItem provides the standard list item layout.
     *
     * It handles:
     * - Proper spacing between leading, content, and trailing
     * - Correct text alignment
     * - Touch ripple effects
     * - Accessibility
     *
     * We configure it with our standardized styling.
     */
    ListItem(
        /**
         * Headline content is the main text.
         * We wrap it in a Text composable with default styling.
         */
        headlineContent = {
            Text(text = headlineText)
        },

        /**
         * Supporting content is optional secondary text.
         * Only shown if supportingText is provided.
         *
         * We use:
         * - bodySmall typography (smaller than headline)
         * - onSurfaceVariant color (Material Design standard for secondary text)
         *
         * This creates a clear visual hierarchy:
         * - Headline is prominent
         * - Supporting text is subtle
         */
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },

        /**
         * Leading content is the icon on the left.
         *
         * The icon is tinted with the accent color to:
         * - Match the search provider's brand
         * - Create visual consistency
         * - Provide a color-coded system (blue = web, red = YouTube, etc.)
         *
         * contentDescription is null because:
         * - The headline text provides context
         * - Screen readers will read the text
         * - Adding a description would be redundant
         */
        leadingContent = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconColor
            )
        },

        /**
         * Trailing content is optional.
         * Used for things like:
         * - Call button on contact results
         * - File type indicator on file results
         * - Any other right-side content
         *
         * The caller is responsible for styling this content.
         */
        trailingContent = trailingContent,

        /**
         * Apply the clickable modifier to make the entire item tappable.
         *
         * This is better than making individual parts clickable because:
         * - Larger touch target (easier to tap)
         * - Consistent behavior (whole item responds)
         * - Better accessibility (single focusable element)
         *
         * We apply the caller's modifier first, then clickable.
         * This allows the caller to add padding, background, etc.
         */
        modifier = modifier.clickable { onClick() }
    )
}
