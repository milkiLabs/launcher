# SOLID Refactoring - Architecture Documentation

## Overview

The Milki Launcher has been refactored to follow SOLID principles and Clean Architecture. This document explains the new architecture, file organization, and how each component works together.

## Architecture Layers

The codebase is now organized into four distinct layers, following Clean Architecture principles:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                                 │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  MainActivity.kt (85 lines)                                            │ │
│  │  - Activity lifecycle only                                             │ │
│  │  - Minimal state management                                            │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌─────────────────────────────────┴──────────────────────────────────────┐ │
│  │  ui/screens/LauncherScreen.kt                                          │ │
│  │  - Main screen UI coordination                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌─────────────────────────────────┴──────────────────────────────────────┐ │
│  │  ui/components/AppSearchDialog.kt                                      │ │
│  │  ui/components/AppListItem.kt                                          │ │
│  │  - Reusable UI components                                              │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                           VIEWMODEL LAYER                                    │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  LauncherViewModel.kt (90 lines)                                       │ │
│  │  - UI state management                                                 │ │
│  │  - Coordinates with Repository                                         │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                           DOMAIN LAYER                                       │
│  ┌───────────────────────┐  ┌──────────────────────────────────────┐        │
│  │  domain/model/        │  │  domain/repository/                  │        │
│  │  - AppInfo.kt         │  │  - AppRepository.kt (interface)      │        │
│  └───────────────────────┘  └──────────────────────────────────────┘        │
├─────────────────────────────────────────────────────────────────────────────┤
│                           DATA LAYER                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  data/repository/AppRepositoryImpl.kt                                  │ │
│  │  - PackageManager queries                                              │ │
│  │  - DataStore persistence                                               │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Dependency Rule:** Dependencies point inward. The outer layers depend on inner layers, never the reverse.

---

## File Structure

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt                              # 85 lines (was 524)
├── LauncherViewModel.kt                         # 90 lines (was 511)
├── LauncherApplication.kt                       # Unchanged
├── AppIconFetcher.kt                           # Unchanged
│
├── domain/                                     # Business logic
│   ├── model/
│   │   └── AppInfo.kt                          # Domain data class
│   └── repository/
│       └── AppRepository.kt                    # Repository interface
│
├── data/                                       # Implementation
│   └── repository/
│       └── AppRepositoryImpl.kt                # Repository implementation
│
└── ui/                                         # UI layer
    ├── screens/
    │   └── LauncherScreen.kt                   # Main screen
    └── components/
        ├── AppSearchDialog.kt                  # Search dialog
        └── AppListItem.kt                      # App list item
```

---

## Detailed Component Documentation

### 1. Domain Layer

#### `domain/model/AppInfo.kt`

**Purpose:** Pure data model representing an installed app.

**Why it's in Domain:**
- Framework-agnostic (no Android dependencies except Intent)
- Used throughout the app
- Can be tested without Android runtime

**Key Features:**
- `nameLower` and `packageLower` use lazy caching for search performance
- Extension function `matchesQuery()` for search logic

**Example Usage:**
```kotlin
val app = AppInfo(
    name = "YouTube",
    packageName = "com.google.android.youtube",
    launchIntent = intent
)

if (app.matchesQuery("tube")) {
    // App matches search
}
```

---

#### `domain/repository/AppRepository.kt`

**Purpose:** Define the contract for app data operations.

**Why it's an Interface:**
- Abstraction allows swapping implementations
- ViewModel depends on interface, not concrete class
- Enables testing with mock implementations

**Methods:**
- `getInstalledApps()`: Load all apps (suspend)
- `getRecentApps()`: Observe recent apps (Flow)
- `saveRecentApp()`: Save a recent app (suspend)

**Example Usage:**
```kotlin
class MyViewModel(private val repository: AppRepository) {
    suspend fun load() {
        val apps = repository.getInstalledApps()
    }
}
```

---

### 2. Data Layer

#### `data/repository/AppRepositoryImpl.kt`

**Purpose:** Implement the repository interface using Android APIs.

**Responsibilities:**
- Query PackageManager for installed apps
- Read/write DataStore for recent apps
- Manage threading with custom dispatcher
- Handle errors (e.g., uninstalled apps)

**Key Features:**
- Processes apps in chunks of 8 for memory efficiency
- Uses Flow for automatic UI updates
- Transactional DataStore updates

**Implementation Details:**
```kotlin
// Limited parallelism dispatcher prevents memory spikes
private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)

