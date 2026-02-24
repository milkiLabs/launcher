/**
 * AppListItem.kt - Reusable component for displaying an app in a list
 *
 * This component displays an app in a horizontal list item format with
 * support for an action menu on long-press.
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.home.LocalPinAction
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * AppListItem displays a single row in the app list.
 *
 * Supports:
 * - Tap: Launch the app
 * - Long-press: Show action menu (Pin to home, App info)
 *
 * @param appInfo The app to display (from domain model)
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier for external customization
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pinAction = LocalPinAction.current
    val context = LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.mediumLarge, vertical = Spacing.medium),
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
            onDismiss = { showMenu = false },
            actions = createAppActions(
                isPinned = false,
                onPin = {
                    pinAction(HomeItem.PinnedApp.fromAppInfo(appInfo))
                },
                onUnpin = {},
                onAppInfo = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${appInfo.packageName}")
                    ).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
        )
    }
}
