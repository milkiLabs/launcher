# Refactoring MainActivity to Follow SOLID Principles

This guide explains how to split the monolithic `MainActivity.kt` into multiple files following SOLID principles. This refactoring improves maintainability, testability, and makes the codebase easier to understand for beginners.

## Table of Contents

1. [Current Architecture Problems](#current-architecture-problems)
2. [SOLID Principles Overview](#solid-principles-overview)
3. [Target Architecture](#target-architecture)
4. [Step-by-Step Refactoring](#step-by-step-refactoring)
5. [File Structure After Refactoring](#file-structure-after-refactoring)
6. [Benefits of This Approach](#benefits-of-this-approach)

---

## Current Architecture Problems

The current `MainActivity.kt` (524 lines) violates several SOLID principles:

### 1. Single Responsibility Principle (SRP) Violation

**Problem:** MainActivity has multiple responsibilities:
- Activity lifecycle management
- UI state management (showSearch, searchQuery)
- Contains 3 different UI composables (LauncherScreen, AppSearchDialog, AppListItem)
- Handles app filtering logic
- Manages navigation/intent handling

**Impact:** Changes to one aspect (e.g., UI design) require modifying the same file as activity logic changes. This increases the risk of bugs and makes testing difficult.

### 2. Open/Closed Principle Violation

**Problem:** To add a new feature (e.g., a settings screen), we must:
- Open MainActivity.kt
- Add new state variables
- Add new composable functions
- Modify existing code

**Impact:** Existing working code must be modified to add features, risking regressions.

### 3. Dependency Inversion Violation

**Problem:** 
- `AppSearchDialog` directly depends on concrete `AppInfo` class
- `AppListItem` directly uses Coil's `rememberAsyncImagePainter`
- No abstraction layers between UI and data

**Impact:** UI components are tightly coupled to implementation details, making them hard to reuse or test in isolation.

---

## SOLID Principles Overview

| Principle | Definition | Goal |
|-----------|------------|------|
| **S**ingle Responsibility | A class/module should have one reason to change | Each file does one thing well |
| **O**pen/Closed | Open for extension, closed for modification | Add features without changing existing code |
| **L**iskov Substitution | Subtypes must be substitutable for base types | Use interfaces/abstractions |
| **I**nterface Segregation | Many small interfaces > one large interface | Clients don't depend on methods they don't use |
| **D**ependency Inversion | Depend on abstractions, not concretions | Use interfaces, dependency injection |

---

## Target Architecture

After refactoring, the architecture will be:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  MainActivity.kt (50 lines)                                            │ │
│  │  - Activity lifecycle only                                             │ │
│  │  - Delegates all UI to LauncherScreen                                  │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌─────────────────────────────────┴──────────────────────────────────────┐ │
│  │  ui/screens/LauncherScreen.kt                                          │ │
│  │  - Main home screen UI                                                 │ │
│  │  - Coordinates between components                                      │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌─────────────────────────────────┴──────────────────────────────────────┐ │
│  │  ui/components/AppSearchDialog.kt                                      │ │
│  │  - Search dialog component                                             │ │
│  │  - Self-contained with its own state logic                             │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌─────────────────────────────────┴──────────────────────────────────────┐ │
│  │  ui/components/AppListItem.kt                                          │ │
│  │  - Individual app row component                                        │ │
│  │  - Reusable across different screens                                   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                           DOMAIN LAYER (Business Logic)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────┐  ┌──────────────────────────────────────┐ │
│  │  domain/model/AppInfo.kt     │  │  domain/repository/AppRepository.kt  │ │
│  │  - Data class definition     │  │  - Interface defining data contract  │ │
│  └──────────────────────────────┘  └──────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                           DATA LAYER (Implementation)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  data/repository/AppRepositoryImpl.kt                                  │ │
│  │  - Implements AppRepository interface                                  │ │
│  │  - Uses PackageManager and DataStore                                   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                           VIEWMODEL LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  LauncherViewModel.kt (refactored)                                     │ │
│  │  - Depends on AppRepository interface                                  │ │
│  │  - No direct PackageManager/DataStore usage                            │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Refactoring

### Step 1: Create Domain Layer (Model & Repository Interface)

#### 1.1 Create `domain/model/AppInfo.kt`

Move the AppInfo data class from ViewModel to a dedicated domain model file.

```kotlin
package com.milki.launcher.domain.model

import android.content.Intent

/**
 * AppInfo represents a single installed application.
 * 
 * This is a domain model - it represents business data independent of 
 * any framework (Android, Compose, etc.). It can be used anywhere in the app.
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
     */
    val nameLower: String by lazy { name.lowercase() }
    
    /**
     * Cached lowercase version of the package name.
     * Used for case-insensitive search matching by package.
     */
    val packageLower: String by lazy { packageName.lowercase() }
}
```

**Why this follows SOLID:**
- **SRP:** This file only defines the data structure
- **OCP:** New fields can be added without changing existing code
- **DIP:** Domain model doesn't depend on Android framework details

---

#### 1.2 Create `domain/repository/AppRepository.kt`

Define an interface that abstracts data operations.

```kotlin
package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * AppRepository defines the contract for app data operations.
 * 
 * This interface follows the Repository pattern from Domain-Driven Design.
 * It abstracts WHERE data comes from (PackageManager, database, network)
 * so the ViewModel doesn't need to know.
 * 
 * Benefits:
 * - ViewModel can be tested with a mock implementation
 * - Data source can be changed (e.g., add caching) without changing ViewModel
 * - Single place to modify if data fetching logic changes
 * 
 * This is the "D" in SOLID - Dependency Inversion.
 * High-level modules (ViewModel) depend on this abstraction,
 * not on low-level details (PackageManager, DataStore).
 */
interface AppRepository {
    
    /**
     * Get all installed apps that have launcher icons.
     * 
     * This is a suspend function because it may take time to query the system.
     * The apps are already sorted alphabetically.
     * 
     * @return List of AppInfo objects representing installed apps
     */
    suspend fun getInstalledApps(): List<AppInfo>
    
    /**
     * Get recently launched apps.
     * 
     * Returns a Flow so the UI can observe changes automatically.
     * When recent apps change, the Flow emits a new list.
     * 
     * @return Flow of recent apps list (updates when data changes)
     */
    fun getRecentApps(): Flow<List<AppInfo>>
    
    /**
     * Save an app to the recent apps list.
     * 
     * This adds the app to the front of the list and removes duplicates.
     * The list is limited to 5 apps maximum.
     * 
     * @param packageName The package name of the app to save
     */
    suspend fun saveRecentApp(packageName: String)
}
```

**Why this follows SOLID:**
- **ISP:** Clean, focused interface with only 3 methods
- **DIP:** ViewModel will depend on this interface, not concrete implementation
- **SRP:** Defines the contract, doesn't implement it

---

### Step 2: Create Data Layer (Repository Implementation)

#### 2.1 Create `data/repository/AppRepositoryImpl.kt`

Move the actual data operations from ViewModel to this implementation class.

```kotlin
package com.milki.launcher.data.repository

import android.app.Application
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementation of AppRepository that uses Android's PackageManager and DataStore.
 * 
 * This is the low-level implementation that knows about:
 * - How to query PackageManager
 * - How to use DataStore
 * - How to manage coroutines and threading
 * 
 * The ViewModel doesn't know about any of this - it just calls the interface methods.
 */
class AppRepositoryImpl(
    private val application: Application
) : AppRepository {
    
    /**
     * DataStore instance for persisting recent apps.
     * This is specific to the implementation, not exposed in the interface.
     */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")
    
    /**
     * Key used to store recent apps in DataStore.
     */
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    /**
     * Dispatcher that limits parallel operations to 8 at a time.
     * Prevents memory spikes when loading many apps.
     */
    private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
    
    /**
     * Load all installed apps from PackageManager.
     * 
     * Implementation details:
     * 1. Query PackageManager for MAIN/LAUNCHER activities
     * 2. Process in chunks of 8 for memory efficiency
     * 3. Sort alphabetically by name
     */
    override suspend fun getInstalledApps(): List<AppInfo> {
        return withContext(limitedDispatcher) {
            val pm = application.packageManager
            
            // Create intent to find all launcher apps
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            // Query the system
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            
            // Process in chunks for memory efficiency
            resolveInfos.chunked(8).flatMap { chunk ->
                chunk.map { resolveInfo ->
                    async {
                        AppInfo(
                            name = resolveInfo.loadLabel(pm).toString(),
                            packageName = resolveInfo.activityInfo.packageName,
                            launchIntent = pm.getLaunchIntentForPackage(
                                resolveInfo.activityInfo.packageName
                            )
                        )
                    }
                }.awaitAll()
            }.sortedBy { it.nameLower }
        }
    }
    
    /**
     * Get recent apps as a Flow.
     * 
     * Flow allows the UI to observe changes automatically.
     * Whenever recent apps are updated, the Flow emits a new list.
     */
    override fun getRecentApps(): Flow<List<AppInfo>> {
        return application.dataStore.data.map { preferences ->
            val recentPackages = preferences[recentAppsKey]?.split(",")?.filter { it.isNotEmpty() } 
                ?: emptyList()
            
            val pm = application.packageManager
            
            // Convert package names to AppInfo objects
            recentPackages.mapNotNull { packageName ->
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    AppInfo(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = packageName,
                        launchIntent = pm.getLaunchIntentForPackage(packageName)
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    // App was uninstalled, skip it
                    null
                }
            }
        }
    }
    
    /**
     * Save an app to recent apps list.
     * 
     * Implementation details:
     * 1. Read current list from DataStore
     * 2. Remove if already exists (to move to front)
     * 3. Add to front
     * 4. Limit to 5 apps
     * 5. Save back to DataStore
     */
    override suspend fun saveRecentApp(packageName: String) {
        application.dataStore.edit { preferences ->
            val current = preferences[recentAppsKey] ?: ""
            
            val recentPackages = current.split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()
            
            // Remove if exists, then add to front
            recentPackages.remove(packageName)
            recentPackages.add(0, packageName)
            
            // Save limited to 5 apps
            preferences[recentAppsKey] = recentPackages.take(5).joinToString(",")
        }
    }
}
```

**Why this follows SOLID:**
- **SRP:** Only handles data access, nothing else
- **OCP:** Can add caching or other features without changing ViewModel
- **DIP:** Implements the interface abstraction

---

### Step 3: Refactor ViewModel to Use Repository

#### 3.1 Update `LauncherViewModel.kt`

```kotlin
package com.milki.launcher

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * LauncherViewModel coordinates between the UI and data layers.
 * 
 * After refactoring:
 * - No longer knows about PackageManager or DataStore (moved to Repository)
 * - Depends on AppRepository interface (abstraction)
 * - Focuses on UI state management only
 * 
 * This is much cleaner and follows SRP better.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    
    /**
     * Repository instance - normally injected via Dependency Injection.
     * For simplicity, we create it here, but in production use Hilt or Koin.
     */
    private val repository: AppRepository = AppRepositoryImpl(application)
    
    /**
     * Observable list of installed apps.
     * UI automatically updates when this changes.
     */
    val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    /**
     * Observable list of recent apps.
     */
    val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    init {
        loadInstalledApps()
        observeRecentApps()
    }
    
    /**
     * Load installed apps from repository.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = repository.getInstalledApps()
            installedApps.clear()
            installedApps.addAll(apps)
        }
    }
    
    /**
     * Observe recent apps from repository.
     * Uses Flow to automatically update when data changes.
     */
    private fun observeRecentApps() {
        viewModelScope.launch {
            repository.getRecentApps().collect { apps ->
                recentApps.clear()
                recentApps.addAll(apps)
            }
        }
    }
    
    /**
     * Save app to recent apps via repository.
     */
    fun saveRecentApp(packageName: String) {
        viewModelScope.launch {
            repository.saveRecentApp(packageName)
            // Note: No need to manually reload - Flow handles updates automatically!
        }
    }
}
```

**Why this follows SOLID:**
- **SRP:** Only manages UI state, delegates data operations
- **DIP:** Depends on AppRepository interface, not implementation
- **OCP:** Can swap repository implementation without changing ViewModel

---

### Step 4: Create UI Components

#### 4.1 Create `ui/components/AppListItem.kt`

```kotlin
package com.milki.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.milki.launcher.AppIconRequest
import com.milki.launcher.domain.model.AppInfo

/**
 * AppListItem displays a single row in the app list.
 * 
 * This is a reusable component that can be used anywhere in the app
 * where you need to display an app. It's completely self-contained
 * and doesn't know about the surrounding screen or dialog.
 * 
 * @param appInfo The app to display
 * @param onClick Called when user taps this item
 * @param modifier Optional modifier for customization
 */
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon loaded using Coil with custom fetcher
            val painter = rememberAsyncImagePainter(
                model = AppIconRequest(appInfo.packageName)
            )
            
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // App name
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
```

**Why this follows SOLID:**
- **SRP:** Only displays one app list item
- **ISP:** Simple interface with just 2 required parameters
- **OCP:** Can be styled or extended without changing existing code

---

#### 4.2 Create `ui/components/AppSearchDialog.kt`

```kotlin
package com.milki.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milki.launcher.domain.model.AppInfo
import kotlinx.coroutines.delay

/**
 * AppSearchDialog displays a full-featured search dialog with:
 * - Search text field with auto-focus
 * - Smart filtering (exact, startsWith, contains)
 * - Recent apps when search is empty
 * - Empty state handling
 * - Keyboard actions (Done launches first app)
 * 
 * This component is self-contained and reusable. It manages its own
 * internal state (filtering logic) but receives data and callbacks
 * from the parent.
 */
@Composable
fun AppSearchDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    // Focus requester for auto-opening keyboard
    val focusRequester = remember { FocusRequester() }
    
    // Filter apps based on search query using smart matching
    val filteredApps = remember(searchQuery, installedApps, recentApps) {
        filterApps(searchQuery, installedApps, recentApps)
    }
    
    // Handle back button to close dialog
    BackHandler { onDismiss() }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search text field
                SearchTextField(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    onLaunchFirstApp = { 
                        filteredApps.firstOrNull()?.let { onLaunchApp(it) }
                    }
                )
                
                // App list or empty state
                if (filteredApps.isEmpty()) {
                    EmptyState(searchQuery = searchQuery)
                } else {
                    AppList(
                        apps = filteredApps,
                        onLaunchApp = onLaunchApp
                    )
                }
            }
        }
    }
    
    // Auto-focus text field when dialog opens
    LaunchedEffect(Unit) {
        delay(10)
        focusRequester.requestFocus()
    }
}

/**
 * Private composable for the search text field.
 * Extracted for cleaner code organization.
 */
@Composable
private fun SearchTextField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onLaunchFirstApp: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .focusRequester(focusRequester),
        placeholder = { Text("Search apps...") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onLaunchFirstApp() }),
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    )
}

/**
 * Private composable for the app list.
 */
@Composable
private fun AppList(
    apps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = apps,
            key = { it.packageName },
            contentType = { "app_item" }
        ) { app ->
            AppListItem(
                appInfo = app,
                onClick = { onLaunchApp(app) }
            )
        }
    }
}

/**
 * Private composable for empty state.
 */
@Composable
private fun EmptyState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (searchQuery.isBlank()) "No recent apps" else "No apps found",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Filter apps using smart matching algorithm.
 * 
 * Priority order:
 * 1. Exact matches (highest priority)
 * 2. Starts with matches (medium priority)
 * 3. Contains matches (lowest priority)
 * 
 * When search is empty, returns recent apps.
 */
private fun filterApps(
    searchQuery: String,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>
): List<AppInfo> {
    if (searchQuery.isBlank()) {
        return recentApps
    }
    
    val queryLower = searchQuery.trim().lowercase()
    val exactMatches = mutableListOf<AppInfo>()
    val startsWithMatches = mutableListOf<AppInfo>()
    val containsMatches = mutableListOf<AppInfo>()
    
    installedApps.forEach { app ->
        when {
            app.nameLower == queryLower || app.packageLower == queryLower -> {
                exactMatches.add(app)
            }
            app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                startsWithMatches.add(app)
            }
            app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
                containsMatches.add(app)
            }
        }
    }
    
    return exactMatches + startsWithMatches + containsMatches
}
```

**Why this follows SOLID:**
- **SRP:** Only handles search dialog functionality
- **OCP:** Extracted helper composables can be extended independently
- **ISP:** Clear, focused parameters

---

#### 4.3 Create `ui/screens/LauncherScreen.kt`

```kotlin
package com.milki.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.ui.components.AppSearchDialog

/**
 * LauncherScreen is the main home screen of the launcher.
 * 
 * It displays:
 * - A black background (when search is closed)
 * - "Tap to search" hint text
 * - The search dialog (when showSearch is true)
 * 
 * This screen coordinates between the home state and search dialog
 * but delegates all implementation details to child components.
 * 
 * @param showSearch Whether the search dialog is visible
 * @param searchQuery Current search text
 * @param onShowSearch Called when user taps the home screen
 * @param onHideSearch Called when search should close
 * @param onSearchQueryChange Called when search text changes
 * @param installedApps List of all installed apps
 * @param recentApps List of recently launched apps
 * @param onLaunchApp Called when user selects an app to launch
 */
@Composable
fun LauncherScreen(
    showSearch: Boolean,
    searchQuery: String,
    onShowSearch: () -> Unit,
    onHideSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit
) {
    // Background with tap-to-search
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onShowSearch() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap to search",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
    
    // Search dialog (conditionally shown)
    if (showSearch) {
        AppSearchDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            installedApps = installedApps,
            recentApps = recentApps,
            onDismiss = onHideSearch,
            onLaunchApp = onLaunchApp
        )
    }
}
```

**Why this follows SOLID:**
- **SRP:** Only handles the launcher screen layout
- **DIP:** Uses AppSearchDialog abstraction, not implementation details
- **OCP:** Can add new screens without changing existing code

---

### Step 5: Simplify MainActivity

#### 5.1 Update `MainActivity.kt`

```kotlin
package com.milki.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.ui.screens.LauncherScreen
import com.milki.launcher.ui.theme.LauncherTheme

/**
 * MainActivity - The entry point of the Milki Launcher.
 * 
 * After refactoring, this class is drastically simplified:
 * - Only manages Activity lifecycle
 * - Delegates all UI to LauncherScreen
 * - No longer contains composable functions
 * - Much easier to understand and maintain
 * 
 * This follows SRP perfectly - it has ONE responsibility:
 * Coordinating the Activity lifecycle and delegating to UI.
 */
class MainActivity : ComponentActivity() {
    
    // State managed at Activity level to survive configuration changes
    private var showSearch by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: LauncherViewModel = viewModel()
            
            LauncherTheme {
                LauncherScreen(
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onShowSearch = { showSearch = true },
                    onHideSearch = { 
                        showSearch = false
                        searchQuery = ""
                    },
                    onSearchQueryChange = { searchQuery = it },
                    installedApps = viewModel.installedApps,
                    recentApps = viewModel.recentApps,
                    onLaunchApp = { appInfo -> launchApp(appInfo, viewModel) }
                )
            }
        }
    }
    
    /**
     * Handle home button presses while Activity is running.
     * Toggle search dialog or clear search text.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (intent.action == Intent.ACTION_MAIN) {
            when {
                !showSearch -> showSearch = true
                searchQuery.isNotEmpty() -> searchQuery = ""
                else -> showSearch = false
            }
        }
    }
    
    /**
     * Launch an app and update recent apps.
     * Extracted to a private method for clarity.
     */
    private fun launchApp(appInfo: AppInfo, viewModel: LauncherViewModel) {
        searchQuery = ""
        showSearch = false
        appInfo.launchIntent?.let { startActivity(it) }
        viewModel.saveRecentApp(appInfo.packageName)
    }
}
```

**Why this follows SOLID:**
- **SRP:** Only Activity lifecycle management
- **OCP:** Can add new features without modifying this file
- **DIP:** Depends on LauncherScreen abstraction

---

## File Structure After Refactoring

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt                              # 50 lines (was 524)
├── LauncherViewModel.kt                         # 70 lines (was 511)
├── LauncherApplication.kt                       # Unchanged
├── AppIconFetcher.kt                           # Unchanged
│
├── domain/                                     # NEW: Business logic
│   ├── model/
│   │   └── AppInfo.kt                          # Data class
│   └── repository/
│       └── AppRepository.kt                    # Interface
│
├── data/                                       # NEW: Implementation
│   └── repository/
│       └── AppRepositoryImpl.kt                # Implementation
│
└── ui/                                         # NEW: UI layer
    ├── screens/
    │   └── LauncherScreen.kt                   # Main screen
    └── components/
        ├── AppSearchDialog.kt                  # Search dialog
        └── AppListItem.kt                      # App list item
```

---

## Benefits of This Approach

### 1. Maintainability
- Each file is small and focused (50-200 lines vs 500+)
- Changes are isolated to specific components
- New developers can understand the codebase faster

### 2. Testability
- **ViewModel** can be tested with mock `AppRepository`
- **UI Components** can be previewed and tested in isolation
- **Repository** can be tested without Android framework

### 3. Reusability
- `AppListItem` can be used in any screen
- `AppSearchDialog` could be reused for different data types
- `AppRepository` interface allows swapping implementations

### 4. Scalability
- Adding a settings screen? Create `ui/screens/SettingsScreen.kt`
- Adding a favorites feature? Extend `AppRepository` interface
- Adding caching? Modify `AppRepositoryImpl` only

### 5. Team Collaboration
- Multiple developers can work on different files simultaneously
- Clear interfaces prevent merge conflicts
- Well-defined boundaries reduce communication overhead

### 6. SOLID Compliance

| Principle | Before | After |
|-----------|--------|-------|
| **SRP** | MainActivity had 5+ responsibilities | Each file has 1 responsibility |
| **OCP** | Had to modify MainActivity to add features | Can extend without modifying existing files |
| **LSP** | Not applicable | Repository interface allows substitutions |
| **ISP** | Not applicable | Small, focused interfaces |
| **DIP** | ViewModel depended on Android classes | Depends on abstractions |

---

## Summary

By splitting MainActivity into multiple files following SOLID principles:

1. **Domain Layer** - Defines business entities and contracts (interfaces)
2. **Data Layer** - Implements data access (can be swapped/tested)
3. **ViewModel Layer** - Manages UI state (cleaner, testable)
4. **UI Layer** - Reusable components and screens

The codebase becomes:
- Easier to understand (small, focused files)
- Easier to test (isolated components)
- Easier to extend (plugin architecture)
- Easier to maintain (clear boundaries)

This architecture follows industry best practices and prepares the codebase for future growth while remaining educational for beginners learning Android development.
