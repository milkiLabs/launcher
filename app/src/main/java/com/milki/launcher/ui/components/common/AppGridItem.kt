/**
 * AppGridItem.kt - Compact grid item component for displaying apps
 *
 * Displays an app in a grid format with:
 * - App icon (56dp)
 * - App name (2 lines max)
 * - Long-press menu for actions
 */

package com.milki.launcher.ui.components.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.interaction.dragdrop.startExternalAppDrag
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * AppGridItem displays an app in a compact grid format.
 *
 * GESTURE INTERACTION MODEL:
 * - Tap: launches the app via onClick
 * - Long-press: shows dropdown menu (non-focusable while finger is down so the
 *   popup window does not steal the ongoing touch sequence from the gesture detector)
 * - Long-press + release: menu becomes focusable and interactive
 * - Long-press + drag: menu closes, external platform drag starts, search dialog dismisses
 *
 * WHY NON-FOCUSABLE POPUP DURING GESTURE:
 * DropdownMenu creates a popup window. When focusable=true, Android routes touch
 * events to the popup, which prevents the gesture detector from receiving movement
 * events needed to detect drag start. By keeping the popup non-focusable while the
 * finger is still down, touches pass through to the gesture detector. Once the
 * finger lifts (onLongPressRelease), we switch to focusable so the menu items
 * become tappable.
 *
 * @param appInfo The app to display
 * @param onClick Called when user taps this item
 * @param onExternalDragStarted Called when an external drag starts (dismisses search dialog)
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onExternalDragStarted: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(
        vertical = Spacing.extraSmall,
        horizontal = Spacing.none
    ),
    modifier: Modifier = Modifier
) {
    val menuState = rememberAppItemContextMenuState()
    val menuActions = remember(appInfo) { buildAppItemMenuActions(appInfo) }
    val hostView = LocalView.current
    val layout = IconLabelLayout(
        iconSize = IconSize.appGrid,
        contentPadding = contentPadding,
        labelTopPadding = Spacing.smallMedium,
        labelMaxLines = 2
    )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .detectDragGesture(
                    key = "${appInfo.packageName}/${appInfo.activityName}",
                    dragThreshold = GridConfig.Default.dragThresholdPx,
                    onTap = onClick,
                    onLongPress = { menuState.onLongPress() },
                    onLongPressRelease = menuState::onLongPressRelease,
                    onDragStart = {
                        menuState.onDragStart()

                        val dragStarted = startExternalAppDrag(
                            hostView = hostView,
                            appInfo = appInfo,
                            dragShadowSize = IconSize.appGrid
                        )

                        if (dragStarted) {
                            hostView.post {
                                onExternalDragStarted()
                            }
                        }
                    },
                    onDrag = { change, _ -> change.consume() },
                    onDragEnd = {},
                    onDragCancel = menuState::onDragCancel
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(CornerRadius.medium)
        ) {
            IconLabelCell(
                label = appInfo.name,
                layout = layout,
                labelStyle = MaterialTheme.typography.bodySmall,
                labelOverflow = TextOverflow.Ellipsis,
                labelTextAlign = TextAlign.Center
            ) {
                AppIcon(
                    packageName = appInfo.packageName,
                    size = layout.iconSize
                )
            }
        }

        ItemActionMenu(
            expanded = menuState.showMenu,
            onDismiss = menuState::dismiss,
            focusable = menuState.isMenuFocusable,
            actions = menuActions
        )
    }
}
