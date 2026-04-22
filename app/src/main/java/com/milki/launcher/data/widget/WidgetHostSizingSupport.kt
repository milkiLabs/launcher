package com.milki.launcher.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.WindowManager
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.ui.interaction.grid.GridConfig
import kotlin.math.roundToInt

private const val WIDGET_CELL_PADDING_DP = 30
private const val WIDGET_CELL_SIZE_DP = 70

internal fun calculateMinWidgetSpan(providerInfo: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetCols = providerInfo.targetCellWidth
        val targetRows = providerInfo.targetCellHeight
        if (targetCols > 0 && targetRows > 0) {
            return targetCols to targetRows
        }
    }

    return dpToCells(providerInfo.minWidth) to dpToCells(providerInfo.minHeight)
}

internal fun calculateMinWidgetResizeSpan(providerInfo: AppWidgetProviderInfo): Pair<Int, Int> {
    val minResizeWidth = providerInfo.minResizeWidth
    val minResizeHeight = providerInfo.minResizeHeight

    return if (minResizeWidth <= 0 || minResizeHeight <= 0) {
        calculateMinWidgetSpan(providerInfo)
    } else {
        dpToCells(minResizeWidth) to dpToCells(minResizeHeight)
    }
}

internal fun needsInitialWidgetConfigure(providerInfo: AppWidgetProviderInfo): Boolean {
    val featureFlags = providerInfo.widgetFeatures
    val isOptionalConfiguration =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (featureFlags and WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
            (featureFlags and WIDGET_FEATURE_RECONFIGURABLE) != 0

    return providerInfo.configure != null &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isOptionalConfiguration)
}

internal fun createWidgetSizeOptions(
    context: Context,
    widthPx: Int,
    heightPx: Int
): Bundle {
    val widthDp = pxToDp(context, widthPx)
    val heightDp = pxToDp(context, heightPx)

    return Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
            )
        }
    }
}

internal fun estimateWidgetSizePx(
    context: Context,
    span: GridSpan
): Pair<Int, Int> {
    val displayMetrics = context.resources.displayMetrics
    val windowWidthPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowManager = context.getSystemService(WindowManager::class.java)
        windowManager?.currentWindowMetrics?.bounds?.width() ?: displayMetrics.widthPixels
    } else {
        displayMetrics.widthPixels
    }
    val cellSizePx = windowWidthPx.toFloat() / GridConfig.Default.columns

    return (cellSizePx * span.columns).roundToInt().coerceAtLeast(1) to
        (cellSizePx * span.rows).roundToInt().coerceAtLeast(1)
}

internal fun pxToDp(context: Context, px: Int): Int {
    return (px / context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
}

private fun dpToCells(dp: Int): Int {
    return ((dp - WIDGET_CELL_PADDING_DP) / WIDGET_CELL_SIZE_DP + 1).coerceAtLeast(1)
}
