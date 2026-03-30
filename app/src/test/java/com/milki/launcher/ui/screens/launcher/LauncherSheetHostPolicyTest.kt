package com.milki.launcher.ui.screens.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSheetHostPolicyTest {

    @Test
    fun closed_target_and_unmounted_host_is_noop() {
        assertEquals(
            LauncherSheetTargetChange.None,
            resolveLauncherSheetTargetChange(targetOpen = false, isMounted = false)
        )
    }

    @Test
    fun open_target_mounts_and_animates_open() {
        assertEquals(
            LauncherSheetTargetChange.MountAndAnimateOpen,
            resolveLauncherSheetTargetChange(targetOpen = true, isMounted = false)
        )
    }

    @Test
    fun closing_mounted_sheet_animates_hidden_then_unmounts() {
        assertEquals(
            LauncherSheetTargetChange.AnimateClosedThenUnmount,
            resolveLauncherSheetTargetChange(targetOpen = false, isMounted = true)
        )
    }

    @Test
    fun reopened_sheet_uses_same_open_transition_again() {
        assertEquals(
            LauncherSheetTargetChange.MountAndAnimateOpen,
            resolveLauncherSheetTargetChange(targetOpen = true, isMounted = true)
        )
    }
}
