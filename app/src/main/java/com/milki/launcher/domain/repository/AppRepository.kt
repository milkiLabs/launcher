/**
 * AppRepository.kt - Repository interface defining data access contract
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * AppRepository defines the contract for all app data operations.
 * 
 */
interface AppRepository {
    
    /**
     * Get all installed apps that have launcher icons.
     * 
     * Why suspend
     * - Prevents blocking the main thread
     * - Caller must be in a coroutine
     * - Repository can perform async operations internally
     * 
     * @return List of AppInfo objects, sorted alphabetically
     */
    suspend fun getInstalledApps(): List<AppInfo>
    
    /**
     * Get recently launched apps as an observable stream.
     * 
     * Returns a Flow instead because recent apps can change over time (when user launches new apps). 
     * TODO: is this actually needed?

     * @return Flow of the current list of recent apps
     */
    fun getRecentApps(): Flow<List<AppInfo>>
    
    /**
     * Save an app to the recent apps list.
     * 
     * This operation:
     * 1. Adds the app to the front of the list
     * 2. Removes duplicates (if app was already recent)
     * 3. Limits the list to 5 apps maximum
     * 4. Persists the list to storage
     * 
     * After this completes, getRecentApps() will emit a new list
     * with the updated order.
     * 
     * @param packageName The package name of the launched app
     */
    suspend fun saveRecentApp(packageName: String)
}
