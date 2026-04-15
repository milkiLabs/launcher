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
        packageName = LauncherBenchmarkTargetApp.packageName,
        includeInStartupProfile = true
    ) {
        val benchmarkDriver = LauncherBenchmarkDriver(this)
        benchmarkDriver.moveTo(
            targetSurface = LauncherBenchmarkSurface.HOME,
            seedHome = true
        )

        repeat(3) {
            benchmarkDriver.transitionTo(LauncherBenchmarkSurface.HOME)
            benchmarkDriver.transitionTo(LauncherBenchmarkSurface.DRAWER)
            benchmarkDriver.transitionTo(LauncherBenchmarkSurface.HOME)
        }
    }
}
