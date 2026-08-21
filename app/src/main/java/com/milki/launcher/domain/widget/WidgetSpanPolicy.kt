package com.milki.launcher.domain.widget

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import android.os.Build
import com.milki.launcher.domain.model.GridSpan
import kotlin.math.roundToInt

private const val WIDGET_CELL_PADDING_DP = 30
private const val WIDGET_CELL_SIZE_DP = 70

/**
 * Resolves a widget provider's minimum footprint in home-grid cells.
 *
 * Prefers the provider's targetCellWidth/Height (API 31+) and falls back to
 * converting minWidth/minHeight dp into cells using the classic launcher
 * formula: cells = (dp - padding) / cellSize + 1.
 */
fun calculateMinWidgetSpan(providerInfo: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetCols = providerInfo.targetCellWidth
        val targetRows = providerInfo.targetCellHeight
        if (targetCols > 0 && targetRows > 0) {
            return targetCols to targetRows
        }
    }

    return dpToCells(providerInfo.minWidth) to dpToCells(providerInfo.minHeight)
}

/** Resolves how small the provider allows itself to be resized, in grid cells. */
fun calculateMinWidgetResizeSpan(providerInfo: AppWidgetProviderInfo): Pair<Int, Int> {
    val minResizeWidth = providerInfo.minResizeWidth
    val minResizeHeight = providerInfo.minResizeHeight

    return if (minResizeWidth <= 0 || minResizeHeight <= 0) {
        calculateMinWidgetSpan(providerInfo)
    } else {
        dpToCells(minResizeWidth) to dpToCells(minResizeHeight)
    }
}

/**
 * Returns true when the provider must be configured before it can appear,
 * mirroring the framework's own launcher behavior for optional configuration.
 */
fun needsInitialWidgetConfigure(providerInfo: AppWidgetProviderInfo): Boolean {
    val featureFlags = providerInfo.widgetFeatures
    val isOptionalConfiguration =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (featureFlags and WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
            (featureFlags and WIDGET_FEATURE_RECONFIGURABLE) != 0

    return providerInfo.configure != null &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isOptionalConfiguration)
}

private fun dpToCells(dp: Int): Int {
    return ((dp - WIDGET_CELL_PADDING_DP) / WIDGET_CELL_SIZE_DP + 1).coerceAtLeast(1)
}

/**
 * Produces a launcher-friendly default size for newly placed widgets.
 *
 * The provider-reported span is a useful starting point, but some widgets report
 * very large defaults that make initial placement awkward. This policy keeps
 * compact widgets unchanged while shrinking oversized widgets into a practical
 * first placement that users can still manually enlarge later.
 */
fun recommendWidgetPlacementSpan(
    rawSpan: GridSpan,
    gridColumns: Int,
    maxDefaultRows: Int = 3,
    maxDefaultArea: Int = (gridColumns * 2) + 2
): GridSpan {
    require(gridColumns >= 1) { "gridColumns must be at least 1" }

    var columns = rawSpan.columns.coerceAtLeast(1)
    var rows = rawSpan.rows.coerceAtLeast(1)

    if (columns > gridColumns) {
        val widthScale = gridColumns.toFloat() / columns.toFloat()
        columns = gridColumns
        rows = (rows * widthScale).roundToInt().coerceAtLeast(1)
    }

    if (rows > maxDefaultRows) {
        val heightScale = maxDefaultRows.toFloat() / rows.toFloat()
        rows = maxDefaultRows
        columns = (columns * heightScale).roundToInt().coerceAtLeast(1)
    }

    val safeMaxArea = maxDefaultArea.coerceAtLeast(1)
    while (columns * rows > safeMaxArea && (columns > 1 || rows > 1)) {
        when {
            rows > 2 -> rows -= 1
            columns > 1 -> columns -= 1
            rows > 1 -> rows -= 1
        }
    }

    return GridSpan(
        columns = columns.coerceIn(1, gridColumns),
        rows = rows.coerceIn(1, maxDefaultRows)
    )
}
