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
import com.milki.launcher.ui.components.common.WidgetPopupIcon
import com.milki.launcher.ui.components.common.ItemContextMenu
import com.milki.launcher.ui.components.common.appInfoPackageNameOrNull
import com.milki.launcher.ui.components.launcher.folder.FolderIcon
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing



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
    compactLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isMenuVisible by remember { mutableStateOf(false) }
    val dismissMenu: () -> Unit = {
        isMenuVisible = false
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        isMenuVisible = true
                    }
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(CornerRadius.medium)
        ) {
            PinnedItemView(item = item, compactLayout = compactLayout)
        }
        
        ItemContextMenu(
            packageName = item.appInfoPackageNameOrNull() ?: "",
            appName = formatHomeItemLabel(item),
            expanded = isMenuVisible,
            onDismiss = dismissMenu,
            focusable = true,
            onExternalDragStarted = dismissMenu,
            extraActions = listOf(
                createUnpinAction(
                    itemId = item.id,
                    actionHandler = com.milki.launcher.presentation.search.LocalSearchActionHandler.current
                )
            )
        )
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
fun PinnedItemView(
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
    return getItemLabel(item).trim()
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
            WidgetPopupIcon(
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
