package com.milki.launcher.ui.screens.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.milki.launcher.ui.components.launcher.LauncherSheet
import com.milki.launcher.ui.components.launcher.LauncherSheetState
import com.milki.launcher.ui.components.launcher.launcherSheetDragHandle
import kotlinx.coroutines.CancellationException

@Composable
internal fun LauncherSurfaceSheetHost(
    isOpen: Boolean,
    sheetState: LauncherSheetState,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit = {},
    keepMountedWhenClosed: Boolean = false,
    content: @Composable (Modifier) -> Unit
) {
    ManagedLauncherSheet(
        isOpen = isOpen,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        onClosed = onClosed,
        keepMountedWhenClosed = keepMountedWhenClosed
    ) {
        content(
            Modifier.launcherSheetDragHandle(
                state = sheetState,
                onDismissedByUser = onDismissRequest
            )
        )
    }
}

@Composable
private fun ManagedLauncherSheet(
    isOpen: Boolean,
    sheetState: LauncherSheetState,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit = {},
    keepMountedWhenClosed: Boolean = false,
    content: @Composable () -> Unit
) {
    var isMounted by remember { mutableStateOf(isOpen) }
    val latestIsOpen by rememberUpdatedState(isOpen)

    LaunchedEffect(isOpen) {
        when (resolveLauncherSheetTargetChange(targetOpen = isOpen, isMounted = isMounted)) {
            LauncherSheetTargetChange.MountAndAnimateOpen -> {
                isMounted = true
                sheetState.animateToExpanded()
            }

            LauncherSheetTargetChange.AnimateClosedThenUnmount -> {
                var closeAnimationInterrupted = false
                try {
                    sheetState.animateToHidden()
                } catch (_: CancellationException) {
                    closeAnimationInterrupted = true
                }

                if (!latestIsOpen) {
                    if (closeAnimationInterrupted) {
                        try {
                            sheetState.snapToHidden()
                        } catch (_: CancellationException) {
                            if (latestIsOpen) {
                                return@LaunchedEffect
                            }
                        }
                    }
                    if (!keepMountedWhenClosed) {
                        isMounted = false
                    }
                    onClosed()
                }
            }

            LauncherSheetTargetChange.None -> Unit
        }
    }

    if (!isMounted) return

    LauncherSheet(
        state = sheetState,
        modifier = Modifier,
        onDismissedByUser = onDismissRequest
    ) {
        content()
    }
}
