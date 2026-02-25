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
                    actionHandler(menuAction.action)
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Creates pin/unpin action for any item type.
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
 * Creates app info action.
 */
fun createAppInfoAction(packageName: String): MenuAction {
    return MenuAction(
        label = "App info",
        icon = Icons.Filled.Info,
        action = SearchResultAction.OpenAppInfo(packageName)
    )
}

/**
 * Creates open with action for files.
 */
fun createOpenWithAction(): MenuAction {
    return MenuAction(
        label = "Open with...",
        icon = Icons.AutoMirrored.Filled.OpenInNew,
        action = SearchResultAction.RequestPermission("", "")
    )
}
