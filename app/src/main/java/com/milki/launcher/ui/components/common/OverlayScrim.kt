package com.milki.launcher.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Shared full-screen scrim for launcher overlays. Tapping the scrim invokes [onClose].
 *
 * Always derives its color from `MaterialTheme.colorScheme.scrim`; pass the desired
 * [alpha] per overlay instead of hardcoding ad-hoc `Color.Black.copy(alpha = ...)` values.
 *
 * OVERLAY MECHANISM MAP (which overlay type applies to which feature):
 * - App drawer / widgets sheet / action-shortcuts sheet:
 *     [com.milki.launcher.ui.screens.launcher.LauncherSurfaceSheetHost]
 *     custom drag-to-dismiss sheet hosted in the launcher surface; no separate scrim,
 *     dismissal via drag handle, Back handling lives in [com.milki.launcher.app.activity.MainActivity].
 * - Search:
 *     `Dialog` window ([com.milki.launcher.ui.components.search.AppSearchDialog]) +
 *     [OverlayScrim]; tap-to-dismiss and BackHandler handled locally, insets via
 *     safeDrawing/navigationBars/ime padding.
 * - Folders:
 *     Anchored popup surface ([com.milki.launcher.ui.components.launcher.folder.FolderPopupDialog])
 *     + [OverlayScrim] with animated alpha driven by the open progress.
 * - Item context menus / widget popups:
 *     `Popup` windows ([com.milki.launcher.ui.components.launcher.ItemActionMenu],
 *     [com.milki.launcher.ui.components.launcher.widget.PopupWidgetView]); no scrim,
 *     they dismiss on outside tap via their own focusable/window logic.
 *
 * Note: widget transform/edit mode draws a full-screen dimming layer whose tap
 * CONFIRMS the transform (rather than closing); it intentionally does not use
 * [OverlayScrim] because its click semantics differ.
 */
private const val DEFAULT_OVERLAY_SCRIM_ALPHA = 0.34f

@Composable
fun OverlayScrim(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = DEFAULT_OVERLAY_SCRIM_ALPHA
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = alpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
    )
}
