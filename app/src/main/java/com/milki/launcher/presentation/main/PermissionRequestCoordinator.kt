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
     * Connects callbacks between ActionExecutor and PermissionHandler.
     *
     * This should be called once during Activity initialization after both
     * objects are created.
     */
    fun bind() {
        actionExecutor.onRequestPermission = { permission ->
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

        actionExecutor.onCloseSearch = {
            searchViewModel.hideSearch()
        }

        actionExecutor.onSaveRecentApp = { componentName ->
            searchViewModel.saveRecentApp(componentName)
        }

        permissionHandler.onCallPermissionResult = { granted ->
            actionExecutor.onPermissionResult(granted)
        }
    }
}
