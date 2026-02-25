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
 * The dropdown menu uses the same ItemActionMenu component as search results,
 * ensuring a consistent UI across the app.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.milki.launcher.domain.model.HomeItem
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
 * - Long press: Shows a dropdown menu with available actions
 *
 * MENU ACTIONS:
 * - All items: "Unpin from home" - removes the item from the home screen
 * - Apps only: "App info" - opens the system app info screen
 *
 * @param item The pinned item to display
 * @param onClick Called when user taps this item
 * @param onLongClick Called when user long-presses this item (DEPRECATED - menu is now shown directly)
 * @param modifier Optional modifier for external customization
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedItem(
    item: HomeItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * State to control whether the dropdown menu is visible.
     * This is triggered by a long press on the item.
     */
    var showMenu by remember { mutableStateOf(false) }

    /**
     * We wrap the entire item in a Box to allow the dropdown menu to be
     * positioned relative to the item. The menu appears anchored to this Box.
     */
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(CornerRadius.medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.medium, horizontal = Spacing.smallMedium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(IconSize.appGrid)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    PinnedItemIcon(item = item, size = IconSize.appGrid)
                }

                Text(
                    text = getItemLabel(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.smallMedium)
                )
            }
        }

        /**
         * Dropdown menu that appears when the user long-presses the item.
         *
         * The menu uses the ItemActionMenu component which is also used in
         * search results, ensuring consistent styling and behavior.
         *
         * Actions are built dynamically based on the item type:
         * - Unpin action: Always available for all item types
         * - App info action: Only available for PinnedApp items
         */
        ItemActionMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            actions = buildPinnedItemActions(item)
        )
    }
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
private fun buildPinnedItemActions(item: HomeItem): List<MenuAction> {
    /**
     * Create a mutable list to hold the actions.
     * We use the helper functions from ItemActionMenu.kt to ensure consistency.
     */
    val actions = mutableListOf<MenuAction>()

    /**
     * The unpin action removes the item from the home screen.
     * We use the createUnpinAction() helper for consistency.
     *
     * This action is available for all item types (apps, files, shortcuts).
     */
    actions.add(createUnpinAction(item.id))

    /**
     * For pinned apps, add the "App info" action.
     * We use the createAppInfoAction() helper for consistency.
     *
     * This opens the system's app info screen where the user can:
     * - View app permissions
     * - Clear cache/data
     * - Uninstall the app
     * - Force stop the app
     */
    if (item is HomeItem.PinnedApp) {
        actions.add(createAppInfoAction(item.packageName))
    }

    return actions
}

/**
 * Returns the display label for a pinned item.
 */
private fun getItemLabel(item: HomeItem): String {
    return when (item) {
        is HomeItem.PinnedApp -> item.label
        is HomeItem.PinnedFile -> item.name
        is HomeItem.AppShortcut -> item.shortLabel
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
        is HomeItem.AppShortcut -> {
            ShortcutIcon(
                shortcut = item,
                size = size,
                modifier = modifier
            )
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
    val iconData = getFileIconData(mimeType, fileName)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = iconData.backgroundColor.copy(alpha = 0.2f)
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconData.icon,
                    contentDescription = null,
                    tint = iconData.iconColor,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

/**
 * Data class holding icon information for file types.
 */
private data class FileIconData(
    val icon: ImageVector,
    val backgroundColor: Color,
    val iconColor: Color
)

/**
 * Returns the appropriate icon for a file based on its MIME type.
 */
@Composable
private fun getFileIconData(mimeType: String, fileName: String): FileIconData {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when {
        mimeType == "application/pdf" || extension == "pdf" -> FileIconData(
            icon = Icons.Outlined.PictureAsPdf,
            backgroundColor = Color(0xFFE53935),
            iconColor = Color.White
        )
        mimeType.startsWith("image/") -> FileIconData(
            icon = Icons.Filled.Image,
            backgroundColor = Color(0xFF43A047),
            iconColor = Color.White
        )
        mimeType.startsWith("video/") -> FileIconData(
            icon = Icons.Filled.VideoFile,
            backgroundColor = Color(0xFFFB8C00),
            iconColor = Color.White
        )
        mimeType.startsWith("audio/") -> FileIconData(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            backgroundColor = Color(0xFF8E24AA),
            iconColor = Color.White
        )
        mimeType.contains("spreadsheet") || extension in listOf("xls", "xlsx", "csv") -> FileIconData(
            icon = Icons.Filled.TableChart,
            backgroundColor = Color(0xFF43A047),
            iconColor = Color.White
        )
        mimeType.contains("document") || extension in listOf("doc", "docx", "rtf") -> FileIconData(
            icon = Icons.Filled.Description,
            backgroundColor = Color(0xFF1E88E5),
            iconColor = Color.White
        )
        mimeType.contains("zip") || mimeType.contains("archive") || extension in listOf("zip", "rar", "7z", "tar") -> FileIconData(
            icon = Icons.Filled.FolderZip,
            backgroundColor = Color(0xFF757575),
            iconColor = Color.White
        )
        else -> FileIconData(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            backgroundColor = Color(0xFF9E9E9E),
            iconColor = Color.White
        )
    }
}

/**
 * Displays an icon for an app shortcut.
 *
 * Ideally, we would load the shortcut's actual icon from the system,
 * but for simplicity, we show a generic shortcut icon with the app's icon
 * as a fallback.
 *
 * TODO: Load actual shortcut icons using LauncherApps.getShortcutIcon()
 */
@Composable
private fun ShortcutIcon(
    shortcut: HomeItem.AppShortcut,
    size: Dp,
    modifier: Modifier = Modifier
) {
    AppIcon(
        packageName = shortcut.packageName,
        size = size,
        modifier = modifier
    )
}