// Flow automatically emits when DataStore changes
override fun getRecentApps(): Flow<List<AppInfo>> {
    return dataStore.data.map { preferences ->
        // Convert stored package names to AppInfo objects
    }
}
```

---

### 3. ViewModel Layer

#### `LauncherViewModel.kt`

**Purpose:** Manage UI state and coordinate data operations.

**Changes After Refactoring:**
- Before: 511 lines with direct PackageManager/DataStore usage
- After: 90 lines, delegates to Repository

**Responsibilities:**
1. Expose observable state (`installedApps`, `recentApps`)
2. Load data when ViewModel is created
3. Handle user actions (`saveRecentApp`)

**State Management:**
```kotlin
// Observable lists that trigger UI updates
val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
```

**Flow Observation:**
```kotlin
// Automatically updates UI when repository data changes
repository.getRecentApps().collectLatest { apps ->
    recentApps.clear()
    recentApps.addAll(apps)
}
```

---

### 4. Presentation Layer

#### `ui/components/AppListItem.kt`

**Purpose:** Display a single app row with icon and name.

**Characteristics:**
- Completely reusable
- No state management
- Pure function of inputs

**Parameters:**
- `appInfo`: The app to display
- `onClick`: Callback when tapped
- `modifier`: Optional styling

**Example:**
```kotlin
AppListItem(
    appInfo = app,
    onClick = { launchApp(app) }
)
```

---

#### `ui/components/AppSearchDialog.kt`

**Purpose:** Full-featured search dialog with filtering.

**Features:**
- Search text field with auto-focus
- Smart filtering (exact > startsWith > contains)
- Shows recent apps when empty
- Keyboard actions (Done launches first app)
- Back button handling

**Internal Structure:**
- Private composables: `SearchTextField`, `AppList`, `EmptyState`
- Pure function `filterApps()` for search logic

**Smart Filtering:**
```kotlin
// Three-tier matching system
val exactMatches = mutableListOf<AppInfo>()
val startsWithMatches = mutableListOf<AppInfo>()
val containsMatches = mutableListOf<AppInfo>()

// Combine in priority order
return exactMatches + startsWithMatches + containsMatches
```

---

#### `ui/screens/LauncherScreen.kt`

**Purpose:** Main home screen composition.

**Layout:**
1. Full-screen black background (tappable)
2. "Tap to search" hint text
3. Search dialog (conditional)

**State Hoisting:**
All state is passed from parent (MainActivity) via parameters. This makes the screen reusable and testable.

---

#### `MainActivity.kt`

**Purpose:** Activity lifecycle and coordination.

**After Refactoring:**
- Only 85 lines (was 524)
- No UI composable definitions
- Minimal state management

**Responsibilities:**
1. Set up Compose UI in `onCreate()`
2. Manage Activity-level state (`showSearch`, `searchQuery`)
3. Handle home button presses in `onNewIntent()`
4. Launch apps via `launchApp()`

**State Hoisting:**
```kotlin
// State lives in Activity, passed to children
var showSearch by mutableStateOf(false)
var searchQuery by mutableStateOf("")

LauncherScreen(
    showSearch = showSearch,
    onShowSearch = { showSearch = true },
    // ...
)
```

---

## Data Flow

### Loading Installed Apps

```
MainActivity.onCreate()
    ↓
viewModel() creates/gets ViewModel
    ↓
LauncherViewModel.init
    ↓
loadInstalledApps()
    ↓
repository.getInstalledApps()
    ↓
PackageManager.queryIntentActivities()
    ↓
Convert to AppInfo objects
    ↓
Update installedApps state
    ↓
Compose recomposes with new data
```

### Saving Recent Apps

```
User taps app
    ↓
MainActivity.launchApp()
    ↓
viewModel.saveRecentApp(packageName)
    ↓
repository.saveRecentApp(packageName)
    ↓
DataStore.edit() updates preferences
    ↓
Flow emits new recent apps list
    ↓
ViewModel.collectLatest receives update
    ↓
Update recentApps state
    ↓
Compose recomposes automatically
```

---

## SOLID Compliance

### Single Responsibility Principle (SRP)

**Before:**
- MainActivity: Activity lifecycle + UI + filtering + navigation
- LauncherViewModel: Business logic + PackageManager + DataStore

**After:**
- MainActivity: Activity lifecycle only
- LauncherViewModel: UI state coordination only
- AppRepositoryImpl: Data access only
- Each UI component: One specific UI element

### Open/Closed Principle (OCP)

**Before:**
- Adding a new feature required modifying MainActivity
- Changes risked breaking existing functionality

**After:**
- Add new screens without modifying existing files
- Add caching by modifying only RepositoryImpl
- UI components can be extended independently

### Liskov Substitution Principle (LSP)

- `AppRepositoryImpl` can be substituted for `AppRepository` interface
- Could create `MockAppRepository` for testing without changing ViewModel

### Interface Segregation Principle (ISP)

- `AppRepository` has only 3 focused methods
- UI components have minimal required parameters
- No "fat interfaces" that force unused methods

### Dependency Inversion Principle (DIP)

**Before:**
```kotlin
class LauncherViewModel(application: Application) {
    // Directly uses PackageManager and DataStore
    private val pm = application.packageManager
}
```

**After:**
```kotlin
class LauncherViewModel(application: Application) {
    // Depends on abstraction (interface)
    private val repository: AppRepository = AppRepositoryImpl(application)
}
```

---

## Benefits

### Maintainability
- Each file is small and focused (50-200 lines)
- Changes are isolated
- Easy to understand for new developers

### Testability
- ViewModel: Test with mock Repository
- Repository: Test with fake DataStore
- UI Components: Preview in isolation

### Reusability
- `AppListItem` can be used in any screen
- `AppSearchDialog` can be adapted for other data types
- Repository interface allows different implementations

### Scalability
- Add screens: Create new file in `ui/screens/`
- Add features: Extend Repository interface
- Add caching: Modify RepositoryImpl only

---

## Summary

The refactored codebase follows industry best practices:

1. **Clean Architecture:** Clear separation of concerns across layers
2. **SOLID Principles:** Each component has single responsibility
3. **MVVM Pattern:** ViewModel mediates between UI and data
4. **Repository Pattern:** Abstracts data source implementation
5. **Compose Best Practices:** Reusable components with state hoisting

The result is a codebase that's easier to understand, test, maintain, and extend while remaining educational for beginners learning Android development.
