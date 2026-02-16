/**
 * SearchResultItems.kt - Individual search result item composables
 *
 * This file contains the composable functions that render individual
 * search result items. Each result type has its own dedicated composable
 * that handles its specific layout and visual presentation.
 *
 * REFACTORING NOTE:
 * This file has been refactored to use the SearchResultListItem wrapper component.
 * Previously, each result type duplicated the same ListItem structure.
 * Now they all use SearchResultListItem, reducing code by ~80%.
 *
 * SUPPORTED RESULT TYPES:
 * - WebSearchResult → WebSearchResultItem (Google, DuckDuckGo searches)
 * - YouTubeSearchResult → YouTubeSearchResultItem (YouTube video searches)
 * - ContactSearchResult → ContactSearchResultItem (phone contacts)
 * - UrlSearchResult → UrlSearchResultItem (direct URL opening)
 * - FileDocumentSearchResult → FileDocumentSearchResultItem (file search)
 * - PermissionRequestResult → PermissionRequestItem (permission prompts)
 *
 * ARCHITECTURE:
 * These are "dumb" UI components - they only display what they're given.
 * - No business logic in this file
 * - Each component receives its data via parameters
 * - User interactions emit callbacks (onClick)
 * - Most components are now thin wrappers around SearchResultListItem
 *
 * THEMING:
 * All items accept an optional accentColor parameter that can be used
 * to color-code results based on the active search provider. If no
 * color is provided, they default to the theme's primary color.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.*
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * WebSearchResultItem - Displays a web search result.
 *
 * This item represents a web search action that will open the user's
 * browser with a pre-formatted search URL. It shows:
 * - A search icon (tinted with accent color)
 * - The search query as the headline
 * - The search engine name as supporting text
 *
 * REFACTORING NOTE:
 * This component now uses SearchResultListItem wrapper, reducing code
 * from ~40 lines to ~10 lines while maintaining identical functionality.
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
     * Use the SearchResultListItem wrapper with web search specific values.
     * 
     * The wrapper handles:
     * - Icon tinting with accent color fallback
     * - Text styling (headline and supporting)
     * - Clickable modifier
     * - Consistent layout
     */
    SearchResultListItem(
        headlineText = result.title,
        supportingText = result.engine,
        leadingIcon = Icons.Default.Search,
        accentColor = accentColor,
        onClick = onClick
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
 * REFACTORING NOTE:
 * This component now uses SearchResultListItem wrapper, reducing code
 * from ~40 lines to ~10 lines while maintaining identical functionality.
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
    /**
     * Use the SearchResultListItem wrapper with YouTube specific values.
     * 
     * The play arrow icon is universally recognized as a video/media symbol,
     * distinguishing YouTube results from regular web searches.
     */
    SearchResultListItem(
        headlineText = result.title,
        supportingText = "YouTube",
        leadingIcon = Icons.Default.PlayArrow,
        accentColor = accentColor,
        onClick = onClick
    )
}

/**
 * UrlSearchResultItem - Displays a direct URL result.
 *
 * This item appears when the user types a valid URL (e.g., "github.com").
 * It provides a quick way to open the URL in the browser without needing
 * to use the "s " prefix for web search.
 *
 * VISUAL ELEMENTS:
 * - An info icon (indicates direct URL)
 * - "Open [url]" as the headline
 * - The full URL as supporting text
 *
 * REFACTORING NOTE:
 * This component now uses SearchResultListItem wrapper, reducing code
 * from ~35 lines to ~10 lines while maintaining identical functionality.
 *
 * USAGE:
 * Displayed when the user types a URL-like query without a provider prefix.
 * The URL is normalized with https:// if no scheme was provided.
 *
 * @param result The URL search result to display
 * @param onClick Callback when the item is clicked
 */
