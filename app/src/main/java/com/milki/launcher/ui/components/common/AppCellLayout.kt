/**
 * AppCellLayout.kt - Shared gesture/menu shell for app grid and list cells
 *
 * Single primitive behind AppGridItem, AppListItem, and lightweight drawer
 * cells. Owns everything that must behave identically across surfaces:
 *
 * - Tap: forwards to onClick
 * - Long-press + drag: starts external platform drag (dragShadowSize = iconSize)
 * - Long-press: shows the app context menu when [showMenu] is true
 *
 * MENU VISIBILITY AS A PARAMETER:
 * Perf-sensitive surfaces (e.g. the drawer grid) pass showMenu = false.
 * In that mode no ItemContextMenuState is allocated and no menu composable
 * is composed for the cell; long-press alone is a no-op while long-press +
 * drag still works. See AppDrawerOverlay.kt for the rationale.
 */

package com.milki.launcher.ui.components.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.milki.launcher.domain.model.AppInfo

/**
 * Shared cell shell: gesture detection plus optional context menu.
 *
 * @param appInfo The app displayed in this cell
 * @param iconSize Icon size used for the external drag shadow
 * @param onClick Called when user taps this cell
 * @param modifier Optional modifier for the outer container
 * @param showMenu When false, no context menu state or composable is created
 * @param onExternalDragStarted Called when an external drag starts
 * @param shape Shape applied to the cell surface
 * @param content The visual cell content (icon, label, ...)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCellLayout(
    appInfo: AppInfo,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true,
    onExternalDragStarted: () -> Unit = {},
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit
) {
    val menuState = if (showMenu) rememberItemContextMenuState() else null

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .detectAppExternalDragGesture(
                    appInfo = appInfo,
                    dragShadowSize = iconSize,
                    onTap = onClick,
                    onLongPress = { menuState?.onLongPress() },
                    onLongPressRelease = { menuState?.onLongPressRelease() },
                    onDragStart = { menuState?.onDragStart() },
                    onDragCancel = { menuState?.onDragCancel() },
                    onExternalDragStarted = onExternalDragStarted
                ),
            color = Color.Transparent,
            shape = shape
        ) {
            content()
        }

        if (menuState != null) {
            AppItemContextMenu(
                appInfo = appInfo,
                menuState = menuState,
                onExternalDragStarted = onExternalDragStarted
            )
        }
    }
}
