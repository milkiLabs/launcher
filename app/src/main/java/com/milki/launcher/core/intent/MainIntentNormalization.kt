package com.milki.launcher.core.intent

import android.content.Intent
import android.content.pm.LauncherApps

/** True when [intent] is the HOME category intent that requests the root surface. */
fun Intent.isHomeIntent(): Boolean {
    return action == Intent.ACTION_MAIN &&
            hasCategory(Intent.CATEGORY_HOME)
}

/**
 * True when [intent] is a transient one-shot action (pin-shortcut confirmation,
 * benchmark run) that must never leave the root navigation stack away from home.
 */
fun Intent.shouldNormalizeRootToHome(): Boolean {
    return action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT ||
            toLauncherBenchmarkRequestOrNull() != null
}
