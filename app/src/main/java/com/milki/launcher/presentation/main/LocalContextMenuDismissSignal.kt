package com.milki.launcher.presentation.main

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Monotonically increasing signal used to dismiss transient item action menus.
 *
 * Any menu host can observe this value and dismiss itself when it changes.
 */
val LocalContextMenuDismissSignal: ProvidableCompositionLocal<Int> = compositionLocalOf {
    0
}
