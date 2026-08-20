/**
 * AppGridItem.kt - Compact grid item component for displaying apps
 *
 * Displays an app in a grid format with:
 * - App icon (56dp, matching home screen compact grid)
 * - App name (2 lines max)
 * - Long-press menu for actions (disable via showMenu = false for
 *   perf-sensitive surfaces; see AppCellLayout)
 *
 * Gesture and menu handling live in the shared [AppCellLayout] shell.
 */

package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.domain.model.AppInfo

import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * AppGridItem displays an app in a compact grid format.
 *
 * GESTURE INTERACTION MODEL (shared AppCellLayout shell):
 * - Tap: launches the app via onClick
 * - Long-press: shows dropdown menu (non-focusable while finger is down so the
 *   popup window does not steal the ongoing touch sequence from the gesture detector)
 * - Long-press + release: menu becomes focusable and interactive
 * - Long-press + drag: menu closes, external platform drag starts, search dialog dismisses
 *
 * @param appInfo The app to display
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier
 * @param showMenu Set false to skip per-item menu state/composition (drawer grid)
 * @param onExternalDragStarted Called when an external drag starts (dismisses search dialog)
 * @param contentPadding Padding around the icon+label content
 */
@Composable
fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true,
    onExternalDragStarted: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(
        vertical = Spacing.extraSmall,
        horizontal = Spacing.none
    )
) {
    val layout = IconLabelLayout(
        iconSize = IconSize.appHomeCompact,
        contentPadding = contentPadding,
        labelTopPadding = Spacing.smallMedium,
        labelMaxLines = 2
    )

    AppCellLayout(
        appInfo = appInfo,
        iconSize = IconSize.appHomeCompact,
        onClick = onClick,
        modifier = modifier,
        showMenu = showMenu,
        onExternalDragStarted = onExternalDragStarted,
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
}
