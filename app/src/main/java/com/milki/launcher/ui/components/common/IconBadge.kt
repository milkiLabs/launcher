package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared badge metrics used by all icon badge overlays.
 *
 * These ratios produce a visually balanced badge at any icon size:
 * - Badge occupies ~34% of the parent icon
 * - Internal padding is ~12% of the badge size
 * - Minimum badge size is 16dp to remain legible
 */
object IconBadgeMetrics {
    const val SIZE_RATIO = 0.34f
    const val PADDING_RATIO = 0.12f
    val MIN_SIZE = 16.dp
    val MIN_PADDING = 2.dp
}

/**
 * Computes badge size from a parent icon size.
 */
fun badgeSize(iconSize: Dp): Dp =
    (iconSize * IconBadgeMetrics.SIZE_RATIO).coerceAtLeast(IconBadgeMetrics.MIN_SIZE)

/**
 * Computes inner badge padding from the computed badge size.
 */
fun badgePadding(badgeSize: Dp): Dp =
    (badgeSize * IconBadgeMetrics.PADDING_RATIO).coerceAtLeast(IconBadgeMetrics.MIN_PADDING)

/**
 * A reusable badge surface positioned at the bottom-end of a [Box] parent.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.size(iconSize)) {
 *     // ... main icon content ...
 *     IconBadge {
 *         // badge content (icon, text, etc.)
 *     }
 * }
 * ```
 *
 * @param iconSize The size of the parent icon (used to compute badge dimensions)
 * @param containerColor Background color of the badge
 * @param contentColor Content color passed to the badge content lambda
 * @param content The composable shown inside the badge
 */
@Composable
fun BoxScope.IconBadge(
    iconSize: Dp,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    val size = badgeSize(iconSize)
    val padding = badgePadding(size)

    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(size),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.size(size - (padding * 2)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
