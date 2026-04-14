package com.milki.launcher.core.intent

import android.content.ComponentName
import android.content.Intent

const val ACTION_BENCHMARK_OPEN_HOME = "com.milki.launcher.action.BENCHMARK_OPEN_HOME"
const val ACTION_BENCHMARK_OPEN_DRAWER = "com.milki.launcher.action.BENCHMARK_OPEN_DRAWER"
const val ACTION_BENCHMARK_PREPARE_HOME = "com.milki.launcher.action.BENCHMARK_PREPARE_HOME"

enum class LauncherBenchmarkAction {
    OPEN_HOME,
    OPEN_DRAWER,
    PREPARE_HOME
}

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

fun Intent.isBenchmarkOpenHomeIntent(): Boolean {
    return action == ACTION_BENCHMARK_OPEN_HOME
}

fun Intent.isBenchmarkOpenDrawerIntent(): Boolean {
    return action == ACTION_BENCHMARK_OPEN_DRAWER
}

fun Intent.isBenchmarkPrepareHomeIntent(): Boolean {
    return action == ACTION_BENCHMARK_PREPARE_HOME
}

fun Intent.toLauncherBenchmarkActionOrNull(): LauncherBenchmarkAction? {
    return when (action) {
        ACTION_BENCHMARK_OPEN_HOME -> LauncherBenchmarkAction.OPEN_HOME
        ACTION_BENCHMARK_OPEN_DRAWER -> LauncherBenchmarkAction.OPEN_DRAWER
        ACTION_BENCHMARK_PREPARE_HOME -> LauncherBenchmarkAction.PREPARE_HOME
        else -> null
    }
}
