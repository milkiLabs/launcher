/**
 * LauncherViewModel.kt - Business logic and data management for the Milki Launcher
 * 
 * This ViewModel follows the MVVM (Model-View-ViewModel) architecture pattern.
 * It's responsible for:
 * - Loading the list of installed apps from the Android system
 * - Managing recently launched apps with persistence
 * - Running heavy operations on background threads
 * - Providing observable state for the UI layer
 * 
 * The ViewModel survives configuration changes (like screen rotation) and
 * uses coroutines for asynchronous operations. Data is persisted using
 * DataStore (Android's modern replacement for SharedPreferences).
 * 
 * For detailed documentation, see: docs/LauncherViewModel.md
 */

package com.milki.launcher

// ============================================================================
// IMPORTS - Android Framework
// ============================================================================
// Application class reference - we need this to access system services
// like PackageManager and DataStore. We use Application context instead of
// Activity context to avoid memory leaks.
import android.app.Application

// Intent is used to query the system for apps that can handle certain actions.
// We use it to find all apps that have launcher icons (MAIN/CATEGORY_LAUNCHER).
import android.content.Intent

// PackageManager is the system service that provides information about
// installed apps. We use it to get app names, icons, and launch intents.
import android.content.pm.PackageManager

// ============================================================================
// IMPORTS - Compose State
// ============================================================================
// mutableStateListOf creates an observable list that Compose can watch.
// When items are added/removed/changed, Compose automatically updates the UI.
import androidx.compose.runtime.mutableStateListOf

// SnapshotStateList is the underlying type that makes the list observable.
// It's thread-safe and integrates with Compose's snapshot system.
import androidx.compose.runtime.snapshots.SnapshotStateList

// ============================================================================
// IMPORTS - DataStore (Data Persistence)
// ============================================================================
// DataStore is Android's modern solution for storing key-value pairs.
// Unlike SharedPreferences, it uses Kotlin coroutines and Flow, and provides
// type safety and transactional consistency.
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// ============================================================================
// IMPORTS - ViewModel
// ============================================================================
// AndroidViewModel is a ViewModel that includes a reference to the Application.
// This is useful when you need Application context (not Activity context).
import androidx.lifecycle.AndroidViewModel

// viewModelScope is a coroutine scope tied to the ViewModel's lifecycle.
// Coroutines launched here are automatically cancelled when ViewModel is cleared.
import androidx.lifecycle.viewModelScope

// ============================================================================
// IMPORTS - Kotlin Coroutines
// ============================================================================
// Dispatchers determine which thread a coroutine runs on:
// - Dispatchers.Main: UI thread (for updating UI)
// - Dispatchers.IO: Optimized for disk and network I/O
// - Dispatchers.Default: For CPU-intensive work
import kotlinx.coroutines.Dispatchers

// async creates a coroutine that returns a result (Deferred).
// Used for parallel processing of multiple tasks.
import kotlinx.coroutines.async

// awaitAll waits for multiple async operations to complete.
// Used with chunked processing to wait for each chunk.
import kotlinx.coroutines.awaitAll

// Flow.first() gets the first value from a Flow and cancels the subscription.
// Used with DataStore to read a value once.
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// launch starts a new coroutine in the given scope.
// Used to start background operations from ViewModel.
import kotlinx.coroutines.launch

// withContext switches to a different dispatcher for a code block.
// Used to move work to background threads and results back to UI thread.
import kotlinx.coroutines.withContext

// ============================================================================
// DATASTORE EXTENSION
// ============================================================================
/**
 * Extension property to create/access DataStore for this app.
 * 
 * This creates a preferences DataStore with the filename "launcher_prefs".
 * The DataStore is stored in the app's private directory and persists across
 * app restarts (but is deleted when app is uninstalled).
 * 
 * We define this as an extension on Context so any Context can access it:
 * context.dataStore.edit { ... }
 * 
 * The 'by preferencesDataStore' delegate handles:
 * - Creating the DataStore if it doesn't exist
 * - Returning the existing one if it does
 * - Managing the DataStore lifecycle
 */
private val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

