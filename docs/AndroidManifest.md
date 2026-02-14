# AndroidManifest.xml - Detailed Documentation

## Overview

`AndroidManifest.xml` is the most important configuration file in an Android app. It tells the Android system everything it needs to know about your app:
- What components exist (Activities, Services, etc.)
- What permissions are required
- What hardware/software features are needed
- How to launch the app

Without a properly configured manifest, your app won't work!

---

## Table of Contents

1. [What is AndroidManifest.xml?](#what-is-androidmanifestxml)
2. [File Location](#file-location)
3. [Complete Manifest Breakdown](#complete-manifest-breakdown)
4. [XML Namespaces](#xml-namespaces)
5. [queries Section](#queries-section)
6. [application Section](#application-section)
7. [activity Section](#activity-section)
8. [intent-filter Section](#intent-filter-section)
9. [Why This Configuration Makes It a Launcher](#why-this-configuration-makes-it-a-launcher)
10. [Permissions and Features](#permissions-and-features)
11. [Common Manifest Issues](#common-manifest-issues)

---

## What is AndroidManifest.xml?

### The App's Identity Card

Think of the manifest as your app's identity card. It contains:
- **Who you are**: Package name, version
- **What you can do**: Permissions, features
- **What you have**: Activities, Services, Receivers
- **How to reach you**: Intents, filters

### When is it Used?

**Install Time**:
```
User installs app
    ↓
System reads manifest
    ↓
Checks permissions
    ↓
Checks compatibility (minSdk, features)
    ↓
Registers components
    ↓
Installs app
```

**Run Time**:
```
User launches app
    ↓
System finds MAIN/LAUNCHER activity
    ↓
Starts MainActivity
    ↓
App runs
```

---

## File Location

```
app/src/main/AndroidManifest.xml
```

**Why `src/main/`?**:
- `main` is the primary source set
- Other source sets: `debug/`, `release/`, `test/`
- Each can have its own manifest that gets merged

---

## Complete Manifest Breakdown

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
        </intent>
    </queries>

    <application
        android:name=".LauncherApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Launcher">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTask"
            android:theme="@style/Theme.Launcher">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## XML Namespaces

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
```

### xmlns:android

**Required**: Defines the Android namespace.
- URL is just an identifier (doesn't need internet)
- All Android attributes use `android:` prefix
- Example: `android:name`, `android:label`

### xmlns:tools

**Optional**: Tools namespace for development.
- Used for design-time attributes
- Not included in final APK
- Example: `tools:ignore`, `tools:replace`

---

## queries Section

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>
</queries>
```

### What is queries?

Introduced in Android 11 (API 30), the `<queries>` element declares what other apps your app needs to interact with.

### Why Do We Need It?

**Android 11+ Privacy Change**:
- Before: Apps could see all installed apps
- After: Apps see only whitelisted apps

Without this section, `PackageManager.queryIntentActivities()` returns only system apps!

### Our Query

```xml
<intent>
    <action android:name="android.intent.action.MAIN" />
</intent>
```

**What it means**: "I need to query apps that have MAIN actions"

**Result**: Can see all apps with launcher icons

### Alternative Declarations

**Query specific package**:
```xml
<queries>
    <package android:name="com.google.android.youtube" />
</queries>
```

**Query all packages** (requires special permission):
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

Our approach is best - only query what we need.

---

## application Section

```xml
<application
    android:name=".LauncherApplication"
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Launcher">
```

### android:name

```xml
android:name=".LauncherApplication"
```

**Points to**: `com.milki.launcher.LauncherApplication`

**Purpose**: Custom Application class for app-wide initialization.

**What happens**:
1. System creates LauncherApplication instance
2. Calls `onCreate()`
3. Configures Coil ImageLoader
4. Then creates MainActivity

**Without this**: Uses default `android.app.Application` class.

### android:allowBackup

```xml
android:allowBackup="true"
```

**What it does**: Allows Android's auto-backup feature.

**What gets backed up**:
- SharedPreferences
- DataStore files
- App-specific files

**Our use case**: Recent apps list is backed up and restored!

### Data Extraction Rules

```xml
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

**Purpose**: Configure backup behavior for different scenarios.

**dataExtractionRules**: For device-to-device transfers.
**fullBackupContent**: For cloud backup (Google Drive).

### Icons

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

**icon**: Default icon (used on older devices, square on some launchers).

**roundIcon**: Circular icon (used on newer devices with circular icon masks).

**Location**: 
- `res/mipmap-mdpi/ic_launcher.png`
- `res/mipmap-hdpi/ic_launcher.png`
- etc. (multiple densities)

### android:label

```xml
android:label="@string/app_name"
```

**What it is**: App's display name.

**Reference**: `res/values/strings.xml`:
```xml
<string name="app_name">Milki Launcher</string>
```

**Where shown**:
- Settings > Apps
- Recent apps list
- Share dialogs

### android:supportsRtl

```xml
android:supportsRtl="true"
```

**RTL**: Right-to-Left languages (Arabic, Hebrew, etc.).

**What it does**: Enables RTL layout support.

**Best practice**: Always set to `true` for international apps.

### android:theme

```xml
android:theme="@style/Theme.Launcher"
```

**Purpose**: Default theme for all app components.

**Reference**: `res/values/themes.xml`:
```xml
<style name="Theme.Launcher" parent="android:Theme.Material.Light.NoActionBar">
    <!-- Theme attributes -->
</style>
```

---

## activity Section

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:launchMode="singleTask"
    android:theme="@style/Theme.Launcher">
```

### android:name

```xml
android:name=".MainActivity"
```

**Points to**: `com.milki.launcher.MainActivity`

**What it does**: Declares the Activity exists and can be launched.

### android:exported

```xml
android:exported="true"
```

**Required for**: Activities that can be started by other apps.

**Our case**: Must be `true` because:
- System needs to launch it as home screen
- Intent filters require exported="true"

**Security**: Since API 31, required for all activities with intent filters.

### android:launchMode

```xml
android:launchMode="singleTask"
```

**What it controls**: How Activity instances are created.

**Options**:
- `standard`: New instance every time (default)
- `singleTop`: Reuse if on top of stack
- `singleTask`: One instance, clears other activities
- `singleInstance`: One instance in isolated task

**Why singleTask for launchers?**:
- Only one instance of launcher should exist
- Pressing home brings existing instance to front
- Prevents multiple launcher stacks

**Behavior**:
```
User opens launcher
    ↓
MainActivity created (instance #1)
    ↓
User opens Settings
    ↓
User presses Home
    ↓
Existing MainActivity brought to front (not new instance)
```

### Activity Theme

```xml
android:theme="@style/Theme.Launcher"
```

Can override application theme for specific activity.

---

## intent-filter Section

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

### What is an Intent Filter?

An intent filter declares what intents the component can handle. It says:
> "I can handle intents with these actions and categories"

### action MAIN

```xml
<action android:name="android.intent.action.MAIN" />
```

**Meaning**: This is the entry point of the app.

**What it does**:
- Declares this is the main activity
- Used when app is first launched
- No data expected (just "start the app")

### category HOME

```xml
<category android:name="android.intent.category.HOME" />
```

**This makes it a launcher!**

**What it does**:
- Tells system this is a home screen replacement
- When user presses Home button, system looks for HOME category
- Without this, it's just a regular app

### category DEFAULT

```xml
<category android:name="android.intent.category.DEFAULT" />
```

**Purpose**: Can handle implicit intents.

**Why needed**: Required when action is used without specific component.

### category LAUNCHER

```xml
<category android:name="android.intent.category.LAUNCHER" />
```

**What it does**:
- Shows app icon in system launcher
- Makes app visible to user
- Without this, app is hidden!

### The Magic Combination

```xml
<action android:name="android.intent.action.MAIN" />
<category android:name="android.intent.category.HOME" />
<category android:name="android.intent.category.DEFAULT" />
```

This combination is what makes an app a **home screen replacement** (launcher).

---

## Why This Configuration Makes It a Launcher

### The Launcher Contract

To be a launcher, an app must:

1. **Declare HOME category**:
   ```xml
   <category android:name="android.intent.category.HOME" />
   ```

2. **Be exported**:
   ```xml
   android:exported="true"
   ```

3. **Use singleTask launch mode**:
   ```xml
   android:launchMode="singleTask"
   ```

### System Behavior

When user presses Home button:
```
System sends ACTION_MAIN + CATEGORY_HOME intent
    ↓
System finds all activities with HOME category
    ↓
If multiple: Shows "Choose launcher" dialog
    ↓
User selects "Milki Launcher"
    ↓
MainActivity launched (or brought to front)
```

### First Launch Experience

```
User installs launcher
    ↓
User opens launcher from app drawer
    ↓
Android detects HOME category
    ↓
Asks: "Set as default launcher?"
    ↓
If yes: Becomes default home screen
    ↓
Pressing Home opens this launcher
```

---

## Permissions and Features

### Current Permissions

Our manifest has **no permissions**!

**Why?**:
- Querying installed apps uses `<queries>`, not permission
- Launching apps doesn't require permission
- Reading app icons doesn't require permission

### Permissions We Could Add

**Internet** (if adding web features):
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

**Wallpaper** (if setting wallpaper):
```xml
<uses-permission android:name="android.permission.SET_WALLPAPER" />
```

**Device Admin** (if locking screen):
```xml
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />
```

### Query All Packages (API 30+)

If targeting API 30+ without queries section:
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

**But this requires Play Store justification!**

Our `<queries>` approach is better.

---

## Common Manifest Issues

### Activity Not Found

**Symptom**: "Activity not found" crash

**Cause**: Activity not declared in manifest

**Fix**:
```xml
<activity android:name=".MyActivity" />
```

### App Not Showing in Launcher

**Symptom**: App installed but no icon

**Cause**: Missing LAUNCHER category

**Fix**:
```xml
<category android:name="android.intent.category.LAUNCHER" />
```

### Can't Query Apps (API 30+)

**Symptom**: Empty app list on Android 11+

**Cause**: Missing queries section

**Fix**:
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>
</queries>
```

### Multiple Launcher Instances

**Symptom**: Multiple launcher windows open

**Cause**: Wrong launchMode

**Fix**:
```xml
android:launchMode="singleTask"
```

### Custom Application Not Working

**Symptom**: Coil configuration ignored

**Cause**: Missing application name

**Fix**:
```xml
<application android:name=".LauncherApplication" ...>
```

---

## Manifest Merging

### Multiple Manifests

Android projects can have multiple manifests:
- `src/main/AndroidManifest.xml` (main)
- `src/debug/AndroidManifest.xml` (debug only)
- `src/release/AndroidManifest.xml` (release only)
- Library manifests (from dependencies)

### Merge Process

```
Build APK
    ↓
Gradle collects all manifests
    ↓
Merges them (main + build type + libraries)
    ↓
Resolves conflicts
    ↓
Single merged manifest in APK
```

### Viewing Merged Manifest

Android Studio: `Build > Analyze APK` → Open APK → `AndroidManifest.xml`

---

## Key Takeaways

1. **AndroidManifest.xml**: App's identity card - tells system everything about your app
2. **queries Section**: Required for API 30+ to see installed apps
3. **application Element**: Global app configuration
4. **activity Element**: Declares Activities
5. **intent-filter**: Defines how component can be launched
6. **HOME + MAIN**: Makes app a launcher
7. **singleTask**: Prevents multiple launcher instances
8. **exported="true"**: Required for launcher activities
9. **Manifest merging**: Multiple manifests combined during build

The manifest is small but critical - without the right configuration, your launcher won't work!
