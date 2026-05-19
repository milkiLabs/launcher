package com.milki.launcher.ui.components.launcher

import androidx.compose.foundation.layout.PaddingValues
import com.milki.launcher.ui.components.common.IconLabelLayout
import com.milki.launcher.ui.theme.IconSize
import com.milki.launcher.ui.theme.Spacing

internal fun homeItemIconLabelLayout(): IconLabelLayout {
    return IconLabelLayout(
        iconSize = IconSize.appHomeCompact,
        contentPadding = PaddingValues(vertical = Spacing.none, horizontal = Spacing.none),
        labelTopPadding = Spacing.none,
        labelMaxLines = 2
    )
}
