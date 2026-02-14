# LauncherViewModel.kt - Detailed Documentation

## Overview

`LauncherViewModel.kt` is the brain of the Milki Launcher. It handles all the data operations, including:
- Loading the list of installed apps from the system
- Managing and persisting recent apps
- Providing data to the UI layer
- Running operations on background threads

This file follows the MVVM (Model-View-ViewModel) architecture pattern and uses Kotlin Coroutines for asynchronous operations.

---

## Table of Contents

1. [Imports and Setup](#imports-and-setup)
2. [DataStore Extension](#datastore-extension)
3. [AppInfo Data Class](#appinfo-data-class)
4. [LauncherViewModel Class](#launcherviewmodel-class)
5. [State Variables](#state-variables)
6. [Initialization (init block)](#initialization-init-block)
7. [Loading Installed Apps](#loading-installed-apps)
8. [Loading Recent Apps](#loading-recent-apps)
9. [Saving Recent Apps](#saving-recent-apps)
10. [Concurrency and Threading](#concurrency-and-threading)
11. [Performance Optimizations](#performance-optimizations)
12. [How It All Works Together](#how-it-all-works-together)

---

## Imports and Setup

```kotlin
package com.milki.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

### Import Breakdown

**Android Framework**:
- `Application`: The base class for maintaining global application state
- `Intent`: Used to query and launch apps
- `PackageManager`: System service for getting app information

**Compose State**:
- `mutableStateListOf`: Creates an observable list that triggers UI updates
- `SnapshotStateList`: The type of observable list used by Compose

**DataStore**:
- `DataStore`: Modern data persistence solution
- `Preferences`: Key-value storage
- `edit`: Function to modify preferences
- `stringPreferencesKey`: Creates a key for string values
- `preferencesDataStore`: Extension to create DataStore

**ViewModel**:
- `AndroidViewModel`: ViewModel with access to Application context
- `viewModelScope`: Coroutine scope tied to ViewModel lifecycle

**Coroutines**:
- `Dispatchers`: Defines which thread to run on
- `async`: Creates a coroutine that returns a result
- `awaitAll`: Waits for multiple async operations
- `flow.first()`: Gets first value from Flow
- `flow.map()`: Transforms Flow values
- `launch`: Starts a new coroutine
- `withContext`: Switches to a different dispatcher

---

## DataStore Extension

```kotlin
private val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")
```

### What is DataStore?

DataStore is a modern data persistence library that replaces SharedPreferences. It's:
- **Type-safe**: Uses strongly typed keys
- **Asynchronous**: Uses Kotlin Coroutines and Flow
- **Transaction-based**: Ensures data consistency
- **Migration-friendly**: Easy to migrate from SharedPreferences

### The Extension Property

This line creates an extension property on the `Context` class:
- `android.content.Context.dataStore`: Makes `dataStore` available on any Context
- `private`: Only visible within this file
- `by preferencesDataStore`: Delegates to the DataStore factory
- `name = "launcher_prefs"`: The filename for the preferences

**Where is the data stored?**:
- Internal app storage: `/data/data/com.milki.launcher/files/datastore/launcher_prefs.preferences_pb`
- Private to the app (other apps can't access it)
- Survives app updates
- Cleared when app is uninstalled

### Why Use an Extension?

Instead of creating a DataStore instance manually every time, we can simply call:
```kotlin
context.dataStore
```

This is cleaner and follows the Kotlin idioms.

---

## AppInfo Data Class

```kotlin
data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?
) {
    // Cache lowercase for faster filtering
    val nameLower: String by lazy { name.lowercase() }
    val packageLower: String by lazy { packageName.lowercase() }
}
```

### What is a Data Class?

A `data class` in Kotlin is a class that is designed to hold data. It automatically generates:
- `equals()` and `hashCode()`
- `toString()`
- `copy()` function
- Component functions for destructuring

### Properties Explained

**name**: `String`
- The display name of the app (e.g., "YouTube")
- Retrieved from `PackageManager.getApplicationLabel()`
- Can change based on user's language settings

**packageName**: `String`
- The unique identifier for the app (e.g., "com.google.android.youtube")
- Set by the app developer and never changes
- Used to launch apps and identify them

**launchIntent**: `Intent?`
- The Intent used to launch the app
- Nullable (some apps might not be launchable)
- Created by `PackageManager.getLaunchIntentForPackage()`

### Lazy Properties for Performance

```kotlin
val nameLower: String by lazy { name.lowercase() }
val packageLower: String by lazy { packageName.lowercase() }
```

**The `lazy` Delegate**:
- Delays computation until first access
- Caches the result after first computation
- Thread-safe by default

**Why Pre-compute Lowercase?**

When searching, we need to compare lowercase versions of strings:
```kotlin
// Without caching (computed every time):
app.name.lowercase() == query.lowercase()

// With caching (computed once):
app.nameLower == queryLower
```

**Performance Impact**:
- For 200 apps and 10 search operations: 2000 lowercase operations saved
- Significant on slower devices
- No memory overhead (strings are small)

---

## LauncherViewModel Class

```kotlin
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
```

### Why AndroidViewModel?

`AndroidViewModel` extends `ViewModel` and takes an `Application` in its constructor:

**Regular ViewModel**:
```kotlin
class MyViewModel : ViewModel()
```
- Can't access Android-specific classes easily
- Good for pure business logic

**AndroidViewModel**:
```kotlin
class MyViewModel(application: Application) : AndroidViewModel(application)
```
- Has access to `getApplication<Application>()`
- Can use `PackageManager`, `Resources`, `DataStore`
- Still survives configuration changes

**Why not pass Context?**:
- ViewModels outlive Activities (survive rotation)
- Holding a reference to an Activity causes memory leaks
- Application context is safe to hold (lives for app lifetime)

### Class Body Structure

```kotlin
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    // DataStore key
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    // Dispatcher for controlled parallelism
    private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
    
    // Observable state lists
    val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    // Initialization
    init {
        loadInstalledApps()
        loadRecentApps()
    }
    
    // Methods for loading and saving data...
}
```

---

## State Variables

### recentAppsKey

```kotlin
private val recentAppsKey = stringPreferencesKey("recent_apps")
```

Creates a type-safe key for storing recent apps in DataStore.

**Why a Key Object?**:
- Type-safe (can't accidentally use wrong type)
- Prevents typos (one definition, reused everywhere)
- Can add metadata in the future

### limitedDispatcher

```kotlin
private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
```

This creates a custom coroutine dispatcher that limits concurrency to 8 parallel operations.

**What is a Dispatcher?**:
A dispatcher determines which thread or thread pool a coroutine runs on:
- `Dispatchers.Main`: UI thread (for updating UI)
- `Dispatchers.IO`: Optimized for disk and network I/O
- `Dispatchers.Default`: Optimized for CPU-intensive work

**What is limitedParallelism?**:
Normally, when you launch many coroutines on `Dispatchers.IO`, they all run in parallel. With `limitedParallelism(8)`:
- Maximum 8 coroutines run simultaneously
- Others wait in queue
- Prevents memory spikes and system overload

**Why 8?**:
- Balances speed and memory usage
- Based on typical CPU core count (4-8 cores on mobile)
- Prevents launching 150+ coroutines at once (memory spike)

### installedApps

```kotlin
val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
```

**mutableStateListOf**: Creates an observable list for Compose.

**SnapshotStateList**: 
- A special list that integrates with Compose's state system
- When items are added/removed/changed, Compose automatically recomposes
- Thread-safe for reads and writes

**Visibility**: `val` (public getter)
- The UI can read this list
- The UI cannot replace the list (must modify in place)
- Ensures ViewModel controls the data

### recentApps

```kotlin
val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
```

Same as `installedApps`, but stores recently launched apps (max 5).

---

## Initialization (init block)

```kotlin
init {
    loadInstalledApps()
    loadRecentApps()
}
```

### What is an init block?

An `init` block runs when the class is instantiated (constructed). It's like the constructor body.

**When does this run?**:
- When ViewModel is first created
- After `super()` call in constructor
- Before the ViewModel is returned to the Activity

**Why not put this in onCreate?**:
- ViewModel shouldn't depend on Activity lifecycle
- Needs to load data immediately for fast UI display
- Survives Activity recreation (rotation)

### What It Does

1. **loadInstalledApps()**: Queries the system for all installed apps
2. **loadRecentApps()**: Reads saved recent apps from DataStore

Both run concurrently (not sequentially) because they're launched in separate coroutines.

---

## Loading Installed Apps

```kotlin
private fun loadInstalledApps() {
    viewModelScope.launch {
        withContext(limitedDispatcher) {
            val pm = getApplication<Application>().packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            
            // Load app info with controlled parallelism
            val apps = resolveInfos.chunked(8).flatMap { chunk ->
                chunk.map { resolveInfo ->
                    async {
                        AppInfo(
                            name = resolveInfo.loadLabel(pm).toString(),
                            packageName = resolveInfo.activityInfo.packageName,
                            launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                        )
                    }
                }.awaitAll()
            }.sortedBy { it.nameLower }
            
            // Update UI state on main thread
            withContext(Dispatchers.Main) {
                installedApps.clear()
                installedApps.addAll(apps)
            }
        }
    }
}
```

### Step-by-Step Breakdown

#### Step 1: Launch Coroutine

```kotlin
viewModelScope.launch {
```

**viewModelScope**: A coroutine scope tied to the ViewModel's lifecycle.
- Coroutines launched here are automatically cancelled when ViewModel is cleared
- Prevents memory leaks
- Survives configuration changes

**launch**: Starts a new coroutine.
- Doesn't block the calling thread
- Runs the code inside concurrently

#### Step 2: Switch to Background Thread

```kotlin
withContext(limitedDispatcher) {
```

**withContext**: Switches to a different dispatcher for the code block.

**Why background thread?**:
- `queryIntentActivities` can be slow (queries system database)
- `loadLabel` reads app resources
- Can't run slow operations on main thread (would freeze UI)

**Why limitedDispatcher?**:
- Controls parallelism
- Prevents memory spikes

#### Step 3: Get PackageManager

```kotlin
val pm = getApplication<Application>().packageManager
```

**getApplication<Application>()**: Gets the Application instance passed to constructor.

**packageManager**: System service for app information.

#### Step 4: Create Query Intent

```kotlin
val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
    addCategory(Intent.CATEGORY_LAUNCHER)
}
```

**What is this Intent?**:
This creates an Intent that matches all "launcher" activities (apps that appear in the app drawer).

**Intent.ACTION_MAIN**: Entry point of the app.

**Intent.CATEGORY_LAUNCHER**: Should appear in launcher.

**Why null data?**: We're querying, not launching. No specific data needed.

#### Step 5: Query the System

```kotlin
val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
```

**queryIntentActivities**: Returns a list of all activities that match the Intent.

**Return value**: `List<ResolveInfo>` - each ResolveInfo contains information about one app.

**The `0` flag**: No special flags (basic query).

**What this returns**:
- All user-installed apps
- System apps that have launcher icons
- Updated apps (both old and new entries might appear)

#### Step 6: Process Apps with Controlled Parallelism

```kotlin
val apps = resolveInfos.chunked(8).flatMap { chunk ->
    chunk.map { resolveInfo ->
        async {
            AppInfo(
                name = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
            )
        }
    }.awaitAll()
}.sortedBy { it.nameLower }
```

This is the most complex part. Let's break it down:

**chunked(8)**:
```kotlin
resolveInfos.chunked(8)
```
Splits the list into chunks of 8 items.

Example:
```
Input:  [A, B, C, D, E, F, G, H, I, J, K]
Output: [[A, B, C, D, E, F, G, H], [I, J, K]]
```

**flatMap**:
Processes each chunk and flattens the results into a single list.

**chunk.map { ... }**:
For each ResolveInfo in the chunk, create an async operation.

**async { ... }**:
Creates a coroutine that will execute the block and return a result.

**awaitAll()**:
Waits for all async operations in the chunk to complete.

**The Flow**:
```
Chunk 1: [App1, App2, ..., App8]
    ↓
Launch 8 coroutines in parallel
    ↓
Wait for all 8 to complete
    ↓
Move to Chunk 2
    ↓
Repeat until done
    ↓
Sort alphabetically
```

**Why This Approach?**:

Without chunks (bad):
```kotlin
// Launches ALL coroutines at once - memory spike!
resolveInfos.map { async { createAppInfo(it) } }.awaitAll()
```

With chunks (good):
```kotlin
// Only 8 coroutines at a time - controlled memory
resolveInfos.chunked(8).flatMap { chunk ->
    chunk.map { async { createAppInfo(it) } }.awaitAll()
}
```

#### Step 7: Sort Alphabetically

```kotlin
}.sortedBy { it.nameLower }
```

Sorts apps by their lowercase name for consistent display.

Uses `nameLower` (pre-computed) for better performance.

#### Step 8: Update UI on Main Thread

```kotlin
withContext(Dispatchers.Main) {
    installedApps.clear()
    installedApps.addAll(apps)
}
```

**Why switch back to Main?**:
- Compose state updates must happen on UI thread
- `installedApps` is a `SnapshotStateList` bound to Compose

**clear() then addAll()**:
- Clears existing list (if reloading)
- Adds all new apps
- Triggers recomposition of the UI

---

## Loading Recent Apps

```kotlin
private fun loadRecentApps() {
    viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val recentPackages = getApplication<Application>().dataStore.data.map { preferences ->
                preferences[recentAppsKey] ?: ""
            }.first().split(",").filter { it.isNotEmpty() }
            
            val pm = getApplication<Application>().packageManager
            
            val apps = recentPackages.mapNotNull { packageName ->
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    AppInfo(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = packageName,
                        launchIntent = pm.getLaunchIntentForPackage(packageName)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            withContext(Dispatchers.Main) {
                recentApps.clear()
                recentApps.addAll(apps)
            }
        }
    }
}
```

### Step-by-Step Breakdown

#### Step 1: Read from DataStore

```kotlin
val recentPackages = getApplication<Application>().dataStore.data.map { preferences ->
    preferences[recentAppsKey] ?: ""
}.first().split(",").filter { it.isNotEmpty() }
```

**dataStore.data**: Returns a `Flow<Preferences>` that emits whenever data changes.

**map { ... }**: Transforms the Flow to extract our specific key.

**preferences[recentAppsKey]**: Gets the string value, or null if not set.

**first()**: Takes only the first emission and cancels the Flow.

**split(",")**: Converts "com.app1,com.app2" to ["com.app1", "com.app2"].

**filter { it.isNotEmpty() }**: Removes empty strings.

**Example**:
```
Stored: "com.whatsapp,com.youtube,com.gmail"
Result: ["com.whatsapp", "com.youtube", "com.gmail"]
```

#### Step 2: Convert Package Names to AppInfo

```kotlin
val apps = recentPackages.mapNotNull { packageName ->
    try {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        AppInfo(
            name = pm.getApplicationLabel(appInfo).toString(),
            packageName = packageName,
            launchIntent = pm.getLaunchIntentForPackage(packageName)
        )
    } catch (e: Exception) {
        null
    }
}
```

**mapNotNull**: Maps each item and removes null results.

**Why try-catch?**:
- User might have uninstalled an app
- App might be disabled
- Package name might be corrupted

**getApplicationInfo**: Gets detailed info about an installed app.
- Throws `PackageManager.NameNotFoundException` if app not found

**Why not use queryIntentActivities?**:
- We already know the package names
- Faster to get specific app info

---

## Saving Recent Apps

```kotlin
fun saveRecentApp(packageName: String) {
    viewModelScope.launch {
        getApplication<Application>().dataStore.edit { preferences ->
            val current = preferences[recentAppsKey] ?: ""
            val recentPackages = current.split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()
            
            recentPackages.remove(packageName)
            recentPackages.add(0, packageName)
            
            preferences[recentAppsKey] = recentPackages.take(5).joinToString(",")
        }
        loadRecentApps()
    }
}
```

### When is this Called?

Called from `MainActivity` when user launches an app:
```kotlin
onLaunchApp = { appInfo ->
    appInfo.launchIntent?.let { startActivity(it) }
    viewModel.saveRecentApp(appInfo.packageName)  // <-- Here
    showSearch = false
    searchQuery = ""
}
```

### Step-by-Step Breakdown

#### Step 1: Edit DataStore

```kotlin
getApplication<Application>().dataStore.edit { preferences ->
```

**edit**: Opens a transaction to modify preferences.
- Thread-safe
- Atomic (all changes apply together)
- Suspends until complete

#### Step 2: Parse Current List

```kotlin
val current = preferences[recentAppsKey] ?: ""
val recentPackages = current.split(",")
    .filter { it.isNotEmpty() }
    .toMutableList()
```

Converts the stored comma-separated string to a mutable list.

**Example**:
```
Stored: "com.whatsapp,com.youtube"
Result: MutableList["com.whatsapp", "com.youtube"]
```

#### Step 3: Update List

```kotlin
recentPackages.remove(packageName)
recentPackages.add(0, packageName)
```

**remove(packageName)**: Removes if already exists (prevents duplicates).

**add(0, packageName)**: Adds to the beginning (most recent first).

**Example**:
```
Before: ["com.youtube", "com.whatsapp"]
Launched: "com.whatsapp"
After: ["com.whatsapp", "com.youtube"]
```

#### Step 4: Limit and Save

```kotlin
preferences[recentAppsKey] = recentPackages.take(5).joinToString(",")
```

**take(5)**: Keeps only the first 5 items (most recent).

**joinToString(",")**: Converts list back to comma-separated string.

**Example**:
```
Input: ["com.a", "com.b", "com.c", "com.d", "com.e", "com.f"]
take(5): ["com.a", "com.b", "com.c", "com.d", "com.e"]
joinToString: "com.a,com.b,com.c,com.d,com.e"
```

#### Step 5: Reload Recent Apps

```kotlin
loadRecentApps()
```

After saving, immediately reload to update the UI.

**Why reload?**:
- Ensures UI shows latest data
- Handles case where app was uninstalled
- Updates display names (might have changed)

---

## Concurrency and Threading

### Thread Usage Summary

```
┌─────────────────────────────────────────────────────────────┐
│                     MAIN THREAD (UI)                        │
│  - Composable functions                                     │
│  - State updates (installedApps, recentApps)                │
│  - UI callbacks                                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ viewModelScope.launch
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    BACKGROUND THREADS                       │
│  - Query PackageManager                                     │
│  - Load app labels and icons                                │
│  - Read/write DataStore                                     │
│  - Sort and filter operations                               │
└─────────────────────────────────────────────────────────────┘
```

### Dispatcher Selection Guide

**Use Dispatchers.Main when**:
- Updating Compose state
- Showing UI feedback
- Very fast operations (< 16ms)

**Use Dispatchers.IO when**:
- Querying PackageManager
- Reading/writing files
- Database operations
- Network requests

**Use Dispatchers.Default when**:
- Heavy computations
- Sorting large lists
- Image processing
- Data parsing

### Why withContext?

```kotlin
viewModelScope.launch {
    // Running on Main thread
    
    withContext(Dispatchers.IO) {
        // Running on IO thread
    }
    
    // Back on Main thread
}
```

**Benefits**:
- Keeps code readable (sequential-looking)
- Automatically handles thread switching
- Exception propagation works correctly
- Easy to switch dispatchers

---

## Performance Optimizations

### 1. Controlled Parallelism

```kotlin
private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
```

**Problem**: Loading 200 apps with `async` creates 200 coroutines.

**Solution**: Process in chunks of 8.

**Memory Impact**:
- Without limit: ~200 concurrent operations
- With limit: Max 8 concurrent operations
- Saves ~192x memory during loading

### 2. Pre-computed Lowercase

```kotlin
val nameLower: String by lazy { name.lowercase() }
```

**Problem**: Searching "y" on 200 apps calls `lowercase()` 200 times.

**Solution**: Compute once, cache forever.

**Performance**:
- First search: 200 lowercase operations
- Subsequent searches: 0 lowercase operations
- Significant on slower devices

### 3. Lazy Properties

```kotlin
val nameLower: String by lazy { ... }
```

**Benefits**:
- Computed only when first accessed
- Cached for future use
- Thread-safe
- No cost for unused properties

### 4. State List Optimization

```kotlin
val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
```

**Why SnapshotStateList?**:
- Integrates with Compose's recomposition
- Smart diffing (only updates changed items)
- Efficient memory usage
- Thread-safe

### 5. Single DataStore Read

```kotlin
.first().split(",")...
```

Instead of observing continuous changes, we read once.

**Why**: Recent apps don't change while dialog is open.

---

## How It All Works Together

### Complete Data Flow

**App Launch Process**:

```
User taps app in list
    ↓
MainActivity.onLaunchApp() called
    ↓
startActivity(intent) opens app
    ↓
viewModel.saveRecentApp(packageName)
    ↓
Write to DataStore (background thread)
    ↓
Reload recent apps from DataStore
    ↓
Update recentApps list (main thread)
    ↓
Compose detects state change
    ↓
UI recomposes with updated list
```

**Initial Load Process**:

```
MainActivity creates ViewModel
    ↓
ViewModel init block runs
    ↓
loadInstalledApps() launched
    ↓
Query PackageManager (IO thread)
    ↓
Process 150 apps in chunks of 8
    ↓
Sort alphabetically
    ↓
Update installedApps (main thread)
    ↓
Compose displays app list
    ↓
loadRecentApps() runs concurrently
    ↓
Read from DataStore (IO thread)
    ↓
Update recentApps (main thread)
    ↓
Recent apps section populates
```

---

## Exercises for Learning

1. **Add App Count**: Display the total number of installed apps in the UI

2. **Alphabetical Sections**: Group apps by first letter (A, B, C sections)

3. **App Categories**: Categorize apps (Games, Social, Tools) based on package name patterns

4. **Search History**: Save and display previous searches

5. **Favorite Apps**: Allow users to "star" apps that always appear at the top

6. **App Usage Stats**: Track how often each app is launched

---

## Troubleshooting

### Apps Not Loading

**Check**:
- Verify `PackageManager` query is correct
- Check logcat for security exceptions
- Ensure `QUERY_ALL_PACKAGES` permission if targeting API 30+

### Recent Apps Not Saving

**Check**:
- Verify DataStore dependency is added
- Check that `saveRecentApp()` is being called
- Look for DataStore exceptions in logcat

### Slow App Loading

**Check**:
- Verify chunk size (8) is appropriate for device
- Check if many apps are installed (>200)
- Consider adding a progress indicator

### Memory Issues

**Check**:
- Verify `limitedParallelism` is being used
- Check if icons are being cached properly
- Monitor memory usage in Android Studio profiler

---

## Glossary

- **Coroutine**: Kotlin's way of writing async code
- **DataStore**: Modern data persistence library
- **Dispatcher**: Determines which thread a coroutine runs on
- **Flow**: Stream of values that can be observed
- **Intent**: Message to request an action from another component
- **Lazy**: Delegate that delays computation
- **PackageManager**: System service for app information
- **ResolveInfo**: Information about an app that matches an Intent
- **SnapshotStateList**: Observable list for Compose
- **ViewModelScope**: Coroutine scope tied to ViewModel lifecycle
- **withContext**: Switches coroutine to different dispatcher

---

This documentation should give you a complete understanding of how LauncherViewModel.kt manages data in the Milki Launcher. The key takeaways are:

1. Use `AndroidViewModel` when you need Application context
2. Use `viewModelScope` for coroutines that should survive rotation
3. Use `withContext` to switch between threads
4. Use `mutableStateListOf` for observable lists in Compose
5. Use DataStore for persistent key-value storage
6. Control parallelism to prevent memory spikes
7. Pre-compute expensive operations
