/**
 * AppRepositoryImpl.kt - Implementation of AppRepository interface
 */

package com.milki.launcher.data.repository

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementation of AppRepository that uses Android's PackageManager and DataStore.

 * @param application The Application instance for accessing system services
 */
class AppRepositoryImpl(
    private val application: Application
) : AppRepository {
    
    // ============================================================================
    // DATASTORE SETUP
    // ============================================================================
    
    /**
     * Extension property to create/access DataStore.
     * 
     * The 'by preferencesDataStore' delegate creates the DataStore lazily
     * and caches it for future use.
     */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")
    
    /**
     * Key used to store recent apps as a comma-separated string.
     * Format: "com.whatsapp,com.youtube,com.gmail"
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
     * 3. Process apps in chunks of 8 for memory efficiency
     * 4. Convert each to AppInfo domain model
     * 5. Sort alphabetically by name
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
            
            // Step 2: Query the system
            // This returns a list of ResolveInfo objects representing matching activities
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            
            // Step 3 & 4: Process in chunks with controlled parallelism
            // chunked(8) splits the list into groups of 8
            // For each chunk, we create 8 async tasks that run in parallel
            // awaitAll waits for all 8 to complete before moving to next chunk
            resolveInfos.chunked(8).flatMap { chunk ->
                chunk.map { resolveInfo ->
                    async {
                        // Convert ResolveInfo to our domain model AppInfo
                        AppInfo(
                            // loadLabel gets the localized display name
                            name = resolveInfo.loadLabel(pm).toString(),
                            // activityInfo.packageName is the unique identifier
                            packageName = resolveInfo.activityInfo.packageName,
                            // Create launch intent for this package
                            launchIntent = pm.getLaunchIntentForPackage(
                                resolveInfo.activityInfo.packageName
                            )
                        )
                    }
                }.awaitAll() // Wait for all 8 in this chunk
            }.sortedBy { it.nameLower } // Sort alphabetically
        }
    }
    
    /**
     * Get recent apps as an observable Flow.
     * 
     * Implementation details:
     * 1. Observe DataStore preferences
     * 2. Extract the comma-separated package names
     * 3. Look up each package in PackageManager
     * 4. Convert to AppInfo objects (filtering out uninstalled apps)
     * 5. Return as Flow that emits updates when data changes
     * 
     * @return Flow emitting list of recent AppInfo objects
     */
    override fun getRecentApps(): Flow<List<AppInfo>> {
        // DataStore.data is a Flow that emits the entire Preferences object
        // whenever ANY preference changes. We map it to extract just our key.
        return application.dataStore.data.map { preferences ->
            // Get saved package names, default to empty string
            val recentPackages = preferences[recentAppsKey]
                ?.split(",") // Split by comma
                ?.filter { it.isNotEmpty() } // Remove empty strings
                ?: emptyList() // Default if null
            
            val pm = application.packageManager
            
            // Convert package names to AppInfo objects
            // mapNotNull automatically filters out nulls (uninstalled apps)
            recentPackages.mapNotNull { packageName ->
                try {
                    // Get app info from PackageManager
                    // This throws NameNotFoundException if app was uninstalled
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    
                    AppInfo(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = packageName,
                        launchIntent = pm.getLaunchIntentForPackage(packageName)
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    // App was uninstalled, return null (filtered out by mapNotNull)
                    null
                }
            }
        }
    }
    
    /**
     * Save an app to the recent apps list.
     * 
     * Implementation details:
     * 1. Read current list from DataStore
     * 2. Remove app if already exists (to move to front)
     * 3. Add to front of list
     * 4. Limit to 5 apps maximum
     * 5. Save comma-separated string back to DataStore
     * 
     * The edit block is transactional - either all changes apply or none.
     * 
     * @param packageName The package name to save
     */
    override suspend fun saveRecentApp(packageName: String) {
        // edit() provides a transactional way to update preferences
        application.dataStore.edit { preferences ->
            // Get current value or empty string
            val current = preferences[recentAppsKey] ?: ""
            
            // Parse into mutable list
            val recentPackages = current.split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()
            
            // Remove if exists (we'll add to front)
            recentPackages.remove(packageName)
            
            // Add to front (most recent)
            recentPackages.add(0, packageName)
            
            // Save back: take first 5, join with commas
            preferences[recentAppsKey] = recentPackages.take(5).joinToString(",")
        }
        // Note: No need to manually refresh - the Flow from getRecentApps()
        // will automatically emit a new value because DataStore changed
    }
}
