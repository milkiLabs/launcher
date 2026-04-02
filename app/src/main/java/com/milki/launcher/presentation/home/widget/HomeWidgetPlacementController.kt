package com.milki.launcher.presentation.home

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem

/**
 * Stateful bind/configure/place controller for widget placement.
 */
internal class HomeWidgetPlacementController(
    private val mutationCoordinator: HomeMutationCoordinator
) {

    private data class PendingWidget(
        val appWidgetId: Int,
        val providerComponent: ComponentName,
        val providerLabel: String,
        val targetPosition: GridPosition,
        val span: GridSpan
    )

    private val pendingWidgets = linkedMapOf<Int, PendingWidget>()

    fun startWidgetPlacement(
        providerInfo: AppWidgetProviderInfo,
        targetPosition: GridPosition,
        span: GridSpan,
        widgetHostManager: WidgetHostManager
    ): HomeViewModel.WidgetPlacementCommand {
        val appWidgetId = widgetHostManager.allocateWidgetId()
        val bindOptions = widgetHostManager.createBindOptions(span)
        val pending = PendingWidget(
            appWidgetId = appWidgetId,
            providerComponent = providerInfo.provider,
            providerLabel = widgetHostManager.loadProviderLabel(providerInfo),
            targetPosition = targetPosition,
            span = span
        )
        pendingWidgets[appWidgetId] = pending

        val boundImmediately = widgetHostManager.bindWidget(
            appWidgetId = appWidgetId,
            providerInfo = providerInfo,
            options = bindOptions
        )

        return if (boundImmediately) {
            resolvePostBindCommand(appWidgetId, widgetHostManager)
        } else {
            HomeViewModel.WidgetPlacementCommand.LaunchBindPermission(
                appWidgetId = appWidgetId,
                intent = widgetHostManager.createBindPermissionIntent(
                    appWidgetId = appWidgetId,
                    providerInfo = providerInfo,
                    options = bindOptions
                )
            )
        }
    }

    fun handleWidgetBindResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): HomeViewModel.WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return HomeViewModel.WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            resolvePostBindCommand(appWidgetId, widgetHostManager)
        } else {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            HomeViewModel.WidgetPlacementCommand.NoOp
        }
    }

    fun handleWidgetConfigureResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): HomeViewModel.WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return HomeViewModel.WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            persistWidget(appWidgetId, pending, widgetHostManager)
            HomeViewModel.WidgetPlacementCommand.NoOp
        } else {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            HomeViewModel.WidgetPlacementCommand.NoOp
        }
    }

    private fun resolvePostBindCommand(
        appWidgetId: Int,
        widgetHostManager: WidgetHostManager
    ): HomeViewModel.WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return HomeViewModel.WidgetPlacementCommand.NoOp
        val boundProviderInfo = widgetHostManager.getProviderInfo(pending.appWidgetId)
        if (boundProviderInfo == null) {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            return HomeViewModel.WidgetPlacementCommand.NoOp
        }

        return if (widgetHostManager.needsConfigure(pending.appWidgetId)) {
            HomeViewModel.WidgetPlacementCommand.LaunchConfigure(appWidgetId = pending.appWidgetId)
        } else {
            persistWidget(appWidgetId, pending, widgetHostManager)
            HomeViewModel.WidgetPlacementCommand.NoOp
        }
    }

    private fun persistWidget(
        appWidgetId: Int,
        pending: PendingWidget,
        widgetHostManager: WidgetHostManager
    ) {
        val widgetItem = HomeItem.WidgetItem.create(
            appWidgetId = pending.appWidgetId,
            providerPackage = pending.providerComponent.packageName,
            providerClass = pending.providerComponent.className,
            label = pending.providerLabel,
            position = pending.targetPosition,
            span = pending.span
        )

        pendingWidgets.remove(appWidgetId)

        mutationCoordinator.launchSerializedMutation(
            fallbackErrorMessage = "Could not place widget"
        ) {
            val applied = mutationCoordinator.applyWriterCommand(
                command = HomeModelWriter.Command.PinOrMoveToPosition(
                    item = widgetItem,
                    targetPosition = pending.targetPosition
                )
            )
            if (!applied) {
                widgetHostManager.deallocateWidgetId(pending.appWidgetId)
            }
            applied
        }
    }

    private fun cancelPendingWidget(
        appWidgetId: Int,
        widgetHostManager: WidgetHostManager,
        pending: PendingWidget
    ) {
        widgetHostManager.deallocateWidgetId(pending.appWidgetId)
        pendingWidgets.remove(appWidgetId)
    }
}