/**
 * SearchResultItems.kt - Individual search result item composables
 *
 * This file contains the composable functions that render individual
 * search result items. Each result type has its own dedicated composable
 * that handles its specific layout and visual presentation.
 *
 * SUPPORTED RESULT TYPES:
 * - WebSearchResult → WebSearchResultItem (Google, DuckDuckGo searches)
 * - YouTubeSearchResult → YouTubeSearchResultItem (YouTube video searches)
 * - ContactSearchResult → ContactSearchResultItem (phone contacts)
 * - PermissionRequestResult → PermissionRequestItem (permission prompts)
 *
 * ARCHITECTURE:
 * These are "dumb" UI components - they only display what they're given.
 * - No business logic in this file
 * - Each component receives its data via parameters
 * - User interactions emit callbacks (onClick)
 *
 * THEMING:
 * All items accept an optional accentColor parameter that can be used
 * to color-code results based on the active search provider. If no
 * color is provided, they default to the theme's primary color.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.*

/**
 * WebSearchResultItem - Displays a web search result.
 *
 * This item represents a web search action that will open the user's
 * browser with a pre-formatted search URL. It shows:
 * - A search icon (tinted with accent color)
 * - The search query as the headline
 * - The search engine name as supporting text
 *
 * USAGE:
 * Displayed when the user uses the "s " prefix to search the web.
 * The accentColor comes from the active SearchProviderConfig.
 *
 * @param result The web search result to display
 * @param accentColor Color for the icon (defaults to primary if null)
 * @param onClick Callback when the item is clicked
 */
