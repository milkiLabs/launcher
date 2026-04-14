package com.milki.launcher.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LauncherBaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = LauncherBenchmarkTarget.packageName,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait(LauncherBenchmarkTarget.prepareHomeIntent())
        device.waitForIdle()

        repeat(3) {
            openHomeAndAwaitIdle()

            startActivityAndWait(LauncherBenchmarkTarget.drawerIntent())
            device.waitForIdle()

            openHomeAndAwaitIdle()
        }
    }
}
