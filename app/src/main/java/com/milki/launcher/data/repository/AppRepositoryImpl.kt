/**
 * AppRepositoryImpl.kt - Implementation of AppRepository interface
 */

package com.milki.launcher.data.repository

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// ============================================================================
// PACKAGEMANAGER COMPATIBILITY EXTENSIONS
// ============================================================================

/**
 * Extension function to query intent activities with backwards compatibility.
 * 
 * Why do we need this?
 * In Android 13 (API 33+), queryIntentActivities(intent, 0) was deprecated.
 * The new API requires PackageManager.ResolveInfoFlags.of(0L) instead of an Int.
 * 
 * This extension handles both cases:
 * - API 33+: Uses the new ResolveInfoFlags API
 * - API < 33: Uses the deprecated Int-based API (with suppression)
 * 
 * @param intent The intent to query for matching activities
 * @return List of ResolveInfo objects for matching activities
 */
fun PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ (API 33): Use the new flags-based API
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        // Android 12 and below: Use the deprecated Int-based API
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
}

/**
 * Extension function to get application info with backwards compatibility.
 * 
 * Why do we need this?
 * In Android 13 (API 33+), getApplicationInfo(packageName, 0) was deprecated.
 * The new API requires PackageManager.ApplicationInfoFlags.of(0L) instead of an Int.
 * 
 * This extension handles both cases:
 * - API 33+: Uses the new ApplicationInfoFlags API
 * - API < 33: Uses the deprecated Int-based API (with suppression)
 * 
 * @param packageName The package name to look up
 * @return ApplicationInfo for the specified package
 * @throws PackageManager.NameNotFoundException if the package doesn't exist
 */
@Throws(PackageManager.NameNotFoundException::class)
fun PackageManager.getApplicationInfoCompat(packageName: String): android.content.pm.ApplicationInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ (API 33): Use the new flags-based API
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        // Android 12 and below: Use the deprecated Int-based API
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }
}

// ============================================================================
// TOP-LEVEL DATASTORE DELEGATE
// ============================================================================

/**
 * Top-level DataStore delegate for storing launcher preferences.
 * 
 * IMPORTANT: This delegate MUST be at the top level of the file, NOT inside a class.
 * 
 * Why?
 * The 'by preferencesDataStore' delegate creates a single DataStore instance per file.
 * If placed inside a class and that class is instantiated multiple times,
 * each instance would try to create a new DataStore for the same file.
 * This throws IllegalStateException and crashes the app.
 * 
 * Google's official documentation explicitly warns:
 * "The datastore instance must be created at the top level of your Kotlin file,
 * outside of any class, to ensure it's a true singleton."
 * 
 * How it works:
 * - The delegate lazily creates the DataStore on first access
 * - It caches the instance for all subsequent accesses
 * - Being top-level ensures there's only ONE instance across the entire app
 * 
 * The 'name' parameter specifies the preferences file name:
 * - Stored at: /data/data/<package>/files/datastore/launcher_prefs.preferences_pb
 * - Contains: user preferences that survive app restarts
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

/**
 * Implementation of AppRepository that uses Android's PackageManager and DataStore.

 * @param application The Application instance for accessing system services
 */
