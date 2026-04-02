package com.milki.launcher.presentation.launcher

/**
 * PermissionOrchestrator.kt - Small internal state machine for runtime permission orchestration.
 *
 * WHY THIS FILE EXISTS:
 * Permission request flow previously relied on handcrafted callback wiring between
 * ActionExecutor and PermissionHandler. That worked, but it made edge-case behavior
 * (duplicate requests, overlapping requests, out-of-order results) implicit.
 *
 * This module makes that behavior explicit by:
 * 1) Modeling orchestration as a pure state transition function.
 * 2) Returning explicit side effects to execute after each transition.
 * 3) Providing a tiny runtime wrapper that executes those effects.
 *
 * BENEFITS:
 * - Easy unit testing (reducer is pure Kotlin, no Android dependency).
 * - Fewer accidental regressions when new permissions are added.
 * - Clear, deterministic handling for edge cases.
 */

/**
 * Immutable state for permission orchestration.
 *
 * @property activePermission The permission currently being requested from Android.
 *                            Null means no request is in flight.
 * @property queuedPermission A single queued request waiting for active request to finish.
 *                            Null means no queued request exists.
 */
data class PermissionOrchestrationState(
    val activePermission: String? = null,
    val queuedPermission: String? = null
)

/**
 * Side effects produced by the state machine.
 *
 * IMPORTANT:
 * The reducer itself never performs side effects. It only returns these effect objects.
 * The runtime orchestrator executes them.
 */
sealed class PermissionOrchestrationEffect {
    /**
     * Ask Android system APIs to show/request a specific permission.
     */
    data class RequestPermission(val permission: String) : PermissionOrchestrationEffect()

    /**
     * Deliver a completed result to higher-level consumers.
     */
    data class DeliverResult(
        val permission: String,
        val granted: Boolean
    ) : PermissionOrchestrationEffect()
}

/**
 * Result container returned by reducer functions.
 *
 * @property state The next immutable state after processing the input event.
 * @property effects The exact side effects that caller should execute.
 */
data class PermissionOrchestrationTransition(
    val state: PermissionOrchestrationState,
    val effects: List<PermissionOrchestrationEffect>
)

/**
 * Pure reducer for permission orchestration.
 *
 * EVENTS:
 * 1) onPermissionRequested(permission)
 * 2) onPermissionResult(permission, granted)
 *
 * EDGE-CASE POLICY (explicit):
 * - Duplicate active requests are ignored.
 * - A second distinct request is queued (single slot).
 * - If a third distinct request arrives while queue is occupied, queue is replaced
 *   with the latest request. This "latest intent wins" approach keeps behavior
 *   predictable without unbounded queue complexity.
 * - Out-of-order/unknown results are ignored safely.
 */
object PermissionOrchestrationReducer {

    /**
     * Processes a new permission request intent.
     */
    fun onPermissionRequested(
        state: PermissionOrchestrationState,
        permission: String
    ): PermissionOrchestrationTransition {
        val active = state.activePermission
        val queued = state.queuedPermission

        // No request in flight -> start immediately.
        if (active == null) {
            return PermissionOrchestrationTransition(
                state = PermissionOrchestrationState(activePermission = permission),
                effects = listOf(PermissionOrchestrationEffect.RequestPermission(permission))
            )
        }

        // Duplicate active request -> no-op.
        if (active == permission) {
            return PermissionOrchestrationTransition(state = state, effects = emptyList())
        }

        // Same as queued request -> no-op.
        if (queued == permission) {
            return PermissionOrchestrationTransition(state = state, effects = emptyList())
        }

        // Queue the latest distinct request (single-slot queue).
        return PermissionOrchestrationTransition(
            state = state.copy(queuedPermission = permission),
            effects = emptyList()
        )
    }

    /**
     * Processes a permission result coming back from Android callback APIs.
     */
    fun onPermissionResult(
        state: PermissionOrchestrationState,
        permission: String,
        granted: Boolean
    ): PermissionOrchestrationTransition {
        val active = state.activePermission

        // If no active request exists, result is stale/unknown -> ignore safely.
        if (active == null) {
            return PermissionOrchestrationTransition(state = state, effects = emptyList())
        }

        // Ignore out-of-order results that do not match the active permission.
        if (active != permission) {
            return PermissionOrchestrationTransition(state = state, effects = emptyList())
        }

        val effects = mutableListOf<PermissionOrchestrationEffect>()
        effects += PermissionOrchestrationEffect.DeliverResult(permission, granted)

        val queued = state.queuedPermission

        // If there is a queued request, immediately promote it and request it.
        if (queued != null) {
            effects += PermissionOrchestrationEffect.RequestPermission(queued)
            return PermissionOrchestrationTransition(
                state = PermissionOrchestrationState(activePermission = queued),
                effects = effects
            )
        }

        // Nothing queued -> go idle.
        return PermissionOrchestrationTransition(
            state = PermissionOrchestrationState(),
            effects = effects
        )
    }
}

/**
 * Runtime wrapper around the pure reducer.
 *
 * This class stores current state and executes effects using callbacks supplied by caller.
 * Keeping reducer pure + wrapper thin gives us both testability and practical integration.
 */
class PermissionOrchestrator(
    private val requestPermission: (String) -> Unit,
    private val deliverPermissionResult: (permission: String, granted: Boolean) -> Unit
) {

    /**
     * Current orchestration state. Exposed as read-only for debugging/tests.
     */
    var state: PermissionOrchestrationState = PermissionOrchestrationState()
        private set

    /**
     * Entry point for new permission request intents.
     */
    fun request(permission: String) {
        val transition = PermissionOrchestrationReducer.onPermissionRequested(state, permission)
        applyTransition(transition)
    }

    /**
     * Entry point for permission result callbacks from Android APIs.
     */
    fun onResult(permission: String, granted: Boolean) {
        val transition = PermissionOrchestrationReducer.onPermissionResult(
            state = state,
            permission = permission,
            granted = granted
        )
        applyTransition(transition)
    }

    /**
     * Applies transition atomically: first update state, then execute effects in order.
     */
    private fun applyTransition(transition: PermissionOrchestrationTransition) {
        state = transition.state

        transition.effects.forEach { effect ->
            when (effect) {
                is PermissionOrchestrationEffect.RequestPermission -> {
                    requestPermission(effect.permission)
                }

                is PermissionOrchestrationEffect.DeliverResult -> {
                    deliverPermissionResult(effect.permission, effect.granted)
                }
            }
        }
    }
}
