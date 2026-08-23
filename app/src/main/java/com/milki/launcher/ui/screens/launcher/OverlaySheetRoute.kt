package com.milki.launcher.ui.screens.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.milki.launcher.ui.components.launcher.LauncherSheetState

/**
 * Generic bottom-sheet overlay route host shared by drawer, widget picker,
 * and shortcut manager overlays.
 *
 * Sheets opened via navigation are mounted open and dismiss by routing
 * [setOpen](false) back through the owning action contract.
 */
@Composable
internal fun OverlaySheetRoute(
    sheetState: LauncherSheetState,
    setOpen: (Boolean) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    LauncherSurfaceSheetHost(
        isOpen = true,
        sheetState = sheetState,
        onDismissRequest = { setOpen(false) }
    ) { dragHandleModifier ->
        content(dragHandleModifier)
    }
}
