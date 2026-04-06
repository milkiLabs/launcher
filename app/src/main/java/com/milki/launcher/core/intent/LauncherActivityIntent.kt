package com.milki.launcher.core.intent

import android.content.ComponentName
import android.content.Intent

const val ACTION_BENCHMARK_OPEN_HOME = "com.milki.launcher.action.BENCHMARK_OPEN_HOME"
const val ACTION_BENCHMARK_OPEN_DRAWER = "com.milki.launcher.action.BENCHMARK_OPEN_DRAWER"

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
