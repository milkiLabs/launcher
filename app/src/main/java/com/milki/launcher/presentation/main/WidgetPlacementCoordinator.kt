package com.milki.launcher.presentation.main

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.presentation.home.HomeViewModel

class WidgetPlacementCoordinator(
    private val activity: ComponentActivity,
    private val homeViewModel: HomeViewModel,
    private val widgetHostManager: WidgetHostManager
) {

    companion object {
        private const val TAG = "WidgetPlacementCoord"
        private const val REQUEST_CONFIGURE_APPWIDGET = 10_401
    }

    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>
    private var pendingBindAppWidgetId: Int? = null
    private var pendingConfigureAppWidgetId: Int? = null

    fun initialize() {
        widgetBindLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val appWidgetId = pendingBindAppWidgetId
            pendingBindAppWidgetId = null
            if (appWidgetId == null) return@registerForActivityResult

            execute(
                homeViewModel.handleWidgetBindResult(
                    resultCode = result.resultCode,
                    widgetHostManager = widgetHostManager,
                    appWidgetId = appWidgetId
                )
            )
        }
    }

    fun execute(command: HomeViewModel.WidgetPlacementCommand) {
        when (command) {
            is HomeViewModel.WidgetPlacementCommand.LaunchBindPermission -> launchBindPermission(command)
            is HomeViewModel.WidgetPlacementCommand.LaunchConfigure -> launchConfigure(command)
            HomeViewModel.WidgetPlacementCommand.NoOp -> Unit
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CONFIGURE_APPWIDGET) return false

        val appWidgetId = pendingConfigureAppWidgetId
        pendingConfigureAppWidgetId = null
        if (appWidgetId == null) return true

        execute(
            homeViewModel.handleWidgetConfigureResult(
                resultCode = resultCode,
                widgetHostManager = widgetHostManager,
                appWidgetId = appWidgetId
            )
        )
        return true
    }

    private fun launchBindPermission(command: HomeViewModel.WidgetPlacementCommand.LaunchBindPermission) {
        runCatching {
            pendingBindAppWidgetId = command.appWidgetId
            widgetBindLauncher.launch(command.intent)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to launch widget bind permission flow", throwable)
            execute(
                homeViewModel.handleWidgetBindResult(
                    resultCode = Activity.RESULT_CANCELED,
                    widgetHostManager = widgetHostManager,
                    appWidgetId = command.appWidgetId
                )
            )
        }
    }

    private fun launchConfigure(command: HomeViewModel.WidgetPlacementCommand.LaunchConfigure) {
        runCatching {
            pendingConfigureAppWidgetId = command.appWidgetId
            widgetHostManager.startConfigureActivityForResult(
                activity = activity,
                appWidgetId = command.appWidgetId,
                requestCode = REQUEST_CONFIGURE_APPWIDGET
            )
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to launch widget configure activity", throwable)
            execute(
                homeViewModel.handleWidgetConfigureResult(
                    resultCode = Activity.RESULT_CANCELED,
                    widgetHostManager = widgetHostManager,
                    appWidgetId = command.appWidgetId
                )
            )
        }
    }
}
