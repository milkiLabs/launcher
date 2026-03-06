package com.milki.launcher.presentation.main

import android.content.Intent
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
}

/**
 * Default Android implementation of [WidgetPlacementCoordinator].
 */
class ActivityWidgetPlacementCoordinator(
    private val activity: ComponentActivity,
    private val homeViewModel: HomeViewModel,
    private val widgetHostManagerProvider: () -> WidgetHostManager
) : WidgetPlacementCoordinator {

    /**
     * Launcher for the system bind-permission activity.
     */
    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>

    /**
     * Launcher for widget provider configuration activity.
     */
    private lateinit var widgetConfigureLauncher: ActivityResultLauncher<Intent>

    /**
     * Registers launcher callbacks and links them back into HomeViewModel command flow.
     */
    override fun initialize() {
        widgetBindLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val command = homeViewModel.handleWidgetBindResult(
                resultCode = result.resultCode,
                widgetHostManager = widgetHostManagerProvider()
            )
            execute(command)
        }

        widgetConfigureLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val command = homeViewModel.handleWidgetConfigureResult(
                resultCode = result.resultCode,
                widgetHostManager = widgetHostManagerProvider()
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
                widgetBindLauncher.launch(command.intent)
            }

            is HomeViewModel.WidgetPlacementCommand.LaunchConfigure -> {
                widgetConfigureLauncher.launch(command.intent)
            }

            HomeViewModel.WidgetPlacementCommand.NoOp -> Unit
        }
    }
}
