/**
 * AppGridItem.kt - Compact grid item component for displaying apps in a grid layout
 *
 * This component is designed for displaying apps in a space-efficient grid format.
 * Unlike AppListItem which shows apps horizontally (icon + name side by side),
 * AppGridItem shows them vertically (icon on top, name below) to maximize
 * the number of apps visible on screen.
 *
 * USAGE:
 * This is used in the search dialog when displaying recent apps or search results.
 * The grid layout allows showing 8 apps (2 rows × 4 columns) in a compact space.
 *
 * ACTIONS:
 * - Tap: Launch the app
 * - Long-press: Show action menu (Pin to home, App info)
 *
 * PERFORMANCE CONSIDERATIONS:
 * - Uses rememberAsyncImagePainter for efficient async image loading
 * - Coil handles caching automatically
 * - Text is limited to 2 lines with ellipsis to prevent layout shifts
 */

package com.milki.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.presentation.home.LocalPinAction
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * AppGridItem displays a single app in a compact grid format.
 *
 * Layout:
 * ```
 * ┌─────────────┐
 * │   [ICON]    │  <- 56dp circular icon
 * │   App Name  │  <- 2 lines max, centered
 * └─────────────┘
 * ```
 *
 * @param appInfo The app to display (from domain model)
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier for external customization
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pinAction = LocalPinAction.current
    val context = LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }
    var menuX by remember { mutableStateOf(0f) }
    var menuY by remember { mutableStateOf(0f) }

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
                    AppIcon(
                        packageName = appInfo.packageName,
                        size = IconSize.appGrid
                    )
                }

                Text(
                    text = appInfo.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.smallMedium)
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
