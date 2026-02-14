/**
 * AppRepository.kt - Repository interface defining data access contract
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * AppRepository defines the contract for all app data operations.
 * 
 * What is a Repository?
 * A Repository mediates between the domain and data mapping layers,
 * acting like an in-memory collection of domain objects. It provides
 * a clean API for accessing data while hiding implementation details.
 * 
 * Why use a Repository interface?
 * 1. Testability: ViewModel can be tested with a mock implementation
 * 2. Flexibility: Can swap data sources without changing ViewModel
 * 3. Single Responsibility: All data access logic is in one place
 * 4. Abstraction: ViewModel doesn't know about PackageManager, DataStore, etc.
 * 
 * Example usage in ViewModel:
 * ```kotlin
 * class MyViewModel(private val repository: AppRepository) {
 *     suspend fun loadApps() {
 *         val apps = repository.getInstalledApps() // Don't care WHERE from
 *     }
 * }
 * ```
 */
interface AppRepository {
    
    /**
     * Get all installed apps that have launcher icons.
     * 
     * This is a suspend function because querying the system may take time.
     * The returned list is already sorted alphabetically by app name.
     * 
     * Why suspend?
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
     * Returns a Flow instead of a suspend function because recent apps
     * can change over time (when user launches new apps). The UI observes
     * this Flow and automatically updates when data changes.
     * 
     * Why Flow?
     * - Automatically notifies observers of changes
     * - No need to manually refresh the UI
     * - Lifecycle-aware (stops emitting when not needed)
     * 
     * @return Flow that emits the current list of recent apps
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
