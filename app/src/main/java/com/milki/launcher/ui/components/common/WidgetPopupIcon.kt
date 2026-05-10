package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.milki.launcher.domain.model.HomeItem

private const val WIDGET_BADGE_SIZE_RATIO = 0.34f
private const val WIDGET_BADGE_PADDING_RATIO = 0.12f

@Composable
fun WidgetPopupIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp,
    label: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(size)
        ) {
        AppIcon(
            packageName = packageName,
            size = size,
            modifier = Modifier.matchParentSize()
        )

        WidgetBadge(iconSize = size)
    }

    if (label != null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                lineHeight = TextUnit(14f, TextUnitType.Sp)
            ),
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.wrapContentHeight(unbounded = true)
        )
    }
}
}

@Composable
private fun BoxScope.WidgetBadge(
    iconSize: Dp
) {
    val badgeSize = (iconSize * WIDGET_BADGE_SIZE_RATIO).coerceAtLeast(16.dp)
    val badgePadding = (badgeSize * WIDGET_BADGE_PADDING_RATIO).coerceAtLeast(2.dp)

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
            Icon(
                imageVector = Icons.Filled.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(badgeSize - (badgePadding * 2))
            )
        }
    }
}
