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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.*
import com.milki.launcher.presentation.search.SearchResultAction
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
 * UrlSearchResultItem - Displays a direct URL result with app handler info.
 *
 * This item appears when the user types a valid URL (e.g., "github.com").
 * It shows which app will handle the URL, making it clear what will happen
 * when the user taps.
 *
 * HANDLER APP DISPLAY:
 * - If a specific app can handle the URL (e.g., YouTube for youtube.com):
 *   - Shows "Open in YouTube" as headline
 *   - Shows the URL as supporting text
 *   - Uses a link icon to indicate deep link
 *
 * - If no specific app (browser fallback):
 *   - Shows "Open [url]" as headline
 *   - Shows the full URL as supporting text
 *   - Uses an info icon
 *
 * VISUAL ELEMENTS:
 * - Leading icon indicates the type of action (deep link vs browser)
 * - Headline shows what will happen (app name or "Open [url]")
 * - Supporting text shows the URL
 * - Trailing content can show an arrow or indicator
 *
 * USAGE:
 * Displayed when the user types a URL-like query without a provider prefix.
 * The URL is normalized with https:// if no scheme was provided.
 *
 * @param result The URL search result to display
 * @param onOpenInApp Callback to open URL in the detected app
 * @param onOpenInBrowser Callback to explicitly open in browser (optional, shown as secondary action)
 */
