package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.milki.launcher.ui.theme.Spacing

data class IconLabelLayout(
    val iconSize: Dp,
    val contentPadding: PaddingValues = PaddingValues(
        vertical = Spacing.extraSmall,
        horizontal = Spacing.none
    ),
    val labelTopPadding: Dp = Spacing.smallMedium,
    val labelMaxLines: Int = 1
)

@Composable
fun IconLabelCell(
    label: String,
    layout: IconLabelLayout,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall,
    labelOverflow: TextOverflow = TextOverflow.Ellipsis,
    labelTextAlign: TextAlign = TextAlign.Center,
    iconContent: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(layout.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(layout.iconSize),
            contentAlignment = Alignment.Center,
            content = iconContent
        )

        Text(
            text = label,
            style = labelStyle,
            color = labelColor,
            maxLines = layout.labelMaxLines,
            overflow = labelOverflow,
            textAlign = labelTextAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = layout.labelTopPadding)
        )
    }
}
