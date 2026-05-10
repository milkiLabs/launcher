package com.milki.launcher.core.permission

import com.milki.launcher.domain.model.PermissionAccessState
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionOutcomeResolverTest {

    @Test
    fun granted_result_maps_to_granted_state() {
        val state = PermissionOutcomeResolver.resolveRuntimeRequestResult(
            isGranted = true,
            shouldShowRationale = false
        )

        assertEquals(PermissionAccessState.GRANTED, state)
    }

    @Test
    fun denied_result_with_rationale_stays_requestable() {
        val state = PermissionOutcomeResolver.resolveRuntimeRequestResult(
            isGranted = false,
            shouldShowRationale = true
        )

        assertEquals(PermissionAccessState.CAN_REQUEST, state)
    }

    @Test
    fun denied_result_without_rationale_requires_settings() {
        val state = PermissionOutcomeResolver.resolveRuntimeRequestResult(
            isGranted = false,
            shouldShowRationale = false
        )

        assertEquals(PermissionAccessState.REQUIRES_SETTINGS, state)
    }
}
