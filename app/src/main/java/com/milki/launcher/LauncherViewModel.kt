/**
 * LauncherViewModel.kt - Business logic and data management for the Milki Launcher
 * 
 * This ViewModel follows the MVVM (Model-View-ViewModel) architecture pattern.
 * It's responsible for:
 * - Coordinating between the UI and data layers
 * - Managing UI state (lists of apps)
 * - Delegating data operations to the Repository
 * 
 * After SOLID refactoring:
 * - No longer directly uses PackageManager or DataStore
 * - Depends on AppRepository interface (abstraction)
 * - Much cleaner and focused on UI state management only
 * 
 * Architecture:
 * - Depends on Domain layer (AppRepository interface)
 * - Data layer (AppRepositoryImpl) provides the actual implementation
 * - UI layer (MainActivity, Composables) observes this ViewModel
 * 
 * For detailed documentation, see: docs/LauncherViewModel.md
 */

package com.milki.launcher

// Android Application class - needed for creating Repository
import android.app.Application

// Compose State - for observable UI state
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

// ViewModel base classes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

// Domain layer imports (inner circle)
import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository

// Coroutines for async operations
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * LauncherViewModel manages UI state and coordinates data operations.
 * 
 * After SOLID refactoring, this ViewModel is much simpler:
 * - Before: 511 lines with direct PackageManager and DataStore usage
 * - After: ~90 lines, delegates data access to Repository
 * 
 * Key responsibilities:
 * 1. Expose observable state (installedApps, recentApps)
 * 2. Load initial data when ViewModel is created
 * 3. Observe recent apps changes via Flow
 * 4. Handle user actions (saveRecentApp)
 * 
 * What it does NOT do:
 * - Query PackageManager directly (Repository does this)
 * - Read/write DataStore directly (Repository does this)
 * - Manage threading details (Repository handles dispatchers)
 * 
 * @param application The Application instance (injected by ViewModelProvider)
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    
    // ========================================================================
    // REPOSITORY
    // ========================================================================
    
    /**
     * Repository instance for data operations.
     * 
     * In a production app with Dependency Injection (Hilt/Koin),
     * this would be injected via constructor:
     * class LauncherViewModel(
     *     application: Application,
     *     private val repository: AppRepository  // Injected!
     * ) : AndroidViewModel(application)
     * 
     * For simplicity in this educational project, we create it here.
     * The important thing is that we depend on the INTERFACE (AppRepository),
     * not the concrete implementation (AppRepositoryImpl).
     * 
     * This follows the Dependency Inversion Principle (DIP):
     * - High-level module (ViewModel) depends on abstraction (interface)
     * - Not on low-level details (implementation)
     */
    private val repository: AppRepository = AppRepositoryImpl(application)
    
    // ========================================================================
    // OBSERVABLE STATE
    // ========================================================================
    
    /**
     * List of all installed apps that have launcher icons.
     * 
     * This is a SnapshotStateList, which integrates with Compose's
     * state system. When apps are added/removed/changed, Compose
     * automatically recomposes any UI that reads this list.
     * 
     * The list is populated asynchronously when ViewModel is created.
     * It's read-only from outside (val), but the contents can be modified
     * via clear() and addAll().
     */
    val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    /**
     * List of recently launched apps (max 5).
     * 
     * This list is automatically updated when the repository's Flow emits
     * a new value. We don't need to manually refresh it after saving an app!
     * 
     * The Flow observation pattern (see init block) handles updates automatically.
     */
    val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    /**
     * init block runs when ViewModel is first created.
     * 
     * We immediately start:
     * 1. Loading installed apps (one-time operation)
     * 2. Observing recent apps (continuous via Flow)
     * 
     * The ViewModel survives configuration changes (rotation), so this
     * only runs once per ViewModel instance, not on every rotation.
     */
    init {
        loadInstalledApps()
        observeRecentApps()
    }
    
    // ========================================================================
    // DATA OPERATIONS
    // ========================================================================
    
    /**
     * Load installed apps from the repository.
     * 
     * This launches a coroutine that:
     * 1. Calls repository.getInstalledApps() (suspends until complete)
     * 2. Clears the current list
     * 3. Adds all loaded apps
     * 
     * The coroutine runs in viewModelScope, which means:
     * - It automatically uses Dispatchers.Main for UI updates
     * - It's cancelled if ViewModel is cleared (prevents memory leaks)
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            // Get apps from repository (runs on background thread internally)
            val apps = repository.getInstalledApps()
            
            // Update state (must be on Main thread, viewModelScope handles this)
            installedApps.clear()
            installedApps.addAll(apps)
        }
    }
    
    /**
     * Observe recent apps from the repository.
     * 
     * This sets up a Flow collector that:
     * 1. Listens to repository.getRecentApps() Flow
     * 2. Updates the recentApps list whenever data changes
     * 3. Runs continuously while ViewModel exists
     * 
     * Benefits of Flow:
     * - Automatic updates: When saveRecentApp() is called, DataStore updates,
     *   Flow emits new value, UI updates automatically
     * - No manual refresh needed
     * - Lifecycle-aware (cancelled when ViewModel cleared)
     */
    private fun observeRecentApps() {
        viewModelScope.launch {
            // collectLatest watches the Flow and runs the block for each emission
            repository.getRecentApps().collectLatest { apps ->
                recentApps.clear()
                recentApps.addAll(apps)
            }
        }
    }
    
    /**
     * Save an app to the recent apps list.
     * 
     * This method delegates to the repository. After the repository
     * updates DataStore, the Flow in observeRecentApps() will automatically
     * emit a new list, updating the UI.
     * 
     * @param packageName The package name of the launched app
     */
    fun saveRecentApp(packageName: String) {
        viewModelScope.launch {
            // Repository updates DataStore
            repository.saveRecentApp(packageName)
            // Note: No need to manually reload! Flow handles it automatically.
        }
    }
}
