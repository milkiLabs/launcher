package com.milki.launcher.ui.components.launcher.widget

import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milki.launcher.data.widget.WidgetPickerEntry
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.ui.components.common.DrawableIcon
import com.milki.launcher.ui.components.common.WidgetPopupIcon
import com.milki.launcher.ui.interaction.dragdrop.startExternalWidgetDrag
import com.milki.launcher.ui.interaction.grid.GridConfig
import com.milki.launcher.ui.interaction.grid.detectDragGesture
import com.milki.launcher.ui.theme.CornerRadius
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

/**
 * Shared height for both widget card previews (inline drawable preview and the
 * popup-icon placeholder) so both drag options align in the row.
 */
private val WidgetPreviewHeight: Dp = 92.dp

@Composable
internal fun WidgetCard(
    entry: WidgetPickerEntry,
    onExternalDragStarted: () -> Unit = {}
) {
    val hostView = LocalView.current
    val shape = RoundedCornerShape(CornerRadius.large)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = Spacing.hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                shape = shape
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = Spacing.medium)
                )
                InfoPill(label = "${entry.span.columns} × ${entry.span.rows}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.Bottom
            ) {
                // Left: Inline (Preview)
                WidgetDragOptionColumn(
                    label = "Inline",
                    mode = WidgetDisplayMode.Inline,
                    entry = entry,
                    hostView = hostView,
                    onExternalDragStarted = onExternalDragStarted,
                    modifier = Modifier.weight(1f)
                ) {
                    WidgetPreview(
                        entry = entry,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Right: Popup (Icon)
                WidgetDragOptionColumn(
                    label = "Popup",
                    mode = WidgetDisplayMode.PopupIcon,
                    entry = entry,
                    hostView = hostView,
                    onExternalDragStarted = onExternalDragStarted,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WidgetPreviewHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        WidgetPopupIcon(
                            packageName = entry.providerInfo.provider.packageName,
                            size = IconSize.appHomeCompact,
                            label = entry.label
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetDragOptionColumn(
    label: String,
    mode: WidgetDisplayMode,
    entry: WidgetPickerEntry,
    hostView: View,
    onExternalDragStarted: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.detectDragGesture(
            key = "${entry.providerInfo.provider.packageName}/${entry.providerInfo.provider.className}-$mode",
            dragThreshold = GridConfig.Default.dragThresholdPx,
            onTap = {},
            onLongPress = {},
            onLongPressRelease = {},
            onDragStart = {
                val dragStarted = startExternalWidgetDrag(
                    hostView = hostView,
                    providerInfo = entry.providerInfo,
                    span = entry.span,
                    displayMode = mode,
                    dragShadowSize = IconSize.appHomeCompact
                )

                if (dragStarted) {
                    hostView.post(onExternalDragStarted)
                }
            },
            onDrag = { change, _ -> change.consume() },
            onDragEnd = {},
            onDragCancel = {}
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        content()

        Surface(
            shape = RoundedCornerShape(CornerRadius.extraLarge),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = Spacing.smallMedium,
                    vertical = Spacing.small
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                    modifier = Modifier.size(IconSize.small)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun WidgetPreview(
    entry: WidgetPickerEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewHeight = WidgetPreviewHeight

    val previewDrawable = remember(entry.providerInfo) {
        try {
            entry.providerInfo.loadPreviewImage(context, 0)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.large))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            )
            .height(previewHeight)
            .padding(Spacing.smallMedium)
    ) {
        if (previewDrawable != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            previewDrawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                            drawIntoCanvas { canvas ->
                                previewDrawable.draw(canvas.nativeCanvas)
                            }
                        }
                    }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WidgetAppIcon(
                    drawable = entry.appIcon,
                    label = entry.label,
                    size = IconSize.appList
                )
                Spacer(modifier = Modifier.height(Spacing.smallMedium))
                Text(
                    text = "Preview unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun WidgetAppIcon(
    drawable: Drawable?,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (drawable == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(CornerRadius.medium))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.standard)
            )
        }
    } else {
        DrawableIcon(
            drawable = drawable,
            modifier = modifier.clip(RoundedCornerShape(CornerRadius.medium)),
            size = size
        )
    }
}

@Composable
private fun InfoPill(label: String) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.small
            )
        )
    }
}
