package com.milki.launcher.core.intent

import android.content.ComponentName
import android.content.Intent

const val ACTION_BENCHMARK = "com.milki.launcher.action.BENCHMARK"
const val EXTRA_BENCHMARK_TARGET = "com.milki.launcher.extra.BENCHMARK_TARGET"
const val EXTRA_BENCHMARK_SEED_HOME = "com.milki.launcher.extra.BENCHMARK_SEED_HOME"

enum class LauncherBenchmarkTarget {
    HOME,
    DRAWER
}

data class LauncherBenchmarkRequest(
    val target: LauncherBenchmarkTarget,
    val seedHome: Boolean = false
)

/**
 * Creates an explicit launcher intent for a specific launcher activity component.
 */
fun createLauncherActivityIntent(componentName: ComponentName): Intent {
    return Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = componentName
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
}

fun Intent.toLauncherBenchmarkRequestOrNull(): LauncherBenchmarkRequest? {
    if (action != ACTION_BENCHMARK) {
        return null
    }

    return parseLauncherBenchmarkRequest(
        targetName = getStringExtra(EXTRA_BENCHMARK_TARGET),
        seedHome = getBooleanExtra(EXTRA_BENCHMARK_SEED_HOME, false)
    )
}

internal fun parseLauncherBenchmarkRequest(
    targetName: String?,
    seedHome: Boolean
): LauncherBenchmarkRequest? {
    val target = when (targetName) {
        LauncherBenchmarkTarget.HOME.name -> LauncherBenchmarkTarget.HOME
        LauncherBenchmarkTarget.DRAWER.name -> LauncherBenchmarkTarget.DRAWER
        else -> return null
    }

    return LauncherBenchmarkRequest(
        target = target,
        seedHome = seedHome
    )
}
