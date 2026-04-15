package com.milki.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LauncherHomeBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupToHomescreen() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = LauncherBenchmarkConfig.startupCompilationMode,
        startupMode = StartupMode.COLD,
        iterations = LauncherBenchmarkConfig.startupIterations,
        setupBlock = {
            LauncherBenchmarkDriver(this).prepareForColdStart(LauncherBenchmarkSurface.HOME)
        }
    ) {
        LauncherBenchmarkDriver(this).open(LauncherBenchmarkSurface.HOME)
    }

    @Test
    fun coldStartupToHomescreenWithoutBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3
        ),
        startupMode = StartupMode.COLD,
        iterations = LauncherBenchmarkConfig.startupIterations,
        setupBlock = {
            LauncherBenchmarkDriver(this).prepareForColdStart(LauncherBenchmarkSurface.HOME)
        }
    ) {
        LauncherBenchmarkDriver(this).open(LauncherBenchmarkSurface.HOME)
    }

    @Test
    fun returnToHomescreenFromDrawer() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = LauncherBenchmarkConfig.startupCompilationMode,
        iterations = LauncherBenchmarkConfig.returnHomeIterations,
        setupBlock = {
            LauncherBenchmarkDriver(this).moveTo(
                targetSurface = LauncherBenchmarkSurface.DRAWER,
                seedHome = true
            )
        }
    ) {
        LauncherBenchmarkDriver(this).open(LauncherBenchmarkSurface.HOME)
    }
}
