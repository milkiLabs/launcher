package com.milki.launcher.benchmark

import android.content.ComponentName
import android.content.Intent

private const val PACKAGE_NAME = "com.milki.launcher"
private const val MAIN_ACTIVITY_NAME = "com.milki.launcher.app.activity.MainActivity"
private const val ACTION_BENCHMARK_OPEN_HOME = "com.milki.launcher.action.BENCHMARK_OPEN_HOME"
private const val ACTION_BENCHMARK_OPEN_DRAWER = "com.milki.launcher.action.BENCHMARK_OPEN_DRAWER"
private const val ACTION_BENCHMARK_PREPARE_HOME = "com.milki.launcher.action.BENCHMARK_PREPARE_HOME"

internal object LauncherBenchmarkTarget {
    const val packageName: String = PACKAGE_NAME

    private val mainActivity = ComponentName(PACKAGE_NAME, MAIN_ACTIVITY_NAME)

    fun homeIntent(): Intent {
        return benchmarkIntent(ACTION_BENCHMARK_OPEN_HOME)
    }

    fun drawerIntent(): Intent {
        return benchmarkIntent(ACTION_BENCHMARK_OPEN_DRAWER)
    }

    fun prepareHomeIntent(): Intent {
        return benchmarkIntent(ACTION_BENCHMARK_PREPARE_HOME)
    }

    private fun benchmarkIntent(action: String): Intent {
        return Intent(action).apply {
            component = mainActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }
}
