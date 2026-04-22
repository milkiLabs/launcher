package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import com.milki.launcher.ui.components.common.IconLabelLayout
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

internal fun homeItemIconLabelLayout(
    compact: Boolean,
    regularContentVerticalPadding: Dp
): IconLabelLayout {
    return if (compact) {
        IconLabelLayout(
            iconSize = IconSize.appHomeCompact,
            contentPadding = PaddingValues(vertical = Spacing.none, horizontal = Spacing.none),
            labelTopPadding = Spacing.none,
            labelMaxLines = 1
        )
    } else {
        IconLabelLayout(
            iconSize = IconSize.appGrid,
            contentPadding = PaddingValues(
                vertical = regularContentVerticalPadding,
                horizontal = Spacing.none
            ),
            labelTopPadding = Spacing.smallMedium,
            labelMaxLines = 1
        )
    }
}