// ============================================================================
// DATA CLASS: AppInfo
// ============================================================================
/**
 * AppInfo represents a single installed application.
 * 
 * This data class holds all the information we need about an app:
 * - name: The human-readable display name (e.g., "YouTube")
 * - packageName: The unique identifier (e.g., "com.google.android.youtube")
 * - launchIntent: The Intent used to launch the app
 * 
 * We also cache lowercase versions of the name and package name to speed up
 * search operations. Without caching, we'd call lowercase() on every search
 * keystroke, which would be slow for 200+ apps.
 * 
 * The 'by lazy' delegate means the lowercase strings are computed only once,
 * the first time they're accessed, and then cached for future use.
 * 
 * @property name The display name of the app
 * @property packageName The unique package identifier
 * @property launchIntent Intent to launch the app (null if not launchable)
 */
data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?
) {
    /**
     * Cached lowercase version of the app name.
     * Used for case-insensitive search matching.
     * Computed lazily (on first access) and cached thereafter.
     */
    val nameLower: String by lazy { name.lowercase() }
    
    /**
     * Cached lowercase version of the package name.
     * Used for case-insensitive search matching by package.
     * Computed lazily (on first access) and cached thereafter.
     */
    val packageLower: String by lazy { packageName.lowercase() }
}

// ============================================================================
// MAIN VIEWMODEL CLASS
// ============================================================================
/**
 * LauncherViewModel manages all data operations for the launcher.
 * 
 * Key responsibilities:
 * 1. Load installed apps from the system (using PackageManager)
 * 2. Load/save recently launched apps (using DataStore)
 * 3. Provide observable state to the UI
 * 4. Manage coroutines for background operations
 * 
 * Why AndroidViewModel instead of regular ViewModel?
 * - We need access to Application context for PackageManager and DataStore
 * - Application context is safe to hold (lives for app lifetime)
 * - Activity context would cause memory leaks
 * 
 * @param application The Application instance (injected by ViewModelProvider)
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    
    // ========================================================================
    // DATASTORE KEY
    // ========================================================================
    /**
     * DataStore key for storing recent app package names.
     * 
     * We store recent apps as a comma-separated string of package names:
     * "com.whatsapp,com.youtube,com.gmail,com.twitter,com.spotify"
     * 
     * Using a Preferences.Key ensures type safety - we can't accidentally
     * store the wrong type or use the wrong key name.
     */
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    // ========================================================================
    // CUSTOM DISPATCHER (CONTROLLED PARALLELISM)
    // ========================================================================
    /**
     * Custom dispatcher that limits concurrency to 8 parallel operations.
     * 
     * Why do we need this?
     * When loading 150+ apps, creating a coroutine for each would launch
     * 150+ concurrent operations, causing a memory spike. By using
     * limitedParallelism(8), we ensure only 8 operations run at once,
     * and the rest wait in queue.
     * 
     * Why 8?
     * - Matches typical mobile CPU core count (4-8 cores)
     * - Balances speed and memory usage
     * - Prevents overwhelming the system
     * 
     * Without this: Memory spike, possible OutOfMemoryError
     * With this: Controlled memory usage, smooth performance
     */
    private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
    
    // ========================================================================
    // OBSERVABLE STATE LISTS
    // ========================================================================
    /**
     * List of all installed apps that have launcher icons.
     * 
     * This is a SnapshotStateList, which is a special type that integrates
     * with Compose's state system. When apps are added/removed/changed,
     * Compose automatically recomposes any UI that reads this list.
     * 
     * The list is populated asynchronously by loadInstalledApps() when
     * the ViewModel is created.
     * 
     * Public val means the UI can read this list but cannot replace it
     * (must use clear() and addAll() to modify).
     */
    val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    /**
     * List of recently launched apps (max 5).
     * 
     * This list is persisted to DataStore so it survives app restarts.
     * Apps are ordered with most recent first.
     * 
     * When a user launches an app, saveRecentApp() is called, which:
     * 1. Adds the app to the front of the list
     * 2. Removes duplicates
     * 3. Limits to 5 apps
     * 4. Saves to DataStore
     * 5. Reloads the list to update UI
     */
    val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    // ========================================================================
    // INITIALIZATION BLOCK
    // ========================================================================
    /**
     * init block runs when the ViewModel is first created.
     * 
     * We immediately start loading data so it's ready when the UI needs it.
     * Both operations run concurrently in separate coroutines because they
     * don't depend on each other.
     * 
     * The ViewModel survives configuration changes (rotation), so this only
     * runs once per ViewModel instance, not on every rotation.
     */
    init {
        // Load all installed apps from the system
        // This queries PackageManager and can take a moment
        loadInstalledApps()
        
        // Load recently launched apps from DataStore
        // This reads persisted data from disk
        loadRecentApps()
    }
    
    // ========================================================================
    // LOAD INSTALLED APPS
    // ========================================================================
    /**
     * Loads all installed apps that have launcher icons.
     * 
     * This method:
     * 1. Queries PackageManager for all apps with MAIN/LAUNCHER intent filter
     * 2. Loads app info (name, package, launch intent) for each app
     * 3. Sorts apps alphabetically by name
     * 4. Updates installedApps state (triggering UI update)
     * 
     * Performance considerations:
     * - Runs on background thread (Dispatchers.IO) to avoid freezing UI
     * - Uses chunked parallelism (8 apps at a time) to control memory
     * - Pre-computes lowercase strings for fast searching
     * 
     * This is called once during initialization. In a production app, you
     * might also call this when apps are installed/uninstalled (using
     * PackageManager broadcasts).
     */
    private fun loadInstalledApps() {
        // Launch a coroutine in the ViewModel's scope.
        // This coroutine will be automatically cancelled if ViewModel is cleared.
        viewModelScope.launch {
            
            // Switch to the limited dispatcher for background work.
            // This ensures heavy operations don't block the UI thread.
            withContext(limitedDispatcher) {
                
                // -----------------------------------------------------------
                // GET PACKAGEMANAGER
                // -----------------------------------------------------------
                // PackageManager is the system service that knows about all
                // installed apps. We get it from the Application context.
                val pm = getApplication<Application>().packageManager
                
                // -----------------------------------------------------------
                // CREATE QUERY INTENT
                // -----------------------------------------------------------
                // We create an Intent that matches all apps with launcher icons.
                // MAIN action + LAUNCHER category = apps that appear in drawer.
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                // -----------------------------------------------------------
                // QUERY THE SYSTEM
                // -----------------------------------------------------------
                // queryIntentActivities returns a list of all activities that
                // can handle our Intent. For MAIN/LAUNCHER, this gives us
                // all apps with icons.
                // 
                // The '0' means no special flags.
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                
                // -----------------------------------------------------------
                // PROCESS APPS WITH CONTROLLED PARALLELISM
                // -----------------------------------------------------------
                // We use chunked processing to avoid memory spikes.
                // Instead of launching 150+ coroutines at once, we process
                // in chunks of 8.
                //
                // The flow is:
                // 1. Split list into chunks of 8
                // 2. For each chunk, launch 8 async operations in parallel
                // 3. Wait for all 8 to complete (awaitAll)
                // 4. Move to next chunk
                // 5. Combine all results and sort alphabetically
                val apps = resolveInfos.chunked(8).flatMap { chunk ->
                    // For each app in the chunk, create an async operation
                    // that loads the AppInfo. async returns a Deferred<AppInfo>.
                    chunk.map { resolveInfo ->
                        async {
                            // Create AppInfo for this app
                            AppInfo(
                                // loadLabel gets the display name (respects locale)
                                name = resolveInfo.loadLabel(pm).toString(),
                                
                                // activityInfo.packageName is the unique ID
                                packageName = resolveInfo.activityInfo.packageName,
                                
                                // getLaunchIntentForPackage creates the launch Intent
                                launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                            )
                        }
                    }.awaitAll() // Wait for all 8 operations in this chunk
                }.sortedBy { it.nameLower } // Sort alphabetically by lowercase name
                
                // -----------------------------------------------------------
                // UPDATE UI STATE
                // -----------------------------------------------------------
                // Switch back to Main dispatcher to update Compose state.
                // Compose state must be updated on the UI thread.
                withContext(Dispatchers.Main) {
                    // Clear existing list (if any)
                    installedApps.clear()
                    // Add all loaded apps
                    installedApps.addAll(apps)
                    // This triggers recomposition of any UI observing installedApps
                }
            }
        }
    }
    
    // ========================================================================
    // LOAD RECENT APPS
    // ========================================================================
    /**
     * Loads recently launched apps from DataStore.
     * 
     * This method:
     * 1. Reads the saved package names from DataStore
     * 2. Looks up each app in PackageManager
     * 3. Creates AppInfo objects for valid apps
     * 4. Updates recentApps state
     * 
     * Apps that have been uninstalled are silently ignored (return null
     * from mapNotNull).
     * 
     * This is called:
     * - During initialization (init block)
     * - After saving a new recent app (to refresh the list)
     */
    private fun loadRecentApps() {
        // Launch coroutine in ViewModel scope
        viewModelScope.launch {
            // Use IO dispatcher for disk I/O (DataStore read)
            withContext(Dispatchers.IO) {
                
                // -----------------------------------------------------------
                // READ FROM DATASTORE
                // -----------------------------------------------------------
                // DataStore.data is a Flow that emits the Preferences object.
                // We use map to extract our specific key, then first() to get
                // just the first emission (we don't need continuous updates).
                val recentPackages = getApplication<Application>().dataStore.data.map { preferences ->
                    // Get the string value, or empty string if not set
                    preferences[recentAppsKey] ?: ""
                }.first() // Take first value and cancel Flow subscription
                 .split(",") // Split comma-separated string into list
                 .filter { it.isNotEmpty() } // Remove empty strings
                
                // -----------------------------------------------------------
                // GET PACKAGEMANAGER
                // -----------------------------------------------------------
                val pm = getApplication<Application>().packageManager
                
                // -----------------------------------------------------------
                // CONVERT PACKAGE NAMES TO APPINFO OBJECTS
                // -----------------------------------------------------------
                // mapNotNull transforms each package name to an AppInfo,
                // but automatically removes null results (for uninstalled apps).
                val apps = recentPackages.mapNotNull { packageName ->
                    try {
                        // Get ApplicationInfo for this package
                        // This throws NameNotFoundException if app not installed
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        
                        // Create AppInfo from the ApplicationInfo
                        AppInfo(
                            name = pm.getApplicationLabel(appInfo).toString(),
                            packageName = packageName,
                            launchIntent = pm.getLaunchIntentForPackage(packageName)
                        )
                    } catch (e: Exception) {
                        // App not found (uninstalled) - return null
                        // mapNotNull will filter this out
                        null
                    }
                }
                
                // -----------------------------------------------------------
                // UPDATE UI STATE
                // -----------------------------------------------------------
                withContext(Dispatchers.Main) {
                    recentApps.clear()
                    recentApps.addAll(apps)
                }
            }
        }
    }
    
    // ========================================================================
    // SAVE RECENT APP
    // ========================================================================
    /**
     * Saves an app to the recent apps list.
     * 
     * This method:
     * 1. Reads current recent apps list from DataStore
     * 2. Removes the app if already in list (to avoid duplicates)
     * 3. Adds app to front of list (most recent)
     * 4. Limits list to 5 apps
     * 5. Saves back to DataStore
     * 6. Reloads the list to update UI
     * 
     * This is called from MainActivity when user launches an app.
     * 
     * @param packageName The package name of the launched app
     */
    fun saveRecentApp(packageName: String) {
        // Launch coroutine in ViewModel scope
        viewModelScope.launch {
            // Use DataStore's edit function to atomically update preferences.
            // The edit block is transactional - either all changes apply or none.
            getApplication<Application>().dataStore.edit { preferences ->
                
                // Read current value, default to empty string
                val current = preferences[recentAppsKey] ?: ""
                
                // Parse into mutable list
                val recentPackages = current.split(",")
                    .filter { it.isNotEmpty() } // Remove empty strings
                    .toMutableList() // Make mutable so we can modify
                
                // Remove if already exists (we'll add to front)
                // This ensures no duplicates and moves to front if already recent
                recentPackages.remove(packageName)
                
                // Add to front of list (most recent)
                recentPackages.add(0, packageName)
                
                // Save back to DataStore:
                // 1. Take first 5 (limit to 5 recent apps)
                // 2. Join with commas
                // 3. Store in preferences
                preferences[recentAppsKey] = recentPackages.take(5).joinToString(",")
            }
            
            // Reload to update UI with new order
            // This ensures the UI shows the most recent app immediately
            loadRecentApps()
        }
    }
}
