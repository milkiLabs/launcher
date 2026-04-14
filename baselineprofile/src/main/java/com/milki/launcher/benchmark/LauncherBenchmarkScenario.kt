package com.milki.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope

internal object LauncherBenchmarkScenario {
    const val startupIterations = 10
    const val returnHomeIterations = 15

    val startupCompilationMode: CompilationMode = CompilationMode.Partial()
}

internal fun MacrobenchmarkScope.prepareSeededHomeForColdStart() {
    pressHome()
    startActivityAndWait(LauncherBenchmarkTarget.prepareHomeIntent())
    device.waitForIdle()
    pressHome()
    killProcess()
}

internal fun MacrobenchmarkScope.prepareSeededHomeFromDrawerState() {
    pressHome()
    startActivityAndWait(LauncherBenchmarkTarget.prepareHomeIntent())
    device.waitForIdle()
    startActivityAndWait(LauncherBenchmarkTarget.drawerIntent())
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openHomeAndAwaitIdle() {
    startActivityAndWait(LauncherBenchmarkTarget.homeIntent())
    device.waitForIdle()
}
