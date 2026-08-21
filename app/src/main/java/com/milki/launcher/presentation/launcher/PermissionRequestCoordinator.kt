package com.milki.launcher.presentation.launcher

import com.milki.launcher.core.permission.PermissionHandler
import com.milki.launcher.presentation.search.ActionExecutor

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
 * - The ActionExecutor consumes [requestPermission] and [closeSearch] directly at
 *   construction time (constructor injection), so there is no late callback binding
 *   and no temporal coupling between construction and bind().
 * - [bind] only wires the PermissionHandler result path, which is inherently
 *   event-driven (Android fires it after a request).
 */
class PermissionRequestCoordinator(
    private val permissionHandler: PermissionHandler,
    private val onCloseSearch: () -> Unit = {},
    private val actionExecutorProvider: () -> ActionExecutor
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
     * Entry point consumed by ActionExecutor when an action needs a runtime permission.
     */
    fun requestPermission(permission: String) {
        permissionOrchestrator.request(permission)
    }

    /**
     * Entry point consumed by ActionExecutor when an action completes and the
     * search surface should be dismissed.
     *
     * The [LauncherNavigator] is the single owner of search visibility: this
     * only pops the route, whose closeRoute(Search) clears the visibility flow.
     */
    fun closeSearch() {
        onCloseSearch()
    }

    /**
     * Connects the PermissionHandler result path.
     *
     * This should be called once during Activity initialization after both
     * objects are created.
     */
    fun bind() {
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
            actionExecutorProvider().onPermissionResult(granted)
        }
    }
}
