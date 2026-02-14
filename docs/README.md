# Milki Launcher - Comprehensive Project Documentation

## Table of Contents

1. [Introduction](#introduction)
2. [Project Overview](#project-overview)
3. [Architecture](#architecture)
4. [Getting Started](#getting-started)
5. [Key Concepts Explained](#key-concepts-explained)
6. [File Structure](#file-structure)
7. [Detailed Component Documentation](#detailed-component-documentation)
8. [Dependencies Explained](#dependencies-explained)
9. [Performance Considerations](#performance-considerations)
10. [Common Issues and Solutions](#common-issues-and-solutions)

---

### Key Features

- **O(n) Search Algorithm**: Linear search through apps - fast enough for 200+ apps
- **Controlled Parallelism**: Processes apps in chunks of 8 to avoid memory spikes
- **LRU Memory Cache**: Icons cached in memory for instant display
- **Coroutine-based**: All heavy operations run on background threads

---

## Architecture

### MVVM Architecture Pattern

This project uses the **Model-View-ViewModel (MVVM)** architecture pattern, which is the recommended architecture for Android apps:

```
┌─────────────────────────────────────────────────────────────┐
│                         VIEW (UI Layer)                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  MainActivity.kt                                    │   │
│  │  - Displays UI using Jetpack Compose                │   │
│  │  - Observes state from ViewModel                    │   │
│  │  - Handles user interactions                        │   │
│  └─────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────┘
                            │ Observes state changes
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     VIEWMODEL                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  LauncherViewModel.kt                               │   │
│  │  - Holds UI state (lists of apps)                   │   │
│  │  - Loads data from system (PackageManager)          │   │
│  │  - Persists recent apps to DataStore                │   │
│  │  - Survives configuration changes                   │   │
│  └─────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────┘
                            │ Fetches data
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       MODEL                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  AppInfo data class                                 │   │
│  │  - Represents a single app                          │   │
│  │  - Contains name, package name, launch intent       │   │
│  │  - Pre-computes lowercase strings for search        │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Concepts Explained

- PackageManager is used it to:
  - Get a list of all installed apps
  - Get app icons
  - Get app names
  - Launch apps

- An Intent is a messaging object used to request an action from another app component. To launch an app, you create an Intent that tells Android "open this app":

```kotlin
val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
startActivity(intent)
```

---

### Main Files Explained

1. **MainActivity.kt**: The entry point of the app. Handles the UI and user interactions.
2. **LauncherViewModel.kt**: Manages the data layer - loads apps, saves recent apps.
3. **LauncherApplication.kt**: Custom Application class for app-wide configuration.
4. **AppIconFetcher.kt**: Custom Coil fetcher for loading app icons efficiently.

---

## Detailed Component Documentation

See the individual documentation files for detailed explanations:

- **[MainActivity.md](MainActivity.md)** - Complete guide to the main UI component
- **[LauncherViewModel.md](LauncherViewModel.md)** - Understanding the ViewModel and data layer
- **[LauncherApplication.md](LauncherApplication.md)** - Application class and Coil configuration
- **[AppIconFetcher.md](AppIconFetcher.md)** - Custom icon loading implementation
- **[Theme.md](Theme.md)** - Material Design 3 theming explained
- **[BuildConfiguration.md](BuildConfiguration.md)** - Understanding Gradle build files
- **[AndroidManifest.md](AndroidManifest.md)** - App configuration explained

---

## Performance Considerations

### 1. Controlled Parallelism

When loading 150+ apps, we process them in chunks of 8 instead of launching all coroutines at once:

```kotlin
resolveInfos.chunked(8).flatMap { chunk ->
    chunk.map { resolveInfo ->
        async { /* load app info */ }
    }.awaitAll()
}
```

This prevents memory spikes and keeps the app responsive.

### 2. Cached Lowercase Strings

The `AppInfo` data class pre-computes lowercase versions of app names and package names:

```kotlin
data class AppInfo(...) {
    val nameLower: String by lazy { name.lowercase() }
    val packageLower: String by lazy { packageName.lowercase() }
}
```

This avoids calling `lowercase()` on every search keystroke.

### 3. Memory Cache for Icons

Coil is configured to use 15% of available memory for caching app icons:

```kotlin
.memoryCache {
    MemoryCache.Builder(this)
        .maxSizePercent(0.15)
        .build()
}
```

### 4. No Disk Cache for Icons

Since app icons are loaded from the system (not network), we disable disk cache:

```kotlin
.diskCache(null)
```

### 5. Search Algorithm

The search uses a three-tier approach:

1. Exact matches first
2. Starts-with matches second
3. Contains matches last

This gives users the most relevant results first.
