package com.milki.launcher.core.intent

import android.content.ComponentName
import android.content.Intent

/**
 * Creates an explicit launcher intent for a specific launcher activity component.
 */
fun createLauncherActivityIntent(componentName: ComponentName): Intent {
    return Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = componentName
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
}