class AppRepositoryImpl(
    private val application: Application
) : AppRepository {
    
    // ============================================================================
    // DATASTORE KEY
    // ============================================================================
    
    /**
     * Key used to store recent apps as a comma-separated string of flattened ComponentNames.
     * 
     * Format: "com.package/.ActivityClass,com.other/.MainActivity"
     * 
     * Why ComponentName instead of just packageName?
     * An app can have MULTIPLE launcher activities (e.g., MainActivity and SettingsActivity).
     * If we only save packageName, we lose which specific activity was launched.
     * getLaunchIntentForPackage() would return the DEFAULT launcher activity,
     * which might be different from what the user actually clicked.
     * 
     * ComponentName.flattenToString() format:
     * - Short form: "com.package/.ActivityClass" (when activity is in the same package)
     * - Long form: "com.package/com.other.ActivityClass" (when activity class has different package)
     * 
     * Why stringPreferencesKey?
     * DataStore requires typed keys. stringPreferencesKey creates a key
     * that can only store/retrieve String values, providing type safety.
     * The string "recent_apps" is the actual key name stored in the file.
     */
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    // ============================================================================
    // DISPATCHER SETUP
    // ============================================================================
    
    /**
     * Custom dispatcher that limits parallel operations to 8 at a time.
     * 
     * Why do we need this?
     * When loading 150+ apps, creating a coroutine for each would launch
     * 150+ concurrent operations, causing a memory spike. By limiting to 8,
     * we process apps in batches, keeping memory usage controlled.
     * 
     * Why 8?
     * - Matches typical mobile CPU core count (4-8 cores)
     * - Balances speed and memory usage
     * - Prevents overwhelming the system
     */
    private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
    
    // ============================================================================
    // REPOSITORY IMPLEMENTATION
    // ============================================================================
    
    /**
     * Load all installed apps that have launcher icons.
     * 
     * Implementation details:
     * 1. Create an Intent that matches MAIN/LAUNCHER activities
     * 2. Query PackageManager for all matching activities
     * 3. Launch parallel coroutines (limited by dispatcher to 8 concurrent)
     * 4. Convert each to AppInfo domain model
     * 5. Sort alphabetically by name
     * 
     * Why NOT chunked()?
     * We use limitedParallelism(8) which naturally limits concurrent coroutines to 8.
     * If we also used chunked(8) with awaitAll(), we'd force sequential batches:
     * - If 1 app takes 500ms and 7 others take 10ms, all 7 threads sit idle
     * - Without chunking, as soon as any coroutine finishes, a new one starts
     * - This maximizes throughput while still limiting memory/pressure to 8 concurrent
     * 
     * @return Sorted list of AppInfo objects
     */
    override suspend fun getInstalledApps(): List<AppInfo> {
        // withContext switches to our limited dispatcher for the whole operation
        return withContext(limitedDispatcher) {
            val pm = application.packageManager
            
            // Step 1: Create query intent
            // ACTION_MAIN + CATEGORY_LAUNCHER = apps that appear in launcher drawer
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            // Step 2: Query the system using compat extension
            // This returns a list of ResolveInfo objects representing matching activities
            val resolveInfos = pm.queryIntentActivitiesCompat(mainIntent)
            
            // Step 3 & 4: Launch all coroutines, limitedParallelism handles concurrency
            // We create one async per app, but only 8 run at a time
            // As each finishes, the next one starts immediately (no batch waiting)
            resolveInfos.map { resolveInfo ->
                async {
                    // Build an explicit launch intent for this specific activity.
                    // We use the full component name (package + activity class)
                    // instead of getLaunchIntentForPackage() because multiple
                    // activities in the same package can have MAIN+LAUNCHER
                    // (e.g., our launcher's MainActivity and SettingsActivity).
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = android.content.ComponentName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }

                    // Convert ResolveInfo to our domain model AppInfo
                    AppInfo(
                        // loadLabel gets the localized display name
                        name = resolveInfo.loadLabel(pm).toString(),
                        // activityInfo.packageName is the package identifier
                        packageName = resolveInfo.activityInfo.packageName,
                        // activityInfo.name is the fully qualified activity class
                        // This distinguishes multiple activities in the same package
                        activityName = resolveInfo.activityInfo.name,
                        // Explicit intent targeting this specific activity
                        launchIntent = launchIntent
                    )
                }
            }.awaitAll().sortedBy { it.nameLower } // Sort alphabetically
        }
    }
    
    /**
     * Get recent apps as an observable Flow.
     * 
     * Implementation details:
     * 1. Observe DataStore preferences
     * 2. Extract the comma-separated flattened ComponentNames
     * 3. Unflatten each ComponentName to get package + activity
     * 4. Look up each package in PackageManager for display name
     * 5. Build explicit launch intent targeting the specific activity
     * 6. Convert to AppInfo objects (filtering out uninstalled apps)
     * 7. Return as Flow that emits updates when data changes
     * 
     * IMPORTANT: flowOn(Dispatchers.IO)
     * Flow operators run on the collector's context by default.
     * If collected on Main (e.g., from UI), PackageManager queries would block the UI.
     * flowOn shifts upstream operations to the specified dispatcher.
     * 
     * @return Flow emitting list of recent AppInfo objects
     */
    override fun getRecentApps(): Flow<List<AppInfo>> {
        // DataStore.data is a Flow that emits the entire Preferences object
        // whenever ANY preference changes. We map it to extract just our key.
        return application.dataStore.data.map { preferences ->
            // Get saved flattened ComponentNames, default to empty string
            val recentComponentNames = preferences[recentAppsKey]
                ?.split(",") // Split by comma
                ?.filter { it.isNotEmpty() } // Remove empty strings
                ?: emptyList() // Default if null
            
            val pm = application.packageManager
            
            // Convert flattened ComponentNames to AppInfo objects
            // mapNotNull automatically filters out nulls (uninstalled apps)
            recentComponentNames.mapNotNull { flattenedComponentName ->
                // Unflatten the ComponentName string back to a ComponentName object
                // Format: "com.package/.ActivityClass" -> ComponentName(package, activity)
                val componentName = ComponentName.unflattenFromString(flattenedComponentName)
                    ?: return@mapNotNull null // Invalid format, skip
                
                val packageName = componentName.packageName
                val activityName = componentName.className
                
                try {
                    // Get app info from PackageManager using compat extension
                    // This throws NameNotFoundException if app was uninstalled
                    val appInfo = pm.getApplicationInfoCompat(packageName)
                    
                    // Build explicit launch intent targeting the SPECIFIC activity
                    // This is critical for apps with multiple launcher activities
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = componentName
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    
                    AppInfo(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = packageName,
                        activityName = activityName,
                        launchIntent = launchIntent
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    // App was uninstalled, return null (filtered out by mapNotNull)
                    null
                }
            }
        }.flowOn(Dispatchers.IO) // Run PackageManager queries on IO thread, not Main
    }
    
    /**
     * Save an app to the recent apps list.
     * 
     * Implementation details:
     * 1. Read current list from DataStore
     * 2. Remove component if already exists (to move to front)
     * 3. Add to front of list
     * 4. Limit to 8 apps maximum
     * 5. Save comma-separated flattened ComponentNames back to DataStore
     * 
     * The edit block is transactional - either all changes apply or none.
     * 
     * IMPORTANT: We save the flattened ComponentName, not just packageName.
     * This preserves the exact activity that was launched, which is critical
     * for apps with multiple launcher activities (e.g., MainActivity vs SettingsActivity).
     * 
     * @param componentName The flattened ComponentName string from ComponentName.flattenToString()
     *                      Format: "com.package/.ActivityClass"
     */
    override suspend fun saveRecentApp(componentName: String) {
        // edit() provides a transactional way to update preferences
        application.dataStore.edit { preferences ->
            // Get current value or empty string
            val current = preferences[recentAppsKey] ?: ""
            
            // Parse into mutable list
            val recentComponents = current.split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()
            
            // Remove if exists (we'll add to front)
            recentComponents.remove(componentName)
            
            // Add to front (most recent)
            recentComponents.add(0, componentName)
            
            // Save back: take first 8, join with commas
            preferences[recentAppsKey] = recentComponents.take(8).joinToString(",")
        }
        // Note: No need to manually refresh - the Flow from getRecentApps()
        // will automatically emit a new value because DataStore changed
    }
}
