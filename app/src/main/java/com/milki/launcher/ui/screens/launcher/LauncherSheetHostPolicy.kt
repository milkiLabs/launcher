package com.milki.launcher.ui.screens.launcher

internal enum class LauncherSheetTargetChange {
    MountAndAnimateOpen,
    AnimateClosedThenUnmount,
    None
}

internal fun resolveLauncherSheetTargetChange(
    targetOpen: Boolean,
    isMounted: Boolean
): LauncherSheetTargetChange {
    return when {
        targetOpen -> LauncherSheetTargetChange.MountAndAnimateOpen
        isMounted -> LauncherSheetTargetChange.AnimateClosedThenUnmount
        else -> LauncherSheetTargetChange.None
    }
}