@Composable
fun WebSearchResultItem(
    result: WebSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    /**
     * Use the provided accent color, or fall back to the theme's primary color.
     * This allows the item to match the active search provider's color scheme.
     */
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    ListItem(
        headlineContent = { Text(text = result.title) },
        supportingContent = {
            /**
             * The supporting text shows which search engine will be used.
             * This helps users understand where they'll be directed.
             */
            Text(
                text = result.engine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            /**
             * The search icon indicates this is a web search action.
             * It's tinted with the accent color for visual consistency.
             */
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = iconColor
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * YouTubeSearchResultItem - Displays a YouTube search result.
 *
 * This item represents a YouTube search action that will open the
 * YouTube app or website with a pre-formatted search URL. It shows:
 * - A play arrow icon (tinted with accent color)
 * - The search query as the headline
 * - "YouTube" as supporting text
 *
 * USAGE:
 * Displayed when the user uses the "y " prefix to search YouTube.
 *
 * @param result The YouTube search result to display
 * @param accentColor Color for the icon (defaults to primary if null)
 * @param onClick Callback when the item is clicked
 */
@Composable
fun YouTubeSearchResultItem(
    result: YouTubeSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    ListItem(
        headlineContent = { Text(text = result.title) },
        supportingContent = {
            /**
             * Show "YouTube" as the supporting text to clearly indicate
             * where the search will be performed.
             */
            Text(
                text = "YouTube",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            /**
             * The play arrow icon is universally recognized as a video/media symbol.
             * This distinguishes YouTube results from regular web searches.
             */
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = iconColor
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * ContactSearchResultItem - Displays a contact search result.
 *
 * This item represents a contact from the user's phone book. It shows:
 * - A person icon (tinted with accent color)
 * - The contact's display name as the headline
 * - The primary phone number as supporting text (if available)
 * - A phone icon as trailing content (if phone numbers exist)
 *
 * USAGE:
 * Displayed when the user uses the "c " prefix to search contacts.
 * Requires READ_CONTACTS permission (handled by PermissionRequestResult).
 *
 * @param result The contact search result to display
 * @param accentColor Color for icons (defaults to primary if null)
 * @param onClick Callback when the item is clicked
 */
@Composable
fun ContactSearchResultItem(
    result: ContactSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    /**
     * Get the first phone number to display as supporting text.
     * Contacts may have multiple numbers; we show the primary one.
     */
    val primaryPhone = result.contact.phoneNumbers.firstOrNull()

    ListItem(
        headlineContent = { Text(text = result.contact.displayName) },
        supportingContent = {
            /**
             * Show the primary phone number if available.
             * This helps users quickly identify the right contact
             * when they have multiple contacts with similar names.
             */
            if (primaryPhone != null) {
                Text(
                    text = primaryPhone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            /**
             * The person icon indicates this is a contact.
             * In the future, we could replace this with the
             * contact's actual profile picture if available.
             */
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = iconColor
            )
        },
        trailingContent = {
            /**
             * Show a phone icon to indicate the contact can be called.
             * This is a subtle visual hint that tapping will initiate
             * a call action (or show contact details).
             *
             * We use a slightly transparent version of the accent color
             * to make it less prominent than the leading icon.
             */
            if (result.contact.phoneNumbers.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call contact",
                    tint = iconColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

/**
 * PermissionRequestItem - Displays a permission request prompt.
 *
 * This is a special result type that doesn't represent a search match,
 * but rather a prompt for the user to grant a permission. It shows:
 * - A warning icon (error color)
 * - A message explaining why the permission is needed
 * - A button to request the permission
 *
 * USAGE:
 * Displayed when a search provider requires a permission that hasn't
 * been granted (e.g., READ_CONTACTS for contact search).
 *
 * DESIGN:
 * This uses a Card with a distinct surfaceVariant background to make
 * it stand out from regular search results. The warning icon and
 * centered layout emphasize that user action is required.
 *
 * @param result The permission request result to display
 * @param accentColor Color for the action button (defaults to primary if null)
 * @param onClick Callback when the button is clicked (to request permission)
 */
@Composable
fun PermissionRequestItem(
    result: PermissionRequestResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    val buttonColor = accentColor ?: MaterialTheme.colorScheme.primary

    /**
     * Card provides a distinct container that stands out from
     * regular list items. The surfaceVariant color is slightly
     * different from the background, creating a subtle elevation effect.
     */
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        /**
         * Column centers all content horizontally.
         * This creates a focused, action-oriented layout.
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /**
             * Warning icon in error color immediately signals
             * that something requires attention.
             */
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            /**
             * The message explains what permission is needed and why.
             * It's centered for readability and uses the onSurfaceVariant
             * color for a slightly muted appearance.
             */
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            /**
             * The button uses the accent color to match the active
             * search provider, creating visual continuity.
             */
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                )
            ) {
                Text(result.buttonText)
            }
        }
    }
}

/**
 * EmptyState - Displayed when no results match the search.
 *
 * This composable is shown when the search results list is empty.
 * It provides helpful feedback and hints to the user.
 *
 * BEHAVIOR:
 * - If query is blank: Shows "No recent apps" with prefix hints
 * - If query has text: Shows "No [provider] results found"
 *
 * @param searchQuery The current search query (used to determine message)
 * @param activeProvider The current search provider (for icon and theming)
 * @param prefixHint Text showing available prefix shortcuts
 */
@Composable
fun EmptyState(
    searchQuery: String,
    activeProvider: SearchProviderConfig?,
    prefixHint: String
) {
    /**
     * Box centers the entire empty state content.
     * This ensures the message is always visible regardless of
     * the dialog size.
     */
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            /**
             * Use the active provider's icon if available,
             * otherwise fall back to the default search icon.
             * The icon is tinted with the provider's color (or default).
             */
            val icon = activeProvider?.icon ?: Icons.Default.Search
            val tint = activeProvider?.color ?: MaterialTheme.colorScheme.onSurfaceVariant

            /**
             * Large icon with reduced opacity creates a subtle,
             * non-distracting visual element.
             */
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = tint.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Choose the appropriate message based on the current state:
             * - Blank query: Invite user to start searching
             * - Active provider: Show provider-specific "no results" message
             * - Default: Generic "no apps found" message
             */
            val message = when {
                searchQuery.isBlank() -> "No recent apps\nType to search"
                activeProvider != null -> "No ${activeProvider.name.lowercase()} results found"
                else -> "No apps found"
            }

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            /**
             * If the query is blank, show the prefix hints.
             * This helps users discover the available search modes
             * (web search, contacts, YouTube, etc.).
             */
            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = prefixHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