@Composable
fun UrlSearchResultItem(
    result: UrlSearchResult,
    onOpenInApp: () -> Unit,
    onOpenInBrowser: (() -> Unit)? = null
) {
    /**
     * Determine the icon based on whether there's a handler app.
     * 
     * - Handler app exists: Use Language icon to indicate deep link/app handling
     *   (Language icon represents opening in another app/context)
     * - No handler app: Use Info icon for generic browser opening
     * 
     * We use Language instead of Link because:
     * - Language is more recognizable as "opening elsewhere"
     * - Link is too similar to a URL indicator
     * - Language implies translation between contexts (URL → App)
     */
    val leadingIcon = if (result.handlerApp != null) {
        Icons.Default.Language
    } else {
        Icons.Default.Info
    }

    /**
     * Build the supporting text.
     * 
     * If there's a handler app, we show:
     * - First line: The display URL (what user typed)
     * - Optional: Hint about opening in browser
     * 
     * If no handler app (browser), we show the full URL.
     */
    val supportingText = result.displayUrl

    /**
     * Trailing content to provide visual feedback about the action.
     * 
     * If there's a handler app, show an arrow to indicate "opening in app".
     * This gives users a clear visual indicator that they're navigating
     * to another application.
     */
    val trailingContent: (@Composable () -> Unit)? = if (result.handlerApp != null) {
        {
            /**
             * Arrow icon indicates the URL will open in another app.
             * Using a subtle alpha to not distract from the main content.
             */
            Icon(
                imageVector = Icons.Default.ArrowOutward,
                contentDescription = "Opens in ${result.handlerApp.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(IconSize.small)
            )
        }
    } else {
        null
    }

    /**
     * Use the SearchResultListItem wrapper with URL specific values.
     * 
     * The title comes from the result (already includes app name if available).
     */
    SearchResultListItem(
        headlineText = result.title,
        supportingText = supportingText,
        leadingIcon = leadingIcon,
        trailingContent = trailingContent,
        onClick = onOpenInApp
    )

    /**
     * TODO: Add secondary action for "Open in Browser" when there's a handler app.
     * This would require extending SearchResultListItem to support secondary actions,
     * or using a different layout for URL results.
     * 
     * For now, users can tap the main item to open in the detected app,
     * and if they want browser, they can clear the handler app's default
     * or use a different method.
     */
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
 * TWO CLICK ACTIONS:
 * - onClick: Called when the user taps the contact item itself (opens dialer)
 * - onDialClick: Called when the user taps the dial icon (makes direct call)
 *
 * This separation allows different behaviors:
 * - Tapping the item opens the dialer (no special permission needed)
 * - Tapping the dial icon makes a direct call (requires CALL_PHONE permission)
 *
 * USAGE:
 * Displayed when the user uses the "c " prefix to search contacts.
 * Requires READ_CONTACTS permission (handled by PermissionRequestResult).
 *
 * @param result The contact search result to display
 * @param accentColor Color for icons (defaults to primary if null)
 * @param onClick Callback when the item is clicked (opens dialer)
 * @param onDialClick Callback when the dial icon is clicked (makes direct call)
 */
@Composable
fun ContactSearchResultItem(
    result: ContactSearchResult,
    accentColor: Color?,
    onClick: () -> Unit,
    onDialClick: (() -> Unit)? = null
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
     * Build the trailing content with a clickable dial icon.
     *
     * The dial icon is a separate clickable area from the main item.
     * This allows:
     * - Tapping the item → opens dialer (ACTION_DIAL)
     * - Tapping the dial icon → makes direct call (ACTION_CALL)
     *
     * The icon uses a larger touch target (IconSize.standard = 24dp)
     * for better accessibility and easier tapping.
     */
    val trailingContent: (@Composable () -> Unit)? = if (result.contact.phoneNumbers.isNotEmpty() && onDialClick != null) {
        {
            /**
             * Make the dial icon clickable with a larger touch target.
             *
             * We use a Box with clickable modifier and size larger than the icon
             * to provide a 48dp minimum touch target (Material Design guideline).
             * The icon is centered within this larger touch area.
             *
             * IMPORTANT: We use local onClick variable to capture the callback
             * in this composable's scope. This ensures the click is handled
             * correctly when the user taps the dial icon.
             */
            Box(
                modifier = Modifier
                    .size(Spacing.extraLarge)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onDialClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call directly",
                    tint = iconColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(IconSize.standard)
                )
            }
        }
    } else null

    /**
     * Use the SearchResultListItem wrapper with contact specific values.
     */
    SearchResultListItem(
        headlineText = result.contact.displayName,
        supportingText = primaryPhone,
        leadingIcon = Icons.Default.Person,
        accentColor = accentColor,
        trailingContent = trailingContent,
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
 * @param result The file search result to display
 * @param accentColor Color for icons
 * @param onClick Callback when clicked
 */
@Composable
fun FileDocumentSearchResultItem(
    result: FileDocumentSearchResult,
    accentColor: Color?,
    onClick: () -> Unit
) {
    val file = result.file
    var showMenu by remember { mutableStateOf(false) }

    val fileIcon = when {
        file.isPdf() -> Icons.Outlined.PictureAsPdf
        file.isWordDocument() -> Icons.AutoMirrored.Outlined.Article
        file.isExcelSpreadsheet() -> Icons.Outlined.TableChart
        file.isPowerPoint() -> Icons.Outlined.Slideshow
        file.isEpub() -> Icons.AutoMirrored.Outlined.MenuBook
        file.isTextFile() -> Icons.AutoMirrored.Outlined.TextSnippet
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val supportingText = buildString {
        if (file.folderPath.isNotEmpty()) {
            append(file.folderPath)
        }
        if (file.size > 0) {
            if (isNotEmpty()) append(" • ")
            append(file.formattedSize())
        }
    }.takeIf { it.isNotEmpty() }

    Box {
        SearchResultListItem(
            headlineText = file.name,
            supportingText = supportingText,
            leadingIcon = fileIcon,
            accentColor = accentColor,
            onClick = onClick,
            onLongClick = { showMenu = true }
        )

        ItemActionMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            actions = listOf(
                createPinAction(
                    isPinned = false,
                    pinAction = SearchResultAction.PinFile(file),
                    unpinAction = SearchResultAction.UnpinItem(
                        HomeItem.PinnedFile.fromFileDocument(file).id
                    )
                )
            )
        )
    }
}

/**
 * EmptyState - Displayed when no results match the search.
 *
 * This composable is shown when the search results list is empty.
 * It provides helpful feedback and hints to the user.
 *
 * BEHAVIOR:
 * - If query is blank: Shows provider-specific empty message with prefix hints
 * - If query has text: Shows "No [provider] results found"
 *
 * PROVIDER-SPECIFIC MESSAGES:
 * - Apps (default): "No recent apps\nType to search"
 * - Contacts: "No recent contacts\nType to search your contacts"
 * - Files: "No recent files\nType to search files"
 * - Web/YouTube: "No results found"
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
             * Choose the appropriate message based on the current state.
             *
             * We provide provider-specific messages for better UX:
             * - Blank query with provider: Show what the provider does
             * - Query with provider: Show "no results" for that provider
             * - Blank query without provider: Default apps message
             * - Query without provider: Generic "no apps" message
             */
            val message = when {
                searchQuery.isBlank() && activeProvider != null -> {
                    /**
                     * Blank query in a specific provider mode.
                     * Show provider-specific empty state message.
                     */
                    when (activeProvider.prefix) {
                        "c" -> "No recent contacts\nType to search your contacts"
                        "f" -> "No recent files\nType to search files"
                        "s" -> "Type to search the web"
                        "y" -> "Type to search YouTube"
                        else -> "No ${activeProvider.name.lowercase()} results"
                    }
                }
                searchQuery.isBlank() -> {
                    /**
                     * Blank query in default app mode.
                     * Show the recent apps message.
                     */
                    "No recent apps\nType to search"
                }
                activeProvider != null -> {
                    /**
                     * Query with active provider but no results.
                     * Show provider-specific "no results" message.
                     */
                    "No ${activeProvider.name.lowercase()} results found"
                }
                else -> {
                    /**
                     * Query in default app mode but no results.
                     */
                    "No apps found"
                }
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