@Composable
fun UrlSearchResultItem(
    result: UrlSearchResult,
    onClick: () -> Unit
) {
    /**
     * Use the SearchResultListItem wrapper with URL specific values.
     * 
     * The headline shows the display URL (what the user typed).
     * The supporting text shows the full URL with scheme (what will be opened).
     * No accent color is provided, so it uses the theme's primary color.
     */
    SearchResultListItem(
        headlineText = result.title,
        supportingText = result.url,
        leadingIcon = Icons.Default.Info,
        onClick = onClick
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
 * REFACTORING NOTE:
 * This component now uses SearchResultListItem wrapper, reducing code
 * from ~60 lines to ~30 lines while maintaining identical functionality.
 * The trailing content (call icon) is still custom since it's unique to contacts.
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
    /**
     * Get the first phone number to display as supporting text.
     * Contacts may have multiple numbers; we show the primary one.
     * This helps users quickly identify the right contact when they
     * have multiple contacts with similar names.
     */
    val primaryPhone = result.contact.phoneNumbers.firstOrNull()

    /**
     * Determine the icon color for the trailing call icon.
     * We use a slightly transparent version of the accent color
     * to make it less prominent than the leading icon.
     */
    val iconColor = accentColor ?: MaterialTheme.colorScheme.primary

    /**
     * Use the SearchResultListItem wrapper with contact specific values.
     * 
     * The trailing content is custom for contacts - it shows a call icon
     * if the contact has phone numbers. This is a visual hint that tapping
     * will initiate a call action.
     */
    SearchResultListItem(
        headlineText = result.contact.displayName,
        supportingText = primaryPhone,
        leadingIcon = Icons.Default.Person,
        accentColor = accentColor,
        trailingContent = if (result.contact.phoneNumbers.isNotEmpty()) {
            {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call contact",
                    tint = iconColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(IconSize.small)
                )
            }
        } else null,
        onClick = onClick
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
            .padding(horizontal = Spacing.mediumLarge, vertical = Spacing.smallMedium),
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
                .padding(Spacing.mediumLarge),
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
                modifier = Modifier.size(IconSize.large)
            )

            Spacer(modifier = Modifier.height(Spacing.smallMedium))

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

            Spacer(modifier = Modifier.height(Spacing.medium))

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
 * FileDocumentSearchResultItem - Displays a file/document search result.
 *
 * This item represents a document file from the device storage. It shows:
 * - A document icon (tinted with accent color)
 * - The file name as the headline
 * - File details as supporting text (folder, size, extension)
 * - An appropriate icon based on file type (PDF, Word, etc.)
 *
 * FILE TYPE INDICATORS:
 * - PDF files show a distinctive PDF icon
 * - Word documents show a document icon
 * - Excel spreadsheets show a table icon
 * - PowerPoint presentations show a slideshow icon
 * - EPUB books show a book icon
 * - Text files show a text snippet icon
 * - Other files show a generic file icon
 *
 * REFACTORING NOTE:
 * This component now uses SearchResultListItem wrapper, reducing code
 * from ~70 lines to ~50 lines while maintaining identical functionality.
 * The icon selection logic is still custom since it's unique to files.
 *
 * USAGE:
 * Displayed when the user uses the "f " prefix to search files.
 * Requires storage permission on Android 10 and below.
 *
 * @param result The file search result to display
 * @param accentColor Color for icons (defaults to primary if null)
 * @param onClick Callback when the item is clicked
 */
@Composable
fun FileDocumentSearchResultItem(
    result: FileDocumentSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    val file = result.file

    /**
     * Determine the appropriate icon based on file type.
     * This helps users quickly identify what kind of document it is.
     * 
     * Different file types get different icons:
     * - PDF: Distinctive PDF icon (most recognizable)
     * - Word: Document icon (AutoMirrored for RTL support)
     * - Excel: Table/spreadsheet icon
     * - PowerPoint: Slideshow icon
     * - EPUB: Book icon (AutoMirrored for RTL support)
     * - Text: Text snippet icon (AutoMirrored for RTL support)
     * - Other: Generic file icon (AutoMirrored for RTL support)
     * 
     * NOTE: AutoMirrored icons automatically flip for right-to-left languages
     * like Arabic and Hebrew, ensuring proper visual direction.
     */
    val fileIcon = when {
        file.isPdf() -> Icons.Outlined.PictureAsPdf
        file.isWordDocument() -> Icons.AutoMirrored.Outlined.Article
        file.isExcelSpreadsheet() -> Icons.Outlined.TableChart
        file.isPowerPoint() -> Icons.Outlined.Slideshow
        file.isEpub() -> Icons.AutoMirrored.Outlined.MenuBook
        file.isTextFile() -> Icons.AutoMirrored.Outlined.TextSnippet
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    /**
     * Build the supporting text with file details.
     * Shows folder path and size.
     * 
     * Format: "folder/path • 1.2 MB"
     */
    val supportingText = buildString {
        // Show folder path if available
        if (file.folderPath.isNotEmpty()) {
            append(file.folderPath)
        }
        // Show file size
        if (file.size > 0) {
            if (isNotEmpty()) append(" • ")
            append(file.formattedSize())
        }
    }.takeIf { it.isNotEmpty() }

    /**
     * Use the SearchResultListItem wrapper with file specific values.
     * 
     * The icon changes based on file type, helping users quickly
     * identify what they're looking for.
     */
    SearchResultListItem(
        headlineText = file.name,
        supportingText = supportingText,
        leadingIcon = fileIcon,
        accentColor = accentColor,
        onClick = onClick
    )
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
            modifier = Modifier.padding(Spacing.extraLarge)
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
                modifier = Modifier.size(IconSize.appLarge),
                tint = tint.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(Spacing.mediumLarge))

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
                Spacer(modifier = Modifier.height(Spacing.large))
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
