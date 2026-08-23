package com.milki.launcher.ui.components.launcher.folder

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.milki.launcher.ui.components.common.OverlayScrim
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.Spacing
import com.milki.launcher.ui.util.center
import com.milki.launcher.ui.util.lerp
import com.milki.launcher.ui.util.windowRect
import kotlin.math.roundToInt

private const val FOLDER_SCRIM_BASE_ALPHA = 0.18f
private const val FOLDER_SCRIM_PROGRESS_ALPHA = 0.34f
private const val FOLDER_SURFACE_BASE_ALPHA = 0.82f
private const val FOLDER_SURFACE_FINAL_ALPHA = 1f
private const val FOLDER_MIN_START_SCALE = 0.28f
private const val FOLDER_OPEN_ANIMATION_MS = 240

internal data class FolderSurfaceTransform(
    val anchorCenter: Offset,
    val targetCenter: Offset,
    val startScaleX: Float,
    val startScaleY: Float
)

@Composable
internal fun rememberFolderOpenProgress(): Float {
    var isEntering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isEntering = true
    }

    val openProgress by animateFloatAsState(
        targetValue = if (isEntering) 1f else 0f,
        animationSpec = tween(
            durationMillis = FOLDER_OPEN_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "folderOpenProgress"
    )
    return openProgress
}

@Composable
internal fun rememberFolderSurfaceMetrics(
    localChildrenSize: Int,
    layout: FolderGridLayout,
    density: Density,
    maxWidth: Dp,
    maxHeight: Dp
): FolderSurfaceMetrics {
    return remember(
        localChildrenSize,
        layout,
        maxWidth,
        maxHeight,
        density
    ) {
        FolderSurfaceMetrics.create(
            density = density,
            layout = layout,
            pageCount = layout.pageCount,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
    }
}

@Composable
internal fun rememberFolderTargetBounds(
    anchorBounds: Rect?,
    metrics: FolderSurfaceMetrics,
    density: Density,
    maxWidth: Dp,
    maxHeight: Dp
): Rect {
    return remember(
        anchorBounds,
        metrics.surfaceWidth,
        metrics.surfaceHeight,
        maxWidth,
        maxHeight,
        density
    ) {
        resolveFolderTargetBounds(
            density = density,
            surfaceWidth = metrics.surfaceWidth,
            surfaceHeight = metrics.surfaceHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            anchorBounds = anchorBounds
        )
    }
}

@Composable
internal fun rememberFolderSurfaceTransform(
    anchorBounds: Rect?,
    targetBoundsPx: Rect
): FolderSurfaceTransform {
    return remember(anchorBounds, targetBoundsPx) {
        val fallbackAnchorBounds = targetBoundsPx
        val resolvedAnchorBounds = anchorBounds ?: fallbackAnchorBounds
        val anchorCenter = resolvedAnchorBounds.center()
        val targetCenter = targetBoundsPx.center()

        val startScaleX = (resolvedAnchorBounds.width / targetBoundsPx.width)
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtLeast(FOLDER_MIN_START_SCALE)
            ?: 1f
        val startScaleY = (resolvedAnchorBounds.height / targetBoundsPx.height)
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtLeast(FOLDER_MIN_START_SCALE)
            ?: 1f

        FolderSurfaceTransform(
            anchorCenter = anchorCenter,
            targetCenter = targetCenter,
            startScaleX = startScaleX,
            startScaleY = startScaleY
        )
    }
}

@Composable
internal fun FolderPopupScrim(
    openProgress: Float,
    onClose: () -> Unit
) {
    OverlayScrim(
        onClose = onClose,
        alpha = FOLDER_SCRIM_BASE_ALPHA + (FOLDER_SCRIM_PROGRESS_ALPHA * openProgress)
    )
}

@Composable
internal fun FolderPopupSurface(
    metrics: FolderSurfaceMetrics,
    targetBoundsPx: Rect,
    transform: FolderSurfaceTransform,
    openProgress: Float,
    onPopupBoundsMeasured: (Rect) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = targetBoundsPx.left.roundToInt(),
                    y = targetBoundsPx.top.roundToInt()
                )
            }
            .size(
                width = metrics.surfaceWidth,
                height = metrics.surfaceHeight
            )
            .graphicsLayer {
                val startTranslation = transform.anchorCenter - transform.targetCenter
                translationX = startTranslation.x * (1f - openProgress)
                translationY = startTranslation.y * (1f - openProgress)
                scaleX = lerp(transform.startScaleX, 1f, openProgress)
                scaleY = lerp(transform.startScaleY, 1f, openProgress)
                alpha = lerp(FOLDER_SURFACE_BASE_ALPHA, FOLDER_SURFACE_FINAL_ALPHA, openProgress)
            }
            .shadow(
                elevation = Spacing.mediumLarge,
                shape = RoundedCornerShape(CornerRadius.large),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            .onGloballyPositioned { coords ->
                onPopupBoundsMeasured(coords.windowRect())
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        shape = RoundedCornerShape(CornerRadius.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none)
    ) {
        content()
    }
}
