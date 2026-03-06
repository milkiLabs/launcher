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
     * Observe all installed launcher apps as a reactive stream.
     *
     * HOW IT WORKS:
     * - Emits the full sorted list of launcher apps immediately on first collection.
     * - Automatically re-emits a fresh list whenever a package is installed,
     *   uninstalled, updated, or changed on the device.
     *
     * WHY THIS EXISTS:
     * A launcher must always reflect the current set of installed apps. Without
     * this, the app drawer and search results would show stale data after the
     * user installs or uninstalls something. Both AppDrawerViewModel and
     * SearchViewModel collect this flow to stay up-to-date without manual
     * refresh logic.
     *
        * IMPLEMENTATION CONTRACT:
        * - Multiple collectors should observe a shared upstream stream rather than
        *   triggering independent full app enumerations.
        * - The flow must never complete (it stays active for the lifetime of the
        *   collecting scope).
        * - Rapid package changes (e.g. bulk updates) should result in only one
        *   reload reaching collectors, not one per broadcast.
     *
     * @return Flow that emits sorted List<AppInfo> on every package change
     */
    fun observeInstalledApps(): Flow<List<AppInfo>>

    /**
     * Get recently launched apps as an observable stream.
     * 
     * Returns a Flow instead because recent apps can change over time (when user launches new apps).
     * This is used extensively in SearchViewModel and FilterAppsUseCase for filtering and
     * displaying recent apps to the user.

     * @return Flow of the current list of recent apps
     */
    fun getRecentApps(): Flow<List<AppInfo>>
    
    /**
     * Save an app to the recent apps list.
     * 
     * This operation:
     * 1. Adds the app to the front of the list
     * 2. Removes duplicates (if app was already recent)
     * 3. Limits the list to 8 apps maximum
     * 4. Persists the list to storage
     * 
     * After this completes, getRecentApps() will emit a new list
     * with the updated order.
     * 
     * IMPORTANT: We save the flattened ComponentName (package/activity), not just packageName.
     * This ensures that if an app has multiple launcher activities (e.g., MainActivity and
     * SettingsActivity), we preserve which specific one was launched.
     * 
     * @param componentName The flattened ComponentName string from ComponentName.flattenToString()
     *                      Format: "com.package/.ActivityClass" or "com.package/com.package.ActivityClass"
     */
    suspend fun saveRecentApp(componentName: String)
}
