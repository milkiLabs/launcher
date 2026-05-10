package com.milki.launcher.data.widget

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import com.milki.launcher.domain.model.GridSpan

data class WidgetPickerEntry(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val span: GridSpan
)

data class WidgetAppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val widgets: List<WidgetPickerEntry>
)
