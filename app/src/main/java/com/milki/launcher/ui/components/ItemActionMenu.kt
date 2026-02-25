/**
 * ItemActionMenu.kt - Unified dropdown menu for item actions
 *
 * This component provides a consistent action menu that uses the unified
 * SearchResultAction system. All actions flow through the same handler.
 *
 * USAGE:
 * - Long-press on any item shows this menu
 * - Actions are emitted via LocalSearchActionHandler
 * - No callbacks needed, uses the same action system as tap actions
 *
 * ACTION BUILDERS:
 * This file provides helper functions to create common menu actions:
 * - createUnpinAction(): For removing items from home screen
 * - createPinAction(): Toggle action for pin/unpin (used in search results)
 * - createAppInfoAction(): For opening system app info screen
 * - createOpenWithAction(): For opening files with specific app
 *
 * These helpers ensure consistent labels, icons, and actions across the app.
 */

package com.milki.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction

/**
 * MenuAction - Represents an action in the dropdown menu.
 *
 * @property label The text to display
 * @property icon The icon to show
 * @property action The SearchResultAction to emit when clicked
 */
data class MenuAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val action: SearchResultAction
)

/**
 * ItemActionMenu - Dropdown menu for item actions.
 *
 * Uses the unified SearchResultAction system. All actions are emitted
 * through LocalSearchActionHandler.
 *
 * @param expanded Whether the menu is visible
 * @param onDismiss Called when menu should close
 * @param actions List of actions to display
 */
@Composable
fun ItemActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier
) {
    /**
     * Haptic feedback controller for providing tactile response on menu item selection.
     * 
     * When a user taps on a menu item, we provide a light haptic feedback to confirm
     * the selection was registered. This creates a more polished and responsive feel.
     */
    val hapticFeedback = LocalHapticFeedback.current

    val actionHandler = LocalSearchActionHandler.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        actions.forEach { menuAction ->
            DropdownMenuItem(
                text = { Text(menuAction.label) },
                leadingIcon = {
                    Icon(
                        imageVector = menuAction.icon,
                        contentDescription = null
                    )
                },
                onClick = {
                    // Provide haptic feedback to confirm menu item selection
                    // This gives the user tactile confirmation that their tap was registered
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    actionHandler(menuAction.action)
                    onDismiss()
                }
            )
        }
    }
}

// ========================================================================
// ACTION BUILDER HELPERS
// ========================================================================

/**
 * Creates an "Unpin from home" action.
 *
 * This action removes an item from the home screen.
 * Used for items that are already pinned (home screen items).
 *
 * @param itemId The ID of the item to unpin
 * @return MenuAction that will remove the item from home screen
 */
fun createUnpinAction(itemId: String): MenuAction {
    return MenuAction(
        label = "Unpin from home",
        icon = Icons.Filled.Delete,
        action = SearchResultAction.UnpinItem(itemId)
    )
}

/**
 * Creates pin/unpin toggle action for search results.
 *
 * This is used in search results where items can be either pinned or not.
 * For items already on the home screen, use createUnpinAction() instead.
 *
 * @param isPinned Whether the item is currently pinned
 * @param pinAction Action to emit for pinning
 * @param unpinAction Action to emit for unpinning
 */
fun createPinAction(
    isPinned: Boolean,
    pinAction: SearchResultAction,
    unpinAction: SearchResultAction
): MenuAction {
    return if (isPinned) {
        MenuAction(
            label = "Unpin from home",
            icon = Icons.Filled.Delete,
            action = unpinAction
        )
    } else {
        MenuAction(
            label = "Pin to home",
            icon = Icons.Outlined.PushPin,
            action = pinAction
        )
    }
}

/**
 * Creates "App info" action.
 *
 * This action opens the system's app info screen where users can:
 * - View app permissions
 * - Clear cache/data
 * - Uninstall the app
 * - Force stop the app
 *
 * @param packageName The package name of the app
 * @return MenuAction that will open the app info screen
 */
fun createAppInfoAction(packageName: String): MenuAction {
    return MenuAction(
        label = "App info",
        icon = Icons.Filled.Info,
        action = SearchResultAction.OpenAppInfo(packageName)
    )
}

/**
 * Creates "Open with..." action for files.
 *
 * This allows users to choose which app to open a file with.
 * Note: Currently uses a placeholder action that needs proper implementation.
 *
 * @return MenuAction for opening file with specific app
 */
fun createOpenWithAction(): MenuAction {
    return MenuAction(
        label = "Open with...",
        icon = Icons.AutoMirrored.Filled.OpenInNew,
        action = SearchResultAction.RequestPermission("", "")
    )
}
