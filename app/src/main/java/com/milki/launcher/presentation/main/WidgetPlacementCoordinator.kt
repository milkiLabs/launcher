package com.milki.launcher.presentation.main

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.presentation.home.HomeViewModel

/**
 * WidgetPlacementCoordinator.kt - Activity-side coordinator for widget bind/configure launchers.
 *
 * WHY THIS FILE EXISTS:
 * HomeViewModel already owns widget placement state transitions and emits
 * WidgetPlacementCommand values. MainActivity should only dispatch those commands.
 *
 * Previously, MainActivity mixed launcher registration + command dispatch + result routing
 * directly in Activity methods. This coordinator extracts that orchestration so Activity
 * remains focused on lifecycle hosting.
 */
interface WidgetPlacementCoordinator {

    /**
     * Registers ActivityResult launchers.
     *
     * Must be called from Activity.onCreate before STARTED state.
     */
    fun initialize()

    /**
     * Executes a command emitted by HomeViewModel.
     */
    fun execute(command: HomeViewModel.WidgetPlacementCommand)

    /**
     * Routes legacy Activity result callbacks used by AppWidgetHost config flows.
     *
     * @return true when the request code belonged to widget placement and was handled.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean
}

/**
 * Default Android implementation of [WidgetPlacementCoordinator].
 */
class ActivityWidgetPlacementCoordinator(
    private val activity: ComponentActivity,
    private val homeViewModel: HomeViewModel,
    private val widgetHostManagerProvider: () -> WidgetHostManager
) : WidgetPlacementCoordinator {

    companion object {
        private const val TAG = "WidgetPlacementCoord"
        private const val REQUEST_CONFIGURE_APPWIDGET = 10_401
    }

    /**
     * Launcher for the system bind-permission activity.
     */
    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>

    private var pendingBindSessionId: String? = null
    private var pendingConfigureSessionId: String? = null

    /**
     * Registers launcher callbacks and links them back into HomeViewModel command flow.
     */
    override fun initialize() {
        widgetBindLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val sessionId = pendingBindSessionId
            pendingBindSessionId = null
            val command = homeViewModel.handleWidgetBindResult(
                resultCode = result.resultCode,
                widgetHostManager = widgetHostManagerProvider(),
                sessionId = sessionId
            )
            execute(command)
        }

    }

    /**
     * Executes bind/configure launch commands produced by HomeViewModel.
     */
    override fun execute(command: HomeViewModel.WidgetPlacementCommand) {
        when (command) {
            is HomeViewModel.WidgetPlacementCommand.LaunchBindPermission -> {
                runCatching {
                    pendingBindSessionId = command.sessionId
                    widgetBindLauncher.launch(command.intent)
                }.onFailure { throwable ->
                    // Some OEM/provider combinations can throw while launching the
                    // bind permission flow. Treat this as a canceled bind so the
                    // pending widget id is released instead of crashing Activity.
                    Log.e(TAG, "Failed to launch widget bind permission flow", throwable)
                    val followUp = homeViewModel.handleWidgetBindResult(
                        resultCode = Activity.RESULT_CANCELED,
                        widgetHostManager = widgetHostManagerProvider(),
                        sessionId = command.sessionId
                    )
                    if (followUp !is HomeViewModel.WidgetPlacementCommand.NoOp) {
                        execute(followUp)
                    }
                }
            }

            is HomeViewModel.WidgetPlacementCommand.LaunchConfigure -> {
                runCatching {
                    pendingConfigureSessionId = command.sessionId
                    widgetHostManagerProvider().startConfigureActivityForResult(
                        activity = activity,
                        appWidgetId = command.appWidgetId,
                        requestCode = REQUEST_CONFIGURE_APPWIDGET
                    )
                }.onFailure { throwable ->
                    // A subset of widgets declares configure activities that are
                    // unavailable/unlaunchable on some devices. Convert the failure
                    // into a canceled configure result so cleanup runs consistently.
                    Log.e(TAG, "Failed to launch widget configure activity", throwable)
                    val followUp = homeViewModel.handleWidgetConfigureResult(
                        resultCode = Activity.RESULT_CANCELED,
                        widgetHostManager = widgetHostManagerProvider(),
                        sessionId = command.sessionId
                    )
                    if (followUp !is HomeViewModel.WidgetPlacementCommand.NoOp) {
                        execute(followUp)
                    }
                }
            }

            HomeViewModel.WidgetPlacementCommand.NoOp -> Unit
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CONFIGURE_APPWIDGET) return false

        val sessionId = pendingConfigureSessionId
        pendingConfigureSessionId = null
        val command = homeViewModel.handleWidgetConfigureResult(
            resultCode = resultCode,
            widgetHostManager = widgetHostManagerProvider(),
            sessionId = sessionId
        )
        execute(command)
        return true
    }
}
