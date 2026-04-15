package com.milki.launcher.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope

private const val PACKAGE_NAME = "com.milki.launcher"
private const val MAIN_ACTIVITY_NAME = "com.milki.launcher.app.activity.MainActivity"
private const val ACTION_BENCHMARK = "com.milki.launcher.action.BENCHMARK"
private const val EXTRA_BENCHMARK_TARGET = "com.milki.launcher.extra.BENCHMARK_TARGET"
private const val EXTRA_BENCHMARK_SEED_HOME = "com.milki.launcher.extra.BENCHMARK_SEED_HOME"
private const val EXTRA_BENCHMARK_DRAWER_QUERY = "com.milki.launcher.extra.BENCHMARK_DRAWER_QUERY"
private const val EXTRA_BENCHMARK_DRAWER_SCROLL_SEQUENCE = "com.milki.launcher.extra.BENCHMARK_DRAWER_SCROLL_SEQUENCE"

internal const val BENCHMARK_DRAWER_SCROLL_SEQUENCE_DOWN_UP = "DOWN_UP"

internal object LauncherBenchmarkConfig {
    const val startupIterations = 10
    const val transitionIterations = 15

    val startupCompilationMode: CompilationMode = CompilationMode.Partial()
}

internal enum class LauncherBenchmarkSurface {
    HOME,
    DRAWER
}

internal data class LauncherBenchmarkRequest(
    val targetSurface: LauncherBenchmarkSurface,
    val seedHome: Boolean = false,
    val drawerQuery: String? = null,
    val drawerScrollSequence: String? = null
)

internal enum class LauncherBenchmarkLaunchMode {
    RESET_TASK,
    IN_TASK
}

internal object LauncherBenchmarkTargetApp {
    const val packageName: String = PACKAGE_NAME

    private val mainActivity = ComponentName(PACKAGE_NAME, MAIN_ACTIVITY_NAME)

    fun intentFor(
        request: LauncherBenchmarkRequest,
        launchMode: LauncherBenchmarkLaunchMode = LauncherBenchmarkLaunchMode.RESET_TASK
    ): Intent {
        return Intent(ACTION_BENCHMARK).apply {
            component = mainActivity
            flags = when (launchMode) {
                LauncherBenchmarkLaunchMode.RESET_TASK -> {
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                LauncherBenchmarkLaunchMode.IN_TASK -> Intent.FLAG_ACTIVITY_NEW_TASK
            }
            putExtra(EXTRA_BENCHMARK_TARGET, request.targetSurface.name)
            putExtra(EXTRA_BENCHMARK_SEED_HOME, request.seedHome)
            request.drawerQuery?.let { query ->
                putExtra(EXTRA_BENCHMARK_DRAWER_QUERY, query)
            }
            request.drawerScrollSequence?.let { sequence ->
                putExtra(EXTRA_BENCHMARK_DRAWER_SCROLL_SEQUENCE, sequence)
            }
        }
    }
}

internal class LauncherBenchmarkDriver(
    private val scope: MacrobenchmarkScope
) {
    fun prepareForColdStart(targetSurface: LauncherBenchmarkSurface) {
        moveTo(targetSurface = targetSurface, seedHome = true)
        scope.pressHome()
        scope.killProcess()
    }

    fun prepareForWarmStart(targetSurface: LauncherBenchmarkSurface) {
        moveTo(targetSurface = targetSurface, seedHome = true)
        scope.pressHome()
    }

    fun prepareForHotStart(targetSurface: LauncherBenchmarkSurface) {
        moveTo(targetSurface = targetSurface, seedHome = true)
    }

    fun moveTo(targetSurface: LauncherBenchmarkSurface, seedHome: Boolean = false) {
        scope.pressHome()
        scope.startActivityAndWait(
            LauncherBenchmarkTargetApp.intentFor(
                LauncherBenchmarkRequest(
                    targetSurface = targetSurface,
                    seedHome = seedHome
                ),
                launchMode = LauncherBenchmarkLaunchMode.RESET_TASK
            )
        )
        scope.device.waitForIdle()
    }

    fun openForStartup(targetSurface: LauncherBenchmarkSurface) {
        scope.startActivityAndWait(
            LauncherBenchmarkTargetApp.intentFor(
                LauncherBenchmarkRequest(targetSurface = targetSurface),
                launchMode = LauncherBenchmarkLaunchMode.RESET_TASK
            )
        )
        scope.device.waitForIdle()
    }

    fun transitionTo(
        targetSurface: LauncherBenchmarkSurface,
        drawerQuery: String? = null,
        drawerScrollSequence: String? = null
    ) {
        scope.startActivityAndWait(
            LauncherBenchmarkTargetApp.intentFor(
                LauncherBenchmarkRequest(
                    targetSurface = targetSurface,
                    drawerQuery = drawerQuery,
                    drawerScrollSequence = drawerScrollSequence
                ),
                launchMode = LauncherBenchmarkLaunchMode.IN_TASK
            )
        )
        scope.device.waitForIdle()
    }
}
