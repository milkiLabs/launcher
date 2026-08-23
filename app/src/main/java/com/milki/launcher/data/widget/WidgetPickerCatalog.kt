package com.milki.launcher.data.widget

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import com.milki.launcher.domain.model.GridSpan

/**
 * Widget catalog models hold [Drawable.ConstantState] instead of live
 * [Drawable] instances so the cached catalog does not pin process-wide
 * drawable resources; callers inflate fresh drawables via [Drawable.newDrawable].
 */

data class WidgetPickerEntry(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val appIcon: Drawable.ConstantState?,
    val span: GridSpan
)

data class WidgetAppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable.ConstantState?,
    val widgets: List<WidgetPickerEntry>
)
