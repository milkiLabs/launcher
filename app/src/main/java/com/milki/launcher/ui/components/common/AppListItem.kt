/**
 * AppListItem.kt - List item component for displaying apps
 *
 * Displays an app in a horizontal list format with:
 * - App icon (40dp)
 * - App name
 * - Long-press menu for actions
 */

package com.milki.launcher.ui.components.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.components.launcher.ItemActionMenu
import com.milki.launcher.ui.components.launcher.createAppInfoAction
import com.milki.launcher.ui.components.launcher.createPinAction
import com.milki.launcher.ui.components.grid.GridConfig
import com.milki.launcher.ui.components.grid.detectDragGesture
import com.milki.launcher.ui.components.dragdrop.startExternalAppDrag
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
 * @param onExternalDragStarted Called when an external drag starts (dismisses search dialog)
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onExternalDragStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var isGestureActive by remember { mutableStateOf(false) }
    val hostView = LocalView.current

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .detectDragGesture(
                    key = "${appInfo.packageName}/${appInfo.activityName}",
                    dragThreshold = GridConfig.Default.dragThresholdPx,
                    onTap = onClick,
                    onLongPress = {
                        showMenu = true
                        isGestureActive = true
                    },
                    onLongPressRelease = {
                        isGestureActive = false
                    },
                    onDragStart = {
                        showMenu = false
                        isGestureActive = false

                        val dragStarted = startExternalAppDrag(
                            hostView = hostView,
                            appInfo = appInfo,
                            dragShadowSize = IconSize.appList
                        )

                        if (dragStarted) {
                            hostView.post {
                                onExternalDragStarted()
                            }
                        }
                    },
                    onDrag = { change, _ -> change.consume() },
                    onDragEnd = {},
                    onDragCancel = {
                        isGestureActive = false
                    }
                ),
            color = Color.Transparent
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

        ItemActionMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false; isGestureActive = false },
            focusable = !isGestureActive,
            actions = listOf(
                createPinAction(
                    isPinned = false,
                    pinAction = SearchResultAction.PinApp(appInfo),
                    unpinAction = SearchResultAction.UnpinItem(
                        HomeItem.PinnedApp.fromAppInfo(appInfo).id
                    )
                ),
                createAppInfoAction(appInfo.packageName)
            )
        )
    }
}
