package com.milki.launcher.domain.model

/**
 * User-relevant access state for a permission-backed feature.
 *
 * This is intentionally higher-level than Android's raw grant APIs:
 * - GRANTED: feature can run now
 * - CAN_REQUEST: feature is blocked, but we can still ask the system again
 * - REQUIRES_SETTINGS: the user must visit Settings to continue
 */
enum class PermissionAccessState {
    GRANTED,
    CAN_REQUEST,
    REQUIRES_SETTINGS;

    val isGranted: Boolean
        get() = this == GRANTED
}
