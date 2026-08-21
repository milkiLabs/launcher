package com.milki.launcher.presentation.home

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.domain.widget.needsInitialWidgetConfigure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

sealed interface WidgetPlacementCommand {
    data class LaunchBindPermission(val appWidgetId: Int, val intent: Intent) : WidgetPlacementCommand
    data class LaunchConfigure(val appWidgetId: Int) : WidgetPlacementCommand
    data object NoOp : WidgetPlacementCommand
}

/**
 * Owns the widget placement state machine: pending widget bookkeeping,
 * bind/configure result resolution, and [WidgetHostPort] interactions.
 *
 * Note: if the process dies between [startWidgetPlacement] (which allocates an
 * AppWidget id) and persistence, that id is orphaned — Android only reclaims
 * allocated ids on uninstall. [releasePendingWidgets] covers the
 * ViewModel-cleared case; process death cannot be intercepted.
 */
class WidgetPlacementManager(
    private val modelMutator: HomeModelMutator,
    private val widgetHost: WidgetHostPort,
    private val scope: CoroutineScope,
    private val pinnedItemsProvider: suspend () -> List<HomeItem>
) {

    private data class PendingWidget(
        val appWidgetId: Int,
        val providerComponent: ComponentName,
        val providerLabel: String,
        val targetPosition: GridPosition,
        val span: GridSpan,
        val displayMode: WidgetDisplayMode
    )

    private val pendingWidgets = linkedMapOf<Int, PendingWidget>()

    /**
     * Deallocates AppWidget ids for widgets that never finished
     * binding/configuring. Call from ViewModel.onCleared.
     */
    fun releasePendingWidgets() {
        val pending = pendingWidgets.values.toList()
        pendingWidgets.clear()
        pending.forEach { widgetHost.deallocateWidgetId(it.appWidgetId) }
    }

    suspend fun startWidgetPlacement(
        providerInfo: AppWidgetProviderInfo,
        targetPosition: GridPosition,
        span: GridSpan,
        displayMode: WidgetDisplayMode = WidgetDisplayMode.Inline
    ): WidgetPlacementCommand {
        val existingWidget = pinnedItemsProvider().filterIsInstance<HomeItem.WidgetItem>().firstOrNull {
            it.providerPackage == providerInfo.provider.packageName &&
            it.providerClass == providerInfo.provider.className
        }

        if (existingWidget != null) {
            val updatedWidget = existingWidget.withDisplayMode(displayMode).withSpan(span)
            modelMutator.mutate(
                fallbackErrorMessage = "Target position is occupied",
                command = HomeModelWriter.PinOrMoveToPosition(
                    item = updatedWidget,
                    targetPosition = targetPosition
                )
            )
            return WidgetPlacementCommand.NoOp
        }

        val appWidgetId = widgetHost.allocateWidgetId()
        val bindOptions = widgetHost.createBindOptions(span)
        pendingWidgets[appWidgetId] = PendingWidget(
            appWidgetId = appWidgetId,
            providerComponent = providerInfo.provider,
            providerLabel = widgetHost.loadProviderLabel(providerInfo),
            targetPosition = targetPosition,
            span = span,
            displayMode = displayMode
        )

        val boundImmediately = widgetHost.bindWidget(
            appWidgetId = appWidgetId,
            providerInfo = providerInfo,
            options = bindOptions
        )

        return if (boundImmediately) {
            resolvePostBindCommand(appWidgetId)
        } else {
            WidgetPlacementCommand.LaunchBindPermission(
                appWidgetId = appWidgetId,
                intent = widgetHost.createBindPermissionIntent(
                    appWidgetId = appWidgetId,
                    providerInfo = providerInfo,
                    options = bindOptions
                )
            )
        }
    }

    fun handleWidgetBindResult(
        resultCode: Int,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            resolvePostBindCommand(appWidgetId)
        } else {
            cancelPendingWidget(pending)
            WidgetPlacementCommand.NoOp
        }
    }

    fun handleWidgetConfigureResult(
        resultCode: Int,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            persistPendingWidget(appWidgetId, pending)
            WidgetPlacementCommand.NoOp
        } else {
            cancelPendingWidget(pending)
            WidgetPlacementCommand.NoOp
        }
    }

    private fun resolvePostBindCommand(appWidgetId: Int): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        val boundProviderInfo = widgetHost.getProviderInfo(appWidgetId)
        if (boundProviderInfo == null) {
            cancelPendingWidget(pending)
            return WidgetPlacementCommand.NoOp
        }

        return if (needsInitialWidgetConfigure(boundProviderInfo)) {
            WidgetPlacementCommand.LaunchConfigure(appWidgetId = appWidgetId)
        } else {
            persistPendingWidget(appWidgetId, pending)
            WidgetPlacementCommand.NoOp
        }
    }

    private fun persistPendingWidget(
        appWidgetId: Int,
        pending: PendingWidget
    ) {
        val widgetItem = HomeItem.WidgetItem.create(
            appWidgetId = pending.appWidgetId,
            providerPackage = pending.providerComponent.packageName,
            providerClass = pending.providerComponent.className,
            label = pending.providerLabel,
            position = pending.targetPosition,
            span = pending.span,
            displayMode = pending.displayMode
        )

        pendingWidgets.remove(appWidgetId)

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            modelMutator.applyTracked(
                command = HomeModelWriter.PinOrMoveToPosition(
                    item = widgetItem,
                    targetPosition = pending.targetPosition
                ),
                fallbackErrorMessage = "Could not place widget",
                onFailure = {
                    modelMutator.reportMoveError("Could not place widget")
                    widgetHost.deallocateWidgetId(pending.appWidgetId)
                }
            )
        }
    }

    private fun cancelPendingWidget(pending: PendingWidget) {
        widgetHost.deallocateWidgetId(pending.appWidgetId)
        pendingWidgets.remove(pending.appWidgetId)
    }
}
