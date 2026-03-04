package com.milki.launcher.presentation.main

import com.milki.launcher.handlers.PermissionHandler
import com.milki.launcher.presentation.search.ActionExecutor
import com.milki.launcher.presentation.search.SearchViewModel

/**
 * PermissionRequestCoordinator.kt - Wires ActionExecutor permission requests to PermissionHandler.
 *
 * WHY THIS FILE EXISTS:
 * MainActivity previously orchestrated several cross-component callbacks:
 * - ActionExecutor -> PermissionHandler (request specific permission)
 * - PermissionHandler -> ActionExecutor (permission result callback)
 *
 * Moving this wiring into a dedicated coordinator keeps MainActivity focused on
 * lifecycle hosting and UI composition.
 *
 * DESIGN NOTES:
 * - This class intentionally contains orchestration side effects (callback wiring).
 * - It does not own permission launcher registration; PermissionHandler still does.
 */
class PermissionRequestCoordinator(
    private val permissionHandler: PermissionHandler,
    private val actionExecutor: ActionExecutor,
    private val searchViewModel: SearchViewModel
) {

    /**
     * Internal state-machine-backed orchestrator that serializes permission requests
     * and routes permission results deterministically.
     *
     * NOTE:
     * This is intentionally created inside the coordinator because callback wiring
     * is this class's responsibility. The orchestrator itself is Android-free and
     * only depends on these two lambdas.
     */
    private val permissionOrchestrator = PermissionOrchestrator(
        requestPermission = ::requestPermissionFromSystem,
        deliverPermissionResult = ::deliverPermissionResultToConsumers
    )

    /**
     * Connects callbacks between ActionExecutor and PermissionHandler.
     *
     * This should be called once during Activity initialization after both
     * objects are created.
     */
    fun bind() {
        actionExecutor.onRequestPermission = { permission ->
            permissionOrchestrator.request(permission)
        }

        actionExecutor.onCloseSearch = {
            searchViewModel.hideSearch()
        }

        actionExecutor.onSaveRecentApp = { componentName ->
            searchViewModel.saveRecentApp(componentName)
        }

        permissionHandler.onPermissionResult = { permission, granted ->
            permissionOrchestrator.onResult(permission, granted)
        }
    }

    /**
     * Requests the matching Android permission via PermissionHandler.
     *
     * This is called by PermissionOrchestrator when its reducer emits
     * a RequestPermission effect.
     */
    private fun requestPermissionFromSystem(permission: String) {
        when (permission) {
            android.Manifest.permission.READ_CONTACTS -> {
                permissionHandler.requestContactsPermission()
            }

            android.Manifest.permission.CALL_PHONE -> {
                permissionHandler.requestCallPermission()
            }

            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE -> {
                permissionHandler.requestFilesPermission()
            }
        }
    }

    /**
     * Delivers completed permission outcomes to the interested consumer.
     *
     * CURRENT CONSUMERS:
     * - CALL_PHONE result is forwarded to ActionExecutor because it can have
     *   a pending action waiting for this grant.
     *
     * NOT FORWARDED (by design):
     * - READ_CONTACTS and file permissions are already persisted into SearchViewModel
     *   directly by PermissionHandler. They currently have no pending-action replay path.
     */
    private fun deliverPermissionResultToConsumers(permission: String, granted: Boolean) {
        if (permission == android.Manifest.permission.CALL_PHONE) {
            actionExecutor.onPermissionResult(granted)
        }
    }
}
