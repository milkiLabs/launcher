package com.milki.launcher.ui.components.launcher.widget

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.ui.components.launcher.PopupOffsetPositionProvider

@Composable
fun PopupWidgetView(
    expanded: Boolean,
    appWidgetId: Int,
    widgetHostManager: WidgetHostManager,
    widthPx: Int,
    heightPx: Int,
    width: Dp,
    height: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val density = LocalDensity.current

    val positionProvider = remember(density) {
        with(density) {
            PopupOffsetPositionProvider(
                windowMarginPx = 12.dp.roundToPx(),
                anchorGapPx = 8.dp.roundToPx(),
                arrowSizePx = 0,
                arrowEdgePaddingPx = 0
            )
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        HomeScreenWidgetView(
            appWidgetId = appWidgetId,
            widgetHostManager = widgetHostManager,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier
                .size(width = width, height = height)
        )
    }
}

