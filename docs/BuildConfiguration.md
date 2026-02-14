# Build Configuration - Detailed Documentation

## Overview

This document explains the Gradle build configuration files that control how the Milki Launcher is built. Understanding build configuration is essential for:
- Adding dependencies
- Configuring the app for different devices
- Managing versions
- Optimizing builds

---

## Table of Contents

1. [What is Gradle?](#what-is-gradle)
2. [Project Structure](#project-structure)
3. [settings.gradle.kts](#settingsgradlekts)
4. [build.gradle.kts (Project Level)](#buildgradlekts-project-level)
5. [build.gradle.kts (App Level)](#buildgradlekts-app-level)
6. [Gradle Version Catalog](#gradle-version-catalog)
7. [Understanding Dependencies](#understanding-dependencies)
8. [Build Types](#build-types)
9. [Compile Options](#compile-options)
10. [Common Tasks](#common-tasks)

---

## What is Gradle?

### Build Automation

Gradle is a build automation tool that:
- Compiles your code
- Downloads and manages dependencies
- Packages your app (APK/AAB)
- Runs tests
- Handles different build variants

### Why Gradle?

**Before Build Tools**:
```bash
# Manual steps to build an app:
1. Compile Java/Kotlin files
2. Download libraries
3. Merge resources
4. Sign the APK
5. Zip align
# ... dozens more steps
```

**With Gradle**:
```bash
./gradlew build  # One command does everything!
```

### Gradle in Android

Android Studio uses Gradle with Android-specific plugins:
- **com.android.application**: For app modules
- **org.jetbrains.kotlin.android**: For Kotlin support
- **com.android.library**: For library modules

---

## Project Structure

```
launcher/
├── settings.gradle.kts          # Project settings
├── build.gradle.kts             # Project-level build config
├── gradle.properties            # Gradle properties
├── gradle/libs.versions.toml    # Version catalog (referenced but not shown)
└── app/
    └── build.gradle.kts         # App-level build config
```

### Two Levels of Build Files

**Project Level** (`/build.gradle.kts`):
- Configures all modules in the project
- Defines plugins used across modules
- Sets up repositories

**App Level** (`/app/build.gradle.kts`):
- Configures the app module specifically
- Defines app ID, SDK versions
- Lists dependencies
- Configures build types

---

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "launcher"
include(":app")
```

### pluginManagement Block

**Purpose**: Configures where to find Gradle plugins.

**Repositories**:
- **google()**: Android and Google plugins
- **mavenCentral()**: General Java/Kotlin libraries
- **gradlePluginPortal()**: Community Gradle plugins

**Content Filtering**:
```kotlin
google {
    content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
    }
}
```

This tells Gradle to only look in the Google repository for packages matching these patterns, improving build speed.

### dependencyResolutionManagement Block

**Purpose**: Configures where to find app dependencies.

**repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)**:
- Prevents modules from defining their own repositories
- Ensures consistent dependency resolution
- Centralizes repository configuration

**Repositories**:
- **google()**: Android libraries (androidx, material, etc.)
- **mavenCentral()**: General libraries (Kotlin, third-party)

### Project Name and Modules

```kotlin
rootProject.name = "launcher"
include(":app")
```

**rootProject.name**: The name of your project (appears in Android Studio).

**include(":app")**: Includes the app module in the build.
- `:app` refers to the `app/` directory
- You could add more modules: `include(":app", ":library", ":feature")`

---

## build.gradle.kts (Project Level)

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

### Comments

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
```

Explains this file configures settings for all modules (currently just `:app`).

### plugins Block

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

**What are Plugins?**

Plugins extend Gradle with new functionality:
- `android.application`: Builds Android apps
- `kotlin.compose`: Enables Jetpack Compose

**alias(libs.plugins...)**:
- Uses the version catalog (libs.versions.toml)
- Centralizes version management
- Type-safe references

**apply false**:
- Declares the plugin for all modules
- But doesn't apply it to the root project
- Each module applies it in their own build.gradle.kts

**Equivalent Without Version Catalog**:
```kotlin
plugins {
    id("com.android.application") version "8.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "1.8.0" apply false
}
```

Version catalog is cleaner and easier to maintain!

---

## build.gradle.kts (App Level)

This is the most important build file for our app.

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.milki.launcher"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.milki.launcher"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

### plugins Block

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
```

**Note**: No `apply false` here - we actually apply these plugins to this module.

**android.application**: Enables Android app building for this module.

**kotlin.compose**: Enables Compose compiler and features.

### android Block

Configures Android-specific settings.

#### namespace

```kotlin
namespace = "com.milki.launcher"
```

**What it is**: The Kotlin/Java package namespace for generated code.

**Must match**: The `package` statement in your Kotlin files:
```kotlin
package com.milki.launcher  // ← Must match namespace
```

**Used for**:
- Generating R.java (resource references)
- Build config class
- Data binding

#### compileSdk

```kotlin
compileSdk {
    version = release(36)
}
```

**What it is**: The Android SDK version used to compile your app.

**version = release(36)**: Uses API level 36 (Android 16/Baklava).

**Why important**:
- Determines what APIs are available at compile time
- Should use the latest stable version
- Doesn't affect minimum device requirements

#### defaultConfig Block

```kotlin
defaultConfig {
    applicationId = "com.milki.launcher"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}
```

**applicationId**: Unique identifier for your app on Play Store and device.
```kotlin
applicationId = "com.milki.launcher"
```
- Usually matches namespace
- Cannot be changed after publishing
- Used for: Play Store, device installation, app updates

**minSdk**: Minimum Android version your app supports.
```kotlin
minSdk = 24  // Android 7.0 (API 24)
```
- App won't install on older devices
- Must balance features vs compatibility
- 24 = Android 7.0 covers ~95% of devices

**targetSdk**: The Android version your app is designed for.
```kotlin
targetSdk = 36  // Android 16 (API 36)
```
- Affects behavior of compatibility modes
- Should match compileSdk
- Google Play requires recent targetSdk

**versionCode**: Internal version number (integer).
```kotlin
versionCode = 1
```
- Must increase with each release
- Used by Play Store for updates
- Not shown to users

**versionName**: User-visible version string.
```kotlin
versionName = "1.0"
```
- Can be any string ("1.0", "1.0.0-beta", etc.)
- Shown in Settings > Apps
- For user reference only

**testInstrumentationRunner**: Test runner for instrumented tests.
```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```
- Runs tests on device/emulator
- JUnit4 test framework

#### buildTypes Block

```kotlin
buildTypes {
    release {
        isMinifyEnabled = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

**Build Types**: Different versions of your app.

**debug** (default, not shown):
- Enabled by default
- Includes debug symbols
- No code optimization
- Faster builds

**release**:
```kotlin
release {
    isMinifyEnabled = false  // Code shrinking
    proguardFiles(...)       // Obfuscation rules
}
```

**isMinifyEnabled = false**:
- If `true`: Shrinks and optimizes code
- If `false`: Faster builds, easier debugging
- Should be `true` for production

**proguardFiles**:
- `proguard-android-optimize.txt`: Default Android rules
- `proguard-rules.pro`: Your custom rules
- Only used when minifyEnabled is true

#### compileOptions Block

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

**Java Version**: Which Java language features to support.

**VERSION_11**:
- Modern Java features
- Required by recent Android Gradle Plugin
- Good balance of features and compatibility

**sourceCompatibility**: Language features you can use.
**targetCompatibility**: Bytecode version to generate.

#### buildFeatures Block

```kotlin
buildFeatures {
    compose = true
}
```

**Enables Jetpack Compose**:
- Compose compiler plugin
- Preview support
- Live Edit (in development)

---

## Gradle Version Catalog

### What is it?

The version catalog (in `gradle/libs.versions.toml`) centralizes dependency versions:

```toml
[versions]
agp = "8.1.0"
kotlin = "1.8.0"
compose-bom = "2023.08.00"
coil = "2.4.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "kotlin" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```

### Benefits

1. **Single source of truth**: Versions defined once
2. **Type-safe**: Auto-complete in IDE
3. **Centralized**: Easy to update versions
4. **Readable**: Clear dependency names

### Using in build.gradle.kts

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
}
```

---

## Understanding Dependencies

### Dependency Notations

```kotlin
implementation(libs.androidx.core.ktx)
```

**implementation**: Use this dependency in production code.

**Other configurations**:
- `api`: Expose to dependent modules
- `compileOnly`: Use at compile time only
- `runtimeOnly`: Use at runtime only
- `testImplementation`: Use in unit tests
- `androidTestImplementation`: Use in instrumented tests
- `debugImplementation`: Use in debug builds only

### Our Dependencies Explained

#### Core Android

```kotlin
implementation(libs.androidx.core.ktx)
```
**Android KTX**: Kotlin extensions for Android framework.
- Makes Android APIs more Kotlin-friendly
- Adds extension functions
- Essential for modern Android

```kotlin
implementation(libs.androidx.lifecycle.runtime.ktx)
```
**Lifecycle**: Components that are lifecycle-aware.
- ViewModel support
- LiveData
- LifecycleScope

```kotlin
implementation(libs.androidx.lifecycle.viewmodel.compose)
```
**ViewModel Compose**: Integration between ViewModel and Compose.
- `viewModel()` function in Compose
- State observation

```kotlin
implementation(libs.androidx.activity.compose)
```
**Activity Compose**: Compose integration with Activities.
- `setContent` function
- `ComponentActivity` support

#### Jetpack Compose

```kotlin
implementation(platform(libs.androidx.compose.bom))
```
**Compose BOM**: Bill of Materials.
- Manages versions for all Compose libraries
- Ensures compatibility
- Update one version, get all updates

```kotlin
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.ui.graphics)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.compose.material3)
```

**compose.ui**: Core Compose UI components.
**compose.ui.graphics**: Graphics utilities.
**compose.ui.tooling.preview**: Preview annotation support.
**compose.material3**: Material Design 3 components.

#### Data Persistence

```kotlin
implementation(libs.androidx.datastore.preferences)
```
**DataStore**: Modern data storage solution.
- Replaces SharedPreferences
- Uses coroutines and Flow
- Type-safe

#### Image Loading

```kotlin
implementation(libs.coil.compose)
```
**Coil**: Image loading library.
- Kotlin coroutines-based
- Efficient caching
- Compose integration

#### Testing

```kotlin
testImplementation(libs.junit)
```
**JUnit**: Unit testing framework.
- Runs on JVM (fast)
- No Android framework needed

```kotlin
androidTestImplementation(libs.androidx.junit)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
```

**androidx.junit**: JUnit for Android tests.
**espresso.core**: UI testing framework.
**compose.ui.test.junit4**: Testing Compose UI.

#### Debug Tools

```kotlin
debugImplementation(libs.androidx.compose.ui.tooling)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

Only included in debug builds, not release.

---

## Build Types

### Debug vs Release

| Feature | Debug | Release |
|---------|-------|---------|
| Build Speed | Fast | Slow |
| Code Optimization | None | Yes (R8/ProGuard) |
| Debugging | Full support | Limited |
| Signing | Debug key | Release key |
| Size | Larger | Smaller |

### Creating Custom Build Types

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        versionNameSuffix = "-debug"
    }
    
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("release")
    }
    
    create("staging") {
        initWith(getByName("debug"))
        applicationIdSuffix = ".staging"
    }
}
```

---

## Compile Options

### Java Compatibility

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

**Java 11 Features Available**:
- `var` keyword
- New String methods
- HTTP Client API
- Improved Optional
- Lambda improvements

### Kotlin Options

```kotlin
kotlinOptions {
    jvmTarget = "11"
}
```

Ensures Kotlin compiles to Java 11 bytecode.

---

## Common Tasks

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Run connected tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Full clean build
./gradlew clean build
```

### Viewing Dependency Tree

```bash
./gradlew app:dependencies
```

Shows all dependencies and their transitive dependencies.

### Checking for Updates

```bash
./gradlew dependencyUpdates
```

(Requires plugin) Shows which dependencies have newer versions.

---

## Key Takeaways

1. **settings.gradle.kts**: Configures repositories and modules
2. **Project build.gradle.kts**: Applies plugins to all modules
3. **App build.gradle.kts**: Configures your specific app
4. **namespace**: Must match your Kotlin package
5. **minSdk**: Determines minimum device support
6. **targetSdk**: Should match compileSdk
7. **Dependencies**: Use version catalog for centralized management
8. **Build Types**: Debug for development, Release for distribution
9. **Compose**: Enabled via buildFeatures

Understanding build configuration is crucial for maintaining and extending your Android project!
