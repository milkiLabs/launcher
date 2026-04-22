/**
 * PinnedItem.kt - Composable for displaying a single pinned item on the home screen
 *
 * This component displays a pinned item (app, file, or shortcut) in the home screen grid.
 * It handles different item types and displays appropriate icons.
 *
 * ITEM TYPES:
 * - PinnedApp: Shows the app icon and name
 * - PinnedFile: Shows a file type icon and filename
 * - AppShortcut: Shows the shortcut icon and label
 *
 * INTERACTION:
 * - Tap: Open/launch the item
 * - Long press: Show dropdown menu with actions (Unpin, App info for apps)
 *
 * NOTE ON LONG-PRESS HANDLING:
 * When used within DraggablePinnedItemsGrid, the long-press and drag gestures are
 * handled by the parent grid. In this case, pass handleLongPress = false.
 * When used standalone (e.g., in search results), pass handleLongPress = true.
 *
 * The dropdown menu uses the same ItemActionMenu component as search results,
 * ensuring a consistent UI across the app.
 */

package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.components.common.AppIcon
import com.milki.launcher.ui.components.common.IconLabelCell
import com.milki.launcher.ui.components.common.IconLabelLayout
import com.milki.launcher.ui.components.common.ShortcutIcon
import com.milki.launcher.ui.components.common.buildAppUtilityMenuActions
import com.milki.launcher.ui.components.common.rememberAppQuickActions
import com.milki.launcher.ui.components.launcher.folder.FolderIcon
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

private const val HOME_ITEM_LABEL_MAX_LENGTH = 18
private const val LABEL_ELLIPSIS_LENGTH = 3

/**
 * PinnedItem displays a single pinned item in the home screen grid.
 *
 * Layout:
 * ```
 * ┌─────────────┐
 * │   [ICON]    │  <- 56dp icon (app icon or file type icon)
 * │   Label     │  <- 1 line max, centered
 * └─────────────┘
 *
 * INTERACTION:
 * - Tap: Opens/launches the item
 * - Long press: Shows a dropdown menu with available actions (if handleLongPress is true)
 *
 * MENU ACTIONS:
 * - All items: "Unpin from home" - removes the item from the home screen
 * - Apps only: "App info" - opens the system app info screen
 *
 * @param item The pinned item to display
 * @param onClick Called when user taps this item
 * @param handleLongPress Whether this composable should handle long-press gestures.
 *                        Set to false when used in DraggablePinnedItemsGrid (parent handles gestures).
 *                        Set to true when used standalone.
 * @param showMenu External control for showing the menu (used by parent when handleLongPress is false)
 * @param menuFocusable Whether the menu popup can receive focus. When false, touches pass through
 *                      to the underlying gesture detector. Used by DraggablePinnedItemsGrid to
 *                      keep drag detection working while the menu is visually shown.
 * @param modifier Optional modifier for external customization
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedItem(
    item: HomeItem,
    onClick: () -> Unit,
    handleLongPress: Boolean = true,
    compactLayout: Boolean = false,
    showMenu: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    menuFocusable: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var internalShowMenu by remember { mutableStateOf(false) }
    val isMenuVisible = if (handleLongPress) internalShowMenu else showMenu
    val quickActions = if (item is HomeItem.PinnedApp) {
        rememberAppQuickActions(
            packageName = item.packageName,
            shouldLoad = isMenuVisible
        )
    } else {
        emptyList()
    }
    val dismissMenu: () -> Unit = {
        if (handleLongPress) {
            internalShowMenu = false
        } else {
            onMenuDismiss()
        }
    }

    Box(modifier = modifier) {
        PinnedItemSurface(
            item = item,
            compactLayout = compactLayout,
            handleLongPress = handleLongPress,
            onClick = onClick,
            onLongPressDetected = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                internalShowMenu = true
            }
        )
        ItemActionMenu(
            expanded = isMenuVisible,
            onDismiss = dismissMenu,
            focusable = menuFocusable,
            onExternalDragStarted = dismissMenu,
            actions = buildPinnedItemActions(item, quickActions)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedItemSurface(
    item: HomeItem,
    compactLayout: Boolean,
    handleLongPress: Boolean,
    onClick: () -> Unit,
    onLongPressDetected: () -> Unit
) {
    if (handleLongPress) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPressDetected
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(CornerRadius.medium)
        ) {
            PinnedItemContent(item = item, compactLayout = compactLayout)
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            PinnedItemContent(item = item, compactLayout = compactLayout)
        }
    }
}

/**
 * The content of a pinned item - icon and label.
 * Extracted to a separate composable to avoid code duplication.
 *
 * FOLDER DISPATCH:
 * FolderItem uses a completely different visual structure ([FolderIcon]) compared
 * to the standard single-icon + label layout. We delegate to [FolderIcon] early
 * and return so the standard Column below is never built for folders.
 */
@Composable
private fun PinnedItemContent(
    item: HomeItem,
    compactLayout: Boolean
) {
    val layout = homeItemIconLabelLayout(
        compact = compactLayout,
        regularContentVerticalPadding = Spacing.extraSmall
    )

    // ── Folder short-circuit ──────────────────────────────────────────────────
    // FolderItem has its own layout with a 2×2 mini-icon preview grid.
    // Delegate directly to FolderIcon and return early to skip the standard
    // single-icon + label column below.
    if (item is HomeItem.FolderItem) {
        FolderIcon(
            folder = item,
            compact = compactLayout
        )
        return
    }

    // ── Standard single-icon layout ───────────────────────────────────────────
    IconLabelCell(
        label = formatHomeItemLabel(item),
        layout = layout,
        labelColor = Color.White,
        labelStyle = MaterialTheme.typography.bodySmall,
        labelOverflow = TextOverflow.Ellipsis,
        labelTextAlign = TextAlign.Center
    ) {
        PinnedItemIcon(item = item, size = layout.iconSize)
    }
}

