package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.LauncherTrigger
import com.milki.launcher.domain.model.LauncherTriggerAction
import com.milki.launcher.domain.model.LauncherTriggerTarget

/**
 * Manages homescreen trigger-to-action mappings and their launch targets.
 *
 * Handles gesture/action configuration (tap, swipe, double-tap) and the
 * concrete app/shortcut targets for OPEN_APP actions.
 */
interface HomeTriggerRepository {

    /**
     * Update one trigger -> action mapping.
     */
    suspend fun setTriggerAction(trigger: LauncherTrigger, action: LauncherTriggerAction)

    /**
     * Map a trigger to the "open app" action and persist the concrete app/shortcut target.
     */
    suspend fun setTriggerOpenAppTarget(
        trigger: LauncherTrigger,
        target: LauncherTriggerTarget
    )
}
