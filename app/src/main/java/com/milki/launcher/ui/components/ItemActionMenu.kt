/**
 * ItemActionMenu.kt - Dropdown menu for item actions
 *
 * This component displays a dropdown menu when the user long-presses on
 * a search result item. It provides actions like pinning to home screen.
 *
 * USAGE:
 * - Long-press on app/file in search results → Shows this menu
 * - Menu contains contextual actions for the item type
 *
 * ACTIONS:
 * - Pin to home: Adds item to home screen grid
 * - App info: Opens system app info (for apps only)
 * - Remove from home: Shows if item is already pinned
 */

package com.milki.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Data class representing an action that can be performed on an item.
 *
 * @property label The text to display in the menu
 * @property icon The icon to show next to the label
 * @property onClick The action to perform when clicked
 */
data class ItemAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

/**
 * ItemActionMenu - Dropdown menu for item actions.
 *
 * Displays a list of actions that can be performed on a search result item.
 * The menu appears anchored to a specific position (typically the item that
 * was long-pressed).
 *
 * COMMON ACTIONS:
 * - Pin to home: Pin the item to the home screen
 * - Unpin from home: Remove the item from home screen (if pinned)
 * - App info: Open system app settings (for apps)
 *
 * @param expanded Whether the menu is currently visible
 * @param onDismiss Called when the menu should be dismissed
 * @param actions List of actions to display in the menu
 * @param modifier Optional modifier for the menu
 */
@Composable
fun ItemActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<ItemAction>,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null
                    )
                },
                onClick = {
                    action.onClick()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Creates the standard action list for an app item.
 *
 * @param isPinned Whether the app is already pinned to home
 * @param onPin Action to pin the app to home
 * @param onUnpin Action to remove the app from home
 * @param onAppInfo Action to open app info (optional)
 */
fun createAppActions(
    isPinned: Boolean,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onAppInfo: (() -> Unit)? = null
): List<ItemAction> {
    return buildList {
        if (isPinned) {
            add(
                ItemAction(
                    label = "Unpin from home",
                    icon = Icons.Filled.Delete,
                    onClick = onUnpin
                )
            )
        } else {
            add(
                ItemAction(
                    label = "Pin to home",
                    icon = Icons.Outlined.PushPin,
                    onClick = onPin
                )
            )
        }
        
        if (onAppInfo != null) {
            add(
                ItemAction(
                    label = "App info",
                    icon = Icons.Filled.Info,
                    onClick = onAppInfo
                )
            )
        }
    }
}

/**
 * Creates the standard action list for a file item.
 *
 * @param isPinned Whether the file is already pinned to home
 * @param onPin Action to pin the file to home
 * @param onUnpin Action to remove the file from home
 * @param onOpenWith Action to open file with specific app (optional)
 */
fun createFileActions(
    isPinned: Boolean,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onOpenWith: (() -> Unit)? = null
): List<ItemAction> {
    return buildList {
        if (isPinned) {
            add(
                ItemAction(
                    label = "Unpin from home",
                    icon = Icons.Filled.Delete,
                    onClick = onUnpin
                )
            )
        } else {
            add(
                ItemAction(
                    label = "Pin to home",
                    icon = Icons.Outlined.PushPin,
                    onClick = onPin
                )
            )
        }
        
        if (onOpenWith != null) {
            add(
                ItemAction(
                    label = "Open with...",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenWith
                )
            )
        }
    }
}
