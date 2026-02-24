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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * @param item The pinned item to display
 * @param onClick Called when user taps this item
 * @param onLongClick Called when user long-presses this item (for remove option)
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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

/**
 * A dialog to confirm removing a pinned item.
 *
 * @param item The item to be removed
 * @param onConfirm Called when user confirms removal
 * @param onDismiss Called when user cancels
 */
@Composable
fun RemoveItemDialog(
    item: HomeItem?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (item == null) return

    val itemName = getItemLabel(item)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove shortcut?") },
        text = { Text("Remove \"$itemName\" from home screen?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
