package com.milki.launcher.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.ui.theme.Spacing

/**
 * A reusable fixed grid layout for rendering a list of apps with a strict column count.
 * This ensures consistency between App Drawer Recent Apps, Search App Results, etc.
 */
@Composable
fun FixedAppGrid(
    apps: List<AppInfo>,
    columns: Int,
    onAppClick: (AppInfo) -> Unit,
    onExternalDragStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
    fillFromRight: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        val rowAppsList = if (fillFromRight) apps.chunked(columns).asReversed() else apps.chunked(columns)

        rowAppsList.forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                val emptySlots = (columns - rowApps.size).coerceAtLeast(0)
                if (fillFromRight) {
                    repeat(emptySlots) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                val displayedApps = if (fillFromRight) rowApps.reversed() else rowApps
                displayedApps.forEach { app ->
                    Box(modifier = Modifier.weight(1f)) {
                        AppGridItem(
                            appInfo = app,
                            onClick = { onAppClick(app) },
                            onExternalDragStarted = onExternalDragStarted
                        )
                    }
                }
                if (!fillFromRight) {
                    repeat(emptySlots) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
