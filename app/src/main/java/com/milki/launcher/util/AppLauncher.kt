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

package com.milki.launcher.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.HomeItem

/**
 * Launches an application using its AppInfo.
 *
 * This function is used when launching apps from search results.
 * It uses the pre-built launchIntent from AppInfo if available,
 * otherwise falls back to using PackageManager.
 *
 * @param context The Android context
 * @param appInfo The AppInfo containing app details and launch intent
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
    // First try to use the pre-built launchIntent from AppInfo
    // This is more reliable as it was built from PackageManager.resolveActivity
    appInfo.launchIntent?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        // Check if the intent can be resolved before starting
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            
            // Save to recent apps if callback provided
            onRecentAppSaved?.invoke(appInfo.flattenToComponentName())
            return true
        }
    }
    
    // Fallback: try to get launch intent from PackageManager
    // This handles cases where launchIntent might be null
    val fallbackIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
    if (fallbackIntent != null) {
        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        // If activityName is specified, we might need to be more specific
        // But getLaunchIntentForPackage usually gets the main launcher activity
        context.startActivity(fallbackIntent)
        
        // Save to recent apps if callback provided
        onRecentAppSaved?.invoke(appInfo.flattenToComponentName())
        return true
    }
    
    // No launch intent found - app might not be launchable
    return false
}

/**
 * Launches a pinned app from the home screen.
 *
 * This function is used when launching apps that have been pinned to
 * the home screen. It uses PackageManager to get the launch intent.
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
    // Get launch intent from PackageManager using the package name
    // This is the standard way to launch an app from its package name
    val intent = context.packageManager.getLaunchIntentForPackage(pinnedApp.packageName)
    
    if (intent != null) {
        // Add NEW_TASK flag because we're typically not in an Activity context
        // when launching from the home screen
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
 * the specific shortcut. This could be enhanced in the future to use
 * LauncherApps API for proper shortcut launching.
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
    // For now, just open the parent app
    // TODO: Implement proper shortcut launching using LauncherApps.pinShortcut() API
    // The proper way would be:
    // val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    // val intent = launcherApps.getShortcutIntent(...)
    
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
