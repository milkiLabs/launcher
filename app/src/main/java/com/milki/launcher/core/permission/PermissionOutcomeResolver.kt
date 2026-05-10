package com.milki.launcher.core.permission

import com.milki.launcher.domain.model.PermissionAccessState

/**
 * Pure classifier for runtime-permission callback results.
 *
 * IMPORTANT:
 * This is only for interpreting a permission *request result* after Android has
 * already responded. It should not be used to infer initial app state, because
 * `shouldShowRequestPermissionRationale()` is ambiguous before the first request.
 */
internal object PermissionOutcomeResolver {
    fun resolveRuntimeRequestResult(
        isGranted: Boolean,
        shouldShowRationale: Boolean
    ): PermissionAccessState {
        return when {
            isGranted -> PermissionAccessState.GRANTED
            shouldShowRationale -> PermissionAccessState.CAN_REQUEST
            else -> PermissionAccessState.REQUIRES_SETTINGS
        }
    }
}
