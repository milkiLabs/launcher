package com.milki.launcher.presentation.launcher.host

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.milki.launcher.core.intent.launchApp
import com.milki.launcher.core.intent.launchAppShortcut
import com.milki.launcher.core.intent.launchSafe
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.LauncherTriggerTarget

/**
 * Resolves a home-trigger target into an actual launch.
 *
 * Extracted from [LauncherRootContent] so the intent-building policy lives in
 * a plain class that can be unit-tested without Compose.
 */
internal class TriggerLaunchController {

    fun launch(context: Context, target: LauncherTriggerTarget?) {
        when (target) {
            is LauncherTriggerTarget.App -> {
                launchApp(
                    context = context,
                    appInfo = AppInfo(
                        name = target.displayName,
                        packageName = target.packageName,
                        activityName = target.activityName
                    )
                )
            }

            is LauncherTriggerTarget.AppShortcut -> {
                launchAppShortcut(
                    context = context,
                    appShortcut = target.toHomeShortcut()
                )
            }

            is LauncherTriggerTarget.ActionShortcut -> {
                val uri = Uri.parse(target.destinationUri)
                val nonBrowserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
                    target.packageName?.let { setPackage(it) }
                }
                val plainIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    target.packageName?.let { setPackage(it) }
                }
                context.launchSafe(
                    "trigger action shortcut ${target.destinationUri}",
                    listOf(nonBrowserIntent, plainIntent)
                )
            }

            null -> Unit
        }
    }
}
