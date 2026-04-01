/**
 * AppRepositoryImpl.kt - Implementation of AppRepository interface
 */

package com.milki.launcher.data.repository

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.milki.launcher.data.icon.AppIconMemoryCache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

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
    // REPOSITORY SCOPE
    // ============================================================================

    /**
     * Dedicated coroutine scope for long-lived repository streams.
     *
     * WHY A REPOSITORY SCOPE:
     * - AppRepositoryImpl is a process-lifetime singleton in DI.
     * - We need one shared hot stream for installed apps that all collectors can reuse.
     * - SupervisorJob keeps unrelated child failures isolated.
     */
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ============================================================================
    // PACKAGE CHANGE OBSERVATION
    // ============================================================================

    /**
     * Internal signal that fires whenever a package install, uninstall, update, or
     * change broadcast is received.
     *
     * WHY SharedFlow:
     * - replay = 0: Signal events are only triggers; we don't persist old triggers.
     * - extraBufferCapacity = 1 + DROP_OLDEST: If the previous reload is still
     *   in-flight when a new broadcast arrives, we keep only the latest signal.
     *   Combined with collectLatest in the repository refresh loop, this means
     *   rapid package changes (e.g. batch updates) naturally coalesce into
     *   one final completed reload.
     */
    private val packageChangeSignal = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Last successful installed-app snapshot refresh timestamp.
     *
     * TIMING SOURCE:
     * We use wall-clock millis because the value is only used for coarse freshness
     * checks and diagnostics inside this repository, not for elapsed-time math.
     */
    private val installedAppsSnapshotTimestampMillis = AtomicLong(0L)

    /**
     * Internal mutable snapshot state.
     *
     * IMPORTANT:
     * - Every emission is a detached immutable copy (`toList()`).
     * - Collectors should treat it as read-only data.
     */
    private val installedAppsSnapshot = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Shared hot trigger stream that drives repository cache refreshes.
     *
     * WHY THIS FIXES REPEATED SCANS:
     * - Before: each collector independently ran mapLatest { getInstalledApps() }.
     * - Now: one repository-level stream performs refreshes and fans out results.
     * - Search + Drawer now share one upstream PackageManager scan path.
     *
     * REFRESH POLICY:
     * - Initial refresh happens once when this shared stream starts.
     * - Subsequent refreshes happen only when packageChangeSignal emits.
     * - collectLatest cancels stale in-flight refresh if a newer signal arrives.
     */
    private val installedAppsRefreshTrigger: SharedFlow<Unit> = packageChangeSignal
        .onStart { emit(Unit) }
        .shareIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    init {
        registerPackageChangeReceiver()
        startInstalledAppsCacheRefreshLoop()
    }

    /**
     * Starts the long-lived refresh loop that keeps installedAppsSnapshot current.
     */
    private fun startInstalledAppsCacheRefreshLoop() {
        repositoryScope.launch {
            installedAppsRefreshTrigger.collectLatest {
                refreshInstalledAppsSnapshot()
            }
        }
    }

    /**
     * Performs one full installed-app scan and updates the cached snapshot.
     *
     * IMPORTANT BEHAVIOR:
     * - The emitted list is always copied with toList() to prevent accidental mutation.
     * - Timestamp updates only after a successful refresh.
     */
    private suspend fun refreshInstalledAppsSnapshot() {
        val latestApps = getInstalledApps().toList()
        installedAppsSnapshot.value = latestApps
        pruneUnavailableRecentApps(latestApps)
        installedAppsSnapshotTimestampMillis.set(System.currentTimeMillis())
    }

    /**
     * Removes invalid recent-app component entries when packages/components disappear.
     *
     * This keeps persisted recents aligned with the current launcher app set so
     * stale rows are not kept after uninstall/update events.
     */
    private suspend fun pruneUnavailableRecentApps(installedApps: List<AppInfo>) {
        val validComponents = installedApps
            .mapTo(mutableSetOf()) { app ->
                ComponentName(app.packageName, app.activityName).flattenToString()
            }

        application.dataStore.edit { preferences ->
            val currentRaw = preferences[recentAppsKey] ?: return@edit
            val currentComponents = currentRaw
                .split(",")
                .filter { it.isNotEmpty() }

            if (currentComponents.isEmpty()) {
                preferences.remove(recentAppsKey)
                return@edit
            }

            val filtered = linkedSetOf<String>()
            currentComponents.forEach { component ->
                if (component in validComponents) {
                    filtered += component
                }
            }

            val normalized = filtered.take(8)
            val normalizedRaw = normalized.joinToString(",")

            if (normalizedRaw == currentRaw) {
                return@edit
            }

            if (normalizedRaw.isEmpty()) {
                preferences.remove(recentAppsKey)
            } else {
                preferences[recentAppsKey] = normalizedRaw
            }
        }
    }

    /**
     * Registers a BroadcastReceiver for system package lifecycle events.
     *
     * WHICH BROADCASTS ARE MONITORED:
     * - ACTION_PACKAGE_ADDED:    new app installed
     * - ACTION_PACKAGE_REMOVED:  app uninstalled
     * - ACTION_PACKAGE_REPLACED: app updated (new version installed over old)
     * - ACTION_PACKAGE_CHANGED:  app component state changed (e.g. component disabled)
     *
     * All four use the "package" data scheme, which means the Intent's data URI
     * contains the affected package name (e.g. "package:com.example.app").
     *
     * LIFECYCLE:
     * The receiver is registered on the Application context, so it lives for the
     * entire process lifetime. Since AppRepositoryImpl is a Koin singleton that
     * also lives for the entire process, this is intentional and correct — there
     * is no leak risk.
     *
     * API 33+ COMPATIBILITY:
     * Starting with Android 13 (TIRAMISU), dynamically registered receivers must
     * declare an export flag. We use RECEIVER_NOT_EXPORTED because package change
     * broadcasts are protected system broadcasts — only the OS can send them, so
     * we don't need to accept intents from other apps.
     */
    private fun registerPackageChangeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Fire-and-forget trigger. The repository refresh loop uses
                // collectLatest, so only the newest pending refresh completes
                // if multiple signals arrive rapidly.
                packageChangeSignal.tryEmit(Unit)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            // Package broadcasts use the "package" URI scheme.
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
        }
    }

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
    // INSTALLED APPS OBSERVATION
    // ============================================================================

    /**
     * Returns the shared installed-app stream for all consumers.
     *
     * CONTRACT:
     * - All collectors observe the same repository-owned snapshot stream.
     * - Collecting this Flow never triggers an additional per-collector scan.
     * - Snapshot refresh is driven by package signals and the repository's
     *   startup refresh, not by collector count.
     */
    override fun observeInstalledApps(): Flow<List<AppInfo>> {
        return installedAppsSnapshot
    }

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

            // PERFORMANCE OPTIMIZATION:
            // Some packages expose multiple launcher activities. Without this cache we would
            // preload the same app icon repeatedly, which adds unnecessary startup work.
            val preloadedIconPackages = ConcurrentHashMap.newKeySet<String>()
            
            // Step 3 & 4: Launch all coroutines, limitedParallelism handles concurrency
            // We create one async per app, but only 8 run at a time
            // As each finishes, the next one starts immediately (no batch waiting)
            resolveInfos.map { resolveInfo ->
                async {
                    val packageName = resolveInfo.activityInfo.packageName

                    // Preload icon into the launcher's dedicated memory cache.
                    //
                    // WHY WE DO THIS HERE:
                    // This repository method already runs on a background dispatcher and
                    // iterates every launcher activity to build AppInfo objects.
                    // Preloading at this stage means the UI can usually render icons from
                    // memory on first composition instead of triggering per-item loads.
                    if (preloadedIconPackages.add(packageName)) {
                        AppIconMemoryCache.preload(
                            packageName = packageName,
                            icon = resolveInfo.loadIcon(pm)
                        )
                    }

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

                    AppInfo(
                        // loadLabel gets the localized display name
                        name = resolveInfo.loadLabel(pm).toString(),
                        // activityInfo.packageName is the package identifier
                        packageName = packageName,
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

                    // Preload icon for recent app entries as well.
                    //
                    // WHY THIS HELPS:
                    // Recent apps may appear before or independent of the full installed
                    // app list in some UI states. Preloading here keeps icon rendering
                    // consistently fast for recent sections too.
                    AppIconMemoryCache.preload(
                        packageName = packageName,
                        icon = pm.getApplicationIcon(packageName)
                    )
                    
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
