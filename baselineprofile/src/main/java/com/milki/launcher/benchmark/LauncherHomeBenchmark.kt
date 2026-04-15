package com.milki.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
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
@OptIn(ExperimentalMetricApi::class)
class LauncherHomeBenchmark {

    private val startupMetrics = listOf(
        StartupTimingMetric(),
        TraceSectionMetric("launcher.startup.mainActivity.onCreate"),
        TraceSectionMetric("launcher.startup.runtime.initialize"),
        TraceSectionMetric("launcher.startup.setContent"),
        TraceSectionMetric("launcher.appsCatalog.queryLauncherActivities"),
        TraceSectionMetric("launcher.appsCatalog.preloadIcon"),
        TraceSectionMetric("launcher.appsCatalog.resolveLabel")
    )

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupToHomescreen() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = startupMetrics,
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
        metrics = startupMetrics,
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
    fun warmStartupToHomescreen() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = startupMetrics,
        compilationMode = LauncherBenchmarkConfig.startupCompilationMode,
        startupMode = StartupMode.WARM,
        iterations = LauncherBenchmarkConfig.startupIterations,
        setupBlock = {
            LauncherBenchmarkDriver(this).prepareForWarmStart(LauncherBenchmarkSurface.HOME)
        }
    ) {
        LauncherBenchmarkDriver(this).open(LauncherBenchmarkSurface.HOME)
    }

    @Test
    fun hotStartupToHomescreen() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = startupMetrics,
        compilationMode = LauncherBenchmarkConfig.startupCompilationMode,
        startupMode = StartupMode.HOT,
        iterations = LauncherBenchmarkConfig.startupIterations,
        setupBlock = {
            LauncherBenchmarkDriver(this).prepareForHotStart(LauncherBenchmarkSurface.HOME)
        }
    ) {
        LauncherBenchmarkDriver(this).open(LauncherBenchmarkSurface.HOME)
    }

    @Test
    fun openDrawerFromHomescreen() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = LauncherBenchmarkConfig.startupCompilationMode,
        iterations = LauncherBenchmarkConfig.transitionIterations,
        setupBlock = {
            LauncherBenchmarkDriver(this).moveTo(
                targetSurface = LauncherBenchmarkSurface.HOME,
                seedHome = true
            )
        }
    ) {
        LauncherBenchmarkDriver(this).open(LauncherBenchmarkSurface.DRAWER)
    }

    @Test
    fun returnToHomescreenFromDrawer() = benchmarkRule.measureRepeated(
        packageName = LauncherBenchmarkTargetApp.packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = LauncherBenchmarkConfig.startupCompilationMode,
        iterations = LauncherBenchmarkConfig.transitionIterations,
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
