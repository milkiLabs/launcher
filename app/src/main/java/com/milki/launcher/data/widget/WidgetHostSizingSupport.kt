package com.milki.launcher.data.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.WindowManager
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.ui.interaction.grid.GridConfig
import kotlin.math.roundToInt

/**
 * Context-bound widget sizing helpers.
 *
 * Pure provider-info decisions (min spans, configure detection) live in
 * domain/widget/WidgetSpanPolicy.kt; only helpers that need a Context or
 * framework constants belong here.
 */
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
