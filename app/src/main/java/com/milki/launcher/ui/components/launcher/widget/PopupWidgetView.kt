package com.milki.launcher.ui.components.launcher.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.milki.launcher.data.widget.WidgetHostManager

import com.milki.launcher.ui.components.launcher.ItemActionMenuPlacement
import com.milki.launcher.ui.components.launcher.ItemActionMenuVerticalPlacement
import com.milki.launcher.ui.components.launcher.calculateItemActionMenuPlacement
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing

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
            PopupWidgetPositionProvider(
                windowMarginPx = 12.dp.roundToPx(),
                anchorGapPx = 8.dp.roundToPx(),
                arrowSizePx = 16.dp.roundToPx(),
                arrowEdgePaddingPx = 24.dp.roundToPx()
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
        PopupWidgetBubble(
            appWidgetId = appWidgetId,
            widgetHostManager = widgetHostManager,
            widthPx = widthPx,
            heightPx = heightPx,
            width = width,
            height = height,
            placement = positionProvider.placement,
            modifier = modifier
        )
    }
}

@Composable
private fun PopupWidgetBubble(
    appWidgetId: Int,
    widgetHostManager: WidgetHostManager,
    widthPx: Int,
    heightPx: Int,
    width: Dp,
    height: Dp,
    placement: ItemActionMenuPlacement,
    modifier: Modifier = Modifier
) {
    val surfaceColor = Color(0xFF20232A).copy(alpha = 0.98f)
    val arrowHalf = 8.dp
    val arrowOffset = with(LocalDensity.current) { placement.arrowOffsetPx.toDp() }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.padding(
                top = if (placement.verticalPlacement == ItemActionMenuVerticalPlacement.Below) {
                    arrowHalf
                } else {
                    0.dp
                },
                bottom = if (placement.verticalPlacement == ItemActionMenuVerticalPlacement.Above) {
                    arrowHalf
                } else {
                    0.dp
                }
            ),
            shape = RoundedCornerShape(CornerRadius.extraLarge),
            color = surfaceColor,
            shadowElevation = 18.dp,
            tonalElevation = Spacing.none
        ) {
            HomeScreenWidgetView(
                appWidgetId = appWidgetId,
                widgetHostManager = widgetHostManager,
                widthPx = widthPx,
                heightPx = heightPx,
                modifier = Modifier
                    .size(width = width, height = height)
                    .clip(RoundedCornerShape(CornerRadius.extraLarge))
            )
        }

        Box(
            modifier = Modifier
                .align(
                    if (placement.verticalPlacement == ItemActionMenuVerticalPlacement.Above) {
                        Alignment.BottomStart
                    } else {
                        Alignment.TopStart
                    }
                )
                .offset(x = arrowOffset)
                .size(16.dp)
                .rotate(45f)
                .background(surfaceColor, RoundedCornerShape(4.dp))
        )
    }
}

private class PopupWidgetPositionProvider(
    private val windowMarginPx: Int,
    private val anchorGapPx: Int,
    private val arrowSizePx: Int,
    private val arrowEdgePaddingPx: Int
) : PopupPositionProvider {
    var placement by mutableStateOf(ItemActionMenuPlacement())
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val resolvedPlacement = calculateItemActionMenuPlacement(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            popupContentSize = popupContentSize,
            windowMarginPx = windowMarginPx,
            anchorGapPx = anchorGapPx,
            arrowSizePx = arrowSizePx,
            arrowEdgePaddingPx = arrowEdgePaddingPx
        )
        placement = resolvedPlacement
        return resolvedPlacement.popupOffset
    }
}
