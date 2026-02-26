# FileOpener and AppLauncher Utilities

## Overview

This document describes the shared utilities for opening files and launching apps.

## FileOpener

See [FileOpener.kt](../app/src/main/java/com/milki/launcher/util/FileOpener.kt)

## AppLauncher

The `AppLauncher` utility (`util/AppLauncher.kt`) provides centralized app launching logic.

### Purpose

Previously, app launching logic was duplicated in:
- `ActionExecutor.launchApp()` - For launching apps from search results
- `LauncherScreen.openPinnedApp()` - For launching pinned apps on home screen

### Functions

#### `launchApp(context, appInfo, onRecentAppSaved)`

Launches an app using AppInfo (from search results). Uses pre-built launchIntent or falls back to PackageManager.

#### `launchPinnedApp(context, pinnedApp)`

Launches a pinned app using its package name.

#### `launchAppShortcut(context, appShortcut)`

Launches an app shortcut (currently opens parent app).

### Usage

```kotlin
import com.milki.launcher.util.launchApp
import com.milki.launcher.util.launchPinnedApp

// Launch from search
launchApp(context, appInfo) { componentName ->
    saveRecentApp(componentName)
}

// Launch from home screen
if (!launchPinnedApp(context, pinnedApp)) {
    Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
}
```
