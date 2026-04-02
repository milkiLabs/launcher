package com.milki.launcher.presentation.launcher

/**
 * Pure policy for HOME-button behavior while app drawer may be visible.
 *
 * This mirrors the pattern used by [HomeButtonPolicy]: policy decides, callers apply.
 */
class DrawerHomePressPolicy {

    data class InputState(
        val isDrawerOpen: Boolean,
        val hasDrawerQuery: Boolean,
        val shouldClearDrawerQueryOnHomePress: Boolean
    )

    enum class Decision {
        NONE,
        CLEAR_QUERY,
        CLOSE_DRAWER
    }

    fun resolve(input: InputState): Decision {
        if (!input.isDrawerOpen) return Decision.NONE
        return if (input.hasDrawerQuery && input.shouldClearDrawerQueryOnHomePress) {
            Decision.CLEAR_QUERY
        } else {
            Decision.CLOSE_DRAWER
        }
    }
}
