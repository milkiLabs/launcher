package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milki.launcher.data.icon.ShortcutIconLoader
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.ui.theme.IconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHORTCUT_BADGE_SIZE_RATIO = 0.34f
private const val SHORTCUT_BADGE_PADDING_RATIO = 0.12f

/**
 * Renders an app-published shortcut icon, with optional browser/app badge.
 */
@Composable
fun ShortcutIcon(
    shortcut: HomeItem.AppShortcut,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.appList,
    showBrowserBadge: Boolean = true
) {
    val context = LocalContext.current
    var shortcutDrawable by remember(shortcut.packageName, shortcut.shortcutId) {
        mutableStateOf(ShortcutIconLoader.getCached(shortcut))
    }

    LaunchedEffect(shortcut.packageName, shortcut.shortcutId) {
        if (shortcutDrawable == null) {
            shortcutDrawable = withContext(Dispatchers.IO) {
                ShortcutIconLoader.getOrLoad(
                    context = context,
                    shortcut = shortcut
                )
            }
        }
    }

    Box(
        modifier = modifier.size(size)
    ) {
        if (shortcutDrawable != null) {
            DrawableIcon(
                drawable = shortcutDrawable,
                size = size,
                modifier = Modifier.matchParentSize()
            )
        } else {
            AppIcon(
                packageName = shortcut.packageName,
                size = size,
                modifier = Modifier.matchParentSize()
            )
        }

        if (showBrowserBadge) {
            ShortcutBrowserBadge(
                packageName = shortcut.packageName,
                iconSize = size
            )
        }
    }
}

@Composable
private fun BoxScope.ShortcutBrowserBadge(
    packageName: String,
    iconSize: Dp
) {
    val badgeSize = (iconSize * SHORTCUT_BADGE_SIZE_RATIO).coerceAtLeast(16.dp)
    val badgePadding = (badgeSize * SHORTCUT_BADGE_PADDING_RATIO).coerceAtLeast(2.dp)

    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(badgeSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 3.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                packageName = packageName,
                size = badgeSize - (badgePadding * 2),
                modifier = Modifier.padding(badgePadding)
            )
        }
    }
}
