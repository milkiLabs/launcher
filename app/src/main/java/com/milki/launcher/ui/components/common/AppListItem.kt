/**
 * AppListItem.kt - List item component for displaying apps
 *
 * Displays an app in a horizontal list format with:
 * - App icon (40dp)
 * - App name
 * - Long-press menu for actions
 *
 * Gesture and menu handling live in the shared [AppCellLayout] shell.
 */

package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.AppInfo

import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * AppListItem displays an app in a horizontal list row.
 *
 * GESTURE INTERACTION MODEL:
 * Same model as AppGridItem — see its documentation for full details.
 * - Long-press shows non-focusable menu (doesn't steal touches)
 * - Finger lift makes menu interactive
 * - Drag closes menu and starts external platform drag
 *
 * @param appInfo The app to display
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier
 * @param showMenu Set false to skip per-item menu state/composition
 * @param onExternalDragStarted Called when an external drag starts (dismisses search dialog)
 */
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true,
    onExternalDragStarted: () -> Unit = {}
) {
    AppCellLayout(
        appInfo = appInfo,
        iconSize = IconSize.appList,
        onClick = onClick,
        modifier = modifier,
        showMenu = showMenu,
        onExternalDragStarted = onExternalDragStarted
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.mediumLarge,
                vertical = Spacing.medium
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = appInfo.packageName,
                size = IconSize.appList
            )

            Spacer(modifier = Modifier.width(Spacing.medium))

            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
