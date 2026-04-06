/**
 * AppLauncher.kt - Utility for launching applications
 *
 * This file provides a centralized utility for launching applications on Android.
 * It handles launching apps from different sources:
 * - AppInfo (from search results)
 * - PinnedApp (from home screen)
 *
 * The AppLauncher ensures consistent behavior across the app and eliminates
 * code duplication in app launching logic.
 *
 * Usage:
 * ```kotlin
 * // Launch from AppInfo (with callback to save recent app)
 * AppLauncher.launchApp(context, appInfo) { recentAppId ->
 *     // Save to recent apps
 * }
 *
 * // Launch from PinnedApp
 * AppLauncher.launchPinnedApp(context, pinnedApp)
 * ```
 */

package com.milki.launcher.core.intent

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.widget.Toast
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem

/**
 * Launches an application using its AppInfo.
 *
 * This function is used when launching apps from search results.
 * It prefers an explicit component launch so we do not keep Intent instances
 * inside the app model or re-query PackageManager on the hot path.
 *
 * @param context The Android context
 * @param appInfo The AppInfo containing app details
 * @param onRecentAppSaved Optional callback invoked with the component name
 *                         when the app is successfully launched (for saving recent apps)
 *
 * Example:
 * ```kotlin
 * AppLauncher.launchApp(context, appInfo) { componentName ->
 *     viewModel.saveRecentApp(componentName)
 * }
 * ```
 */
fun launchApp(
    context: Context,
    appInfo: AppInfo,
    onRecentAppSaved: ((String) -> Unit)? = null
): Boolean {
    val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = ComponentName(appInfo.packageName, appInfo.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (explicitIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(explicitIntent)
        onRecentAppSaved?.invoke(appInfo.flattenToComponentName())
        return true
    }

    // Fallback: ask PackageManager for the package's launcher intent in case
    // the stored activity is no longer valid after an app update.
    val fallbackIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
    if (fallbackIntent != null) {
        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallbackIntent)
        onRecentAppSaved?.invoke(appInfo.flattenToComponentName())
        return true
    }

    return false
}

/**
 * Launches a pinned app from the home screen.
 *
 * This function is used when launching apps that have been pinned to
 * the home screen. It directly creates an intent using the activity name
 * stored in PinnedApp, avoiding the overhead of PackageManager lookup.
 *
 * @param context The Android context
 * @param pinnedApp The PinnedApp from the home screen
 * @return True if the app was successfully launched, false otherwise
 *
 * Example:
 * ```kotlin
 * if (!AppLauncher.launchPinnedApp(context, pinnedApp)) {
 *     Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
 * }
 * ```
 */
fun launchPinnedApp(
    context: Context,
    pinnedApp: HomeItem.PinnedApp
): Boolean {
    // Create intent directly using the activity name stored in PinnedApp
    // This avoids the I/O overhead of calling PackageManager.getLaunchIntentForPackage()
    // which queries the system for the default launcher activity every time.
    val intent = Intent().apply {
        setClassName(pinnedApp.packageName, pinnedApp.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    // Verify the activity exists before launching
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        return true
    }
    
    // App not found - return false so caller can show error message
    return false
}

/**
 * Launches an app shortcut (e.g., WhatsApp chat shortcut).
 *
 * This function is used for app shortcuts that were created by other apps
 * using Android's ShortcutManager API. These are different from regular
 * app shortcuts - they point to specific content within an app.
 *
 * Note: The current implementation simply opens the parent app rather than
 * the specific shortcut when launcher APIs are unavailable.
 *
 * @param context The Android context
 * @param appShortcut The AppShortcut to launch
 * @return True if the parent app was successfully launched, false otherwise
 *
 * Example:
 * ```kotlin
 * AppLauncher.launchAppShortcut(context, shortcut)
 * ```
 */
fun launchAppShortcut(
    context: Context,
    appShortcut: HomeItem.AppShortcut
): Boolean {
    // Preferred path: launch the concrete shortcut via LauncherApps.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            try {
                launcherApps.startShortcut(
                    appShortcut.packageName,
                    appShortcut.shortcutId,
                    null,
                    null,
                    Process.myUserHandle()
                )
                return true
            } catch (_: SecurityException) {
                // Fall through to package launch fallback.
            } catch (_: IllegalStateException) {
                // Fall through to package launch fallback.
            } catch (_: IllegalArgumentException) {
                // Fall through to package launch fallback.
            }
        }
    }
    
    val intent = context.packageManager.getLaunchIntentForPackage(appShortcut.packageName)
    
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
    
    return false
}

/**
 * Extension function to convert AppInfo to a component name string.
 * Used for saving recent apps.
 *
 * @return String in format "packageName/activityName"
 */
fun AppInfo.flattenToComponentName(): String {
    return android.content.ComponentName(
        packageName,
        activityName
    ).flattenToString()
}