private fun formatHomeItemLabel(item: HomeItem): String {
    return truncateHomeItemLabel(getItemLabel(item))
}

private fun truncateHomeItemLabel(label: String): String {
    val trimmedLabel = label.trim()
    if (trimmedLabel.length <= HOME_ITEM_LABEL_MAX_LENGTH) {
        return trimmedLabel
    }

    return trimmedLabel
        .take(HOME_ITEM_LABEL_MAX_LENGTH - LABEL_ELLIPSIS_LENGTH)
        .trimEnd()
        .plus("...")
}

/**
 * Builds the list of menu actions for a pinned item.
 *
 * The actions available depend on the item type:
 * - All items can be unpinned from the home screen
 * - Apps additionally have an "App info" action
 *
 * This function uses the helper functions from ItemActionMenu.kt to ensure
 * consistent action creation across the app.
 *
 * @param item The pinned item to build actions for
 * @return List of MenuAction objects to display in the dropdown menu
 */
private fun buildPinnedItemActions(
    item: HomeItem,
    quickActions: List<HomeItem.AppShortcut>
): List<MenuAction> {
    /**
     * Create a mutable list to hold the actions.
     * We use the helper functions from ItemActionMenu.kt to ensure consistency.
     */
    val actions = mutableListOf<MenuAction>()

    if (item is HomeItem.PinnedApp) {
        actions.addAll(
            buildAppUtilityMenuActions(
                packageName = item.packageName,
                quickActions = quickActions
            )
        )
    }

    /**
     * The unpin action removes the item from the home screen.
     * We use the createUnpinAction() helper for consistency.
     *
     * This action is available for all item types (apps, files, shortcuts, folders).
     * For folders, "unpin" removes the entire folder AND all its children.
     */
    actions.add(createUnpinAction(item.id))

    // FolderItem does NOT get any extra actions beyond unpin.
    // Rename is handled inline by tapping the title inside the FolderPopupDialog.

    return actions
}

/**
 * Returns the display label for a pinned item.
 */
private fun getItemLabel(item: HomeItem): String {
    return when (item) {
        is HomeItem.PinnedApp -> item.label
        is HomeItem.PinnedFile -> item.name
        is HomeItem.PinnedContact -> item.displayName
        is HomeItem.AppShortcut -> item.shortLabel.ifBlank { item.longLabel }
        // Folder name is set by the user (defaults to "Folder").
        is HomeItem.FolderItem -> item.name
        // Widget label comes from the provider metadata.
        is HomeItem.WidgetItem -> item.label
    }
}

/**
 * Displays the appropriate icon for a pinned item.
 *
 * For apps, shows the app icon.
 * For files, shows a file type icon based on the MIME type.
 * For shortcuts, shows the shortcut icon (or a generic shortcut icon).
 */
@Composable
private fun PinnedItemIcon(
    item: HomeItem,
    size: Dp,
    modifier: Modifier = Modifier
) {
    when (item) {
        is HomeItem.PinnedApp -> {
            AppIcon(
                packageName = item.packageName,
                size = size,
                modifier = modifier
            )
        }
        is HomeItem.PinnedFile -> {
            FileIcon(
                mimeType = item.mimeType,
                fileName = item.name,
                size = size,
                modifier = modifier
            )
        }
        is HomeItem.PinnedContact -> {
            ContactIcon(
                size = size,
                modifier = modifier
            )
        }
        is HomeItem.AppShortcut -> {
            ShortcutIcon(
                shortcut = item,
                size = size,
                modifier = modifier
            )
        }
        is HomeItem.FolderItem -> {
            // PinnedItemIcon should never be called for FolderItem because
            // PinnedItemContent short-circuits to FolderIcon before reaching
            // this function. This branch exists solely to make the when
            // expression exhaustive and avoid a compile error.
            FolderIcon(
                folder = item,
                modifier = modifier
            )
        }
        is HomeItem.WidgetItem -> {
            // Widgets are rendered by HomeScreenWidgetView, not PinnedItemIcon.
            // This branch exists solely for exhaustiveness.
            AppIcon(
                packageName = item.providerPackage,
                size = size,
                modifier = modifier
            )
        }
    }
}

/**
 * Displays a generic contact icon for pinned contacts.
 */
@Composable
private fun ContactIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(CornerRadius.medium),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.appList)
                )
            }
        }
    }
}

/**
 * Displays an icon for a file based on its MIME type.
 *
 * Shows different icons for:
 * - PDF files
 * - Documents (Word, etc.)
 * - Spreadsheets (Excel, etc.)
 * - Archives (ZIP, etc.)
 * - Generic file icon for unknown types
 */
@Composable
private fun FileIcon(
    mimeType: String,
    fileName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val fileTypeVisual = resolveFileTypeVisual(mimeType, fileName)

    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = RectangleShape,
            color = fileTypeVisual.backgroundColor.copy(alpha = 0.2f)
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileTypeVisual.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * FILE_ICON_FOREGROUND_SCALE)
                )
            }
        }
    }
}
