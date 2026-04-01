package com.milki.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.milki.launcher.presentation.main.LocalContextMenuDismissSignal
import com.milki.launcher.presentation.search.LocalSearchActionHandler
import com.milki.launcher.presentation.search.SearchResultAction
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val action: SearchResultAction? = null,
    val onClick: (() -> Unit)? = null,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true
)

internal enum class ItemActionMenuVerticalPlacement {
    Above,
    Below
}

internal data class ItemActionMenuPlacement(
    val popupOffset: IntOffset = IntOffset.Zero,
    val verticalPlacement: ItemActionMenuVerticalPlacement = ItemActionMenuVerticalPlacement.Above,
    val arrowOffsetPx: Int = 0
)

@Composable
fun ItemActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
    focusable: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!expanded || actions.isEmpty()) return

    val hapticFeedback = LocalHapticFeedback.current
    val actionHandler = LocalSearchActionHandler.current
    val density = LocalDensity.current
    val dismissSignal = LocalContextMenuDismissSignal.current
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    var lastHandledDismissSignal by remember { mutableIntStateOf(dismissSignal) }

    LaunchedEffect(dismissSignal) {
        if (dismissSignal == lastHandledDismissSignal) return@LaunchedEffect
        lastHandledDismissSignal = dismissSignal
        latestOnDismiss()
    }

    val positionProvider = remember(density) {
        with(density) {
            ItemActionMenuPositionProvider(
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
            focusable = focusable,
            dismissOnBackPress = focusable,
            dismissOnClickOutside = focusable
        )
    ) {
        ItemActionMenuBubble(
            actions = actions,
            placement = positionProvider.placement,
            modifier = modifier,
            onActionSelected = { menuAction ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                menuAction.onClick?.invoke() ?: menuAction.action?.let(actionHandler)
                onDismiss()
            }
        )
    }
}

@Composable
private fun ItemActionMenuBubble(
    actions: List<MenuAction>,
    placement: ItemActionMenuPlacement,
    onActionSelected: (MenuAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceColor = Color(0xFF2F323A).copy(alpha = 0.97f)
    val iconTintDefault = Color.White.copy(alpha = 0.96f)
    val textColorDefault = Color.White.copy(alpha = 0.98f)
    val destructiveColor = Color(0xFFFFB4AB)
    val arrowOffset = with(LocalDensity.current) { placement.arrowOffsetPx.toDp() }
    val arrowHalf = 8.dp

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .widthIn(min = 196.dp, max = 280.dp)
                .padding(
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
            shape = RoundedCornerShape(28.dp),
            color = surfaceColor,
            shadowElevation = 18.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = Spacing.smallMedium, horizontal = Spacing.smallMedium),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                actions.forEach { action ->
                    val tint = if (action.isDestructive) destructiveColor else iconTintDefault
                    val textColor = if (action.isDestructive) destructiveColor else textColorDefault

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent, RoundedCornerShape(CornerRadius.large))
                            .clickable(
                                enabled = action.enabled,
                                onClick = { onActionSelected(action) }
                            )
                            .padding(
                                horizontal = Spacing.mediumLarge,
                                vertical = Spacing.mediumLarge
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(IconSize.standard)
                        )
                        Spacer(modifier = Modifier.width(Spacing.mediumLarge))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor
                        )
                    }
                }
            }
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

private class ItemActionMenuPositionProvider(
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
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
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

internal fun calculateItemActionMenuPlacement(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    windowMarginPx: Int,
    anchorGapPx: Int,
    arrowSizePx: Int,
    arrowEdgePaddingPx: Int
): ItemActionMenuPlacement {
    val clampedLeft = (
        anchorBounds.centerX() - (popupContentSize.width / 2)
        ).coerceIn(
        minimumValue = windowMarginPx,
        maximumValue = (windowSize.width - popupContentSize.width - windowMarginPx)
            .coerceAtLeast(windowMarginPx)
    )

    val spaceAbove = anchorBounds.top - windowMarginPx
    val spaceBelow = windowSize.height - anchorBounds.bottom - windowMarginPx
    val requiredVerticalSpace = popupContentSize.height + anchorGapPx

    val verticalPlacement = when {
        spaceAbove >= requiredVerticalSpace -> ItemActionMenuVerticalPlacement.Above
        spaceBelow >= requiredVerticalSpace -> ItemActionMenuVerticalPlacement.Below
        spaceBelow > spaceAbove -> ItemActionMenuVerticalPlacement.Below
        else -> ItemActionMenuVerticalPlacement.Above
    }

    val popupTop = when (verticalPlacement) {
        ItemActionMenuVerticalPlacement.Above -> (
            anchorBounds.top - popupContentSize.height - anchorGapPx
            ).coerceAtLeast(windowMarginPx)

        ItemActionMenuVerticalPlacement.Below -> (
            anchorBounds.bottom + anchorGapPx
            ).coerceAtMost(
            (windowSize.height - popupContentSize.height - windowMarginPx)
                .coerceAtLeast(windowMarginPx)
        )
    }

    val desiredArrowOffset = anchorBounds.centerX() - clampedLeft - (arrowSizePx / 2)
    val arrowOffset = desiredArrowOffset.coerceIn(
        minimumValue = arrowEdgePaddingPx,
        maximumValue = (popupContentSize.width - arrowSizePx - arrowEdgePaddingPx)
            .coerceAtLeast(arrowEdgePaddingPx)
    )

    return ItemActionMenuPlacement(
        popupOffset = IntOffset(x = clampedLeft, y = popupTop),
        verticalPlacement = verticalPlacement,
        arrowOffsetPx = arrowOffset
    )
}

private fun IntRect.centerX(): Int {
    return left + ((right - left) / 2)
}

fun createUnpinAction(itemId: String): MenuAction {
    return MenuAction(
        label = "Unpin from home",
        icon = Icons.Filled.Delete,
        action = SearchResultAction.UnpinItem(itemId),
        isDestructive = true
    )
}

fun createPinAction(
    isPinned: Boolean,
    pinAction: SearchResultAction,
    unpinAction: SearchResultAction
): MenuAction {
    return if (isPinned) {
        MenuAction(
            label = "Unpin from home",
            icon = Icons.Filled.Delete,
            action = unpinAction,
            isDestructive = true
        )
    } else {
        MenuAction(
            label = "Pin to home",
            icon = Icons.Outlined.PushPin,
            action = pinAction
        )
    }
}

fun createAppInfoAction(packageName: String): MenuAction {
    return MenuAction(
        label = "App info",
        icon = Icons.Filled.Info,
        action = SearchResultAction.OpenAppInfo(packageName)
    )
}

fun createOpenWithAction(): MenuAction {
    return MenuAction(
        label = "Open with...",
        icon = Icons.AutoMirrored.Filled.OpenInNew,
        onClick = { }
    )
}
