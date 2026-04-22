package com.milki.launcher.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.ui.interaction.dragdrop.startExternalAppDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture

@Composable
internal fun Modifier.detectAppExternalDragGesture(
    appInfo: AppInfo,
    dragShadowSize: Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onLongPressRelease: () -> Unit,
    onDragStart: () -> Unit,
    onDragCancel: () -> Unit,
    onExternalDragStarted: () -> Unit
): Modifier {
    val hostView = LocalView.current

    return this.detectDragGesture(
        key = "${appInfo.packageName}/${appInfo.activityName}",
        dragThreshold = GridConfig.Default.dragThresholdPx,
        onTap = onTap,
        onLongPress = { onLongPress() },
        onLongPressRelease = onLongPressRelease,
        onDragStart = {
            onDragStart()

            val dragStarted = startExternalAppDrag(
                hostView = hostView,
                appInfo = appInfo,
                dragShadowSize = dragShadowSize
            )

            if (dragStarted) {
                hostView.post {
                    onExternalDragStarted()
                }
            }
        },
        onDrag = { change, _ -> change.consume() },
        onDragEnd = {},
        onDragCancel = onDragCancel
    )
}