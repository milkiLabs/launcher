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
        packageName = LauncherBenchmarkTarget.packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = LauncherBenchmarkScenario.startupCompilationMode,
        startupMode = StartupMode.COLD,
        iterations = LauncherBenchmarkScenario.startupIterations,
        setupBlock = {
            prepareSeededHomeForColdStart()
        }
    ) {
        openHomeAndAwaitIdle()
    }

    @Test
    fun coldStartupToHomescreenWithoutBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTarget.packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3
        ),
        startupMode = StartupMode.COLD,
        iterations = LauncherBenchmarkScenario.startupIterations,
        setupBlock = {
            prepareSeededHomeForColdStart()
        }
    ) {
        openHomeAndAwaitIdle()
    }

    @Test
    fun returnToHomescreenFromDrawer() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTarget.packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = LauncherBenchmarkScenario.startupCompilationMode,
        iterations = LauncherBenchmarkScenario.returnHomeIterations,
        setupBlock = {
            prepareSeededHomeFromDrawerState()
        }
    ) {
        openHomeAndAwaitIdle()
    }
}
