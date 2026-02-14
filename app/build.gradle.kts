/**
 * App-level build.gradle.kts - Build configuration for the launcher app module
 * 
 * This file configures how the app module is built, including:
 * - Android SDK versions (compile, min, target)
 * - App ID and versioning
 * - Build types (debug, release)
 * - Dependencies (libraries the app uses)
 * - Build features (Compose support)
 * 
 * This is the most important build file for day-to-day development.
 * 
 * For detailed documentation, see: docs/BuildConfiguration.md
 */

// ============================================================================
// PLUGINS
// ============================================================================
// Plugins extend Gradle with Android-specific functionality.
// We use the version catalog (libs) for centralized version management.
plugins {
    // Android Application plugin - enables building Android apps
    // Provides: android { } block, APK/AAB generation, etc.
    alias(libs.plugins.android.application)
    
    // Kotlin Compose plugin - enables Jetpack Compose
    // Provides: Compose compiler, preview support, live literals
    alias(libs.plugins.kotlin.compose)
}

// ============================================================================
// ANDROID CONFIGURATION
// ============================================================================
android {
    // ------------------------------------------------------------------------
    // NAMESPACE
    // ------------------------------------------------------------------------
    // The Kotlin/Java package namespace for generated code.
    // Must match the 'package' statement in your Kotlin files.
    // Used for: R.java, BuildConfig.java, databinding
    namespace = "com.milki.launcher"
    
    // ------------------------------------------------------------------------
    // COMPILE SDK
    // ------------------------------------------------------------------------
    // The Android SDK version used to compile the app.
    // Determines which APIs are available at compile time.
    // Should use the latest stable version.
    // Does NOT affect minimum device requirements.
    compileSdk {
        version = release(36)  // API 36 = Android 16/Baklava
    }

    // ------------------------------------------------------------------------
    // DEFAULT CONFIG
    // ------------------------------------------------------------------------
    // Default settings applied to all build variants (debug, release, etc.)
    defaultConfig {
        // Application ID - unique identifier for your app
        // Used by: Play Store, device installation, app updates
        // Cannot be changed after publishing to Play Store!
        applicationId = "com.milki.launcher"
        
        // Minimum SDK - lowest Android version supported
        // App won't install on older devices
        // API 24 = Android 7.0 (covers ~95% of devices)
        minSdk = 24
        
        // Target SDK - version the app is designed for
        // Affects compatibility behavior
        // Should match compileSdk
        targetSdk = 36
        
        // Version code - internal version number (integer)
        // Must increase with each release
        // Used by Play Store for update detection
        versionCode = 1
        
        // Version name - user-visible version string
        // Can be any format: "1.0", "1.0.0-beta", "2.0.1"
        // Shown in Settings > Apps
        versionName = "1.0"

        // Test runner for instrumented tests (tests running on device)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ------------------------------------------------------------------------
    // BUILD TYPES
    // ------------------------------------------------------------------------
    // Different configurations for building the app
    buildTypes {
        // Release build - optimized for distribution
        release {
            // Code shrinking/obfuscation (disabled for now)
            // When enabled: Removes unused code, obfuscates names
            // Should be true for production releases
            isMinifyEnabled = false
            
            // ProGuard/R8 configuration files
            // Define rules for code shrinking
            proguardFiles(
                // Default Android ProGuard rules
                getDefaultProguardFile("proguard-android-optimize.txt"),
                // App-specific ProGuard rules
                "proguard-rules.pro"
            )
        }
        
        // Debug build (not explicitly configured, uses defaults)
        // - Includes debug symbols
        // - No code optimization
        // - Faster builds
        // - debug { } block would go here if needed
    }
    
    // ------------------------------------------------------------------------
    // COMPILE OPTIONS
    // ------------------------------------------------------------------------
    // Java language and bytecode compatibility
    compileOptions {
        // Source compatibility - which Java language features to support
        sourceCompatibility = JavaVersion.VERSION_11
        
        // Target compatibility - bytecode version to generate
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    // ------------------------------------------------------------------------
    // BUILD FEATURES
    // ------------------------------------------------------------------------
    // Enable specific Android build features
    buildFeatures {
        // Enable Jetpack Compose
        // Required for Compose projects
        compose = true
    }
}

// ============================================================================
// DEPENDENCIES
// ============================================================================
// External libraries used by the app.
// We use version catalog (libs) for centralized version management.
dependencies {
    
    // ========================================================================
    // CORE ANDROID LIBRARIES
    // ========================================================================
    
    // Android KTX - Kotlin extensions for Android framework
    // Makes Android APIs more Kotlin-friendly (extension functions, etc.)
    implementation(libs.androidx.core.ktx)
    
    // Lifecycle - Components that are lifecycle-aware
    // Provides: ViewModel, LiveData, lifecycleScope
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // ViewModel Compose - Integration between ViewModel and Compose
    // Provides: viewModel() function in composables
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Activity Compose - Integration between Activities and Compose
    // Provides: setContent { }, ComponentActivity support
    implementation(libs.androidx.activity.compose)
    
    // ========================================================================
    // JETPACK COMPOSE
    // ========================================================================
    
    // Compose BOM (Bill of Materials) - Manages Compose library versions
    // Ensures all Compose libraries use compatible versions
    implementation(platform(libs.androidx.compose.bom))
    
    // Core Compose UI
    implementation(libs.androidx.compose.ui)
    
    // Compose Graphics - Graphics utilities and drawing
    implementation(libs.androidx.compose.ui.graphics)
    
    // Compose Tooling Preview - Preview annotation support
    // Shows Composable previews in Android Studio
    implementation(libs.androidx.compose.ui.tooling.preview)
    
    // Material Design 3 - Material You components
    // Modern UI components following Material Design 3 guidelines
    implementation(libs.androidx.compose.material3)
    
    // ========================================================================
    // DATA PERSISTENCE
    // ========================================================================
    
    // DataStore - Modern data storage solution
    // Replaces SharedPreferences, uses coroutines and Flow
    implementation(libs.androidx.datastore.preferences)
    
    // ========================================================================
    // IMAGE LOADING
    // ========================================================================
    
    // Coil - Image loading library for Android
    // Kotlin coroutines-based, efficient caching, Compose integration
    implementation(libs.coil.compose)
    
    // ========================================================================
    // TESTING - UNIT TESTS
    // ========================================================================
    
    // JUnit - Unit testing framework
    // Runs on JVM (fast, no Android framework needed)
    testImplementation(libs.junit)
    
    // ========================================================================
    // TESTING - INSTRUMENTED TESTS
    // ========================================================================
    // These run on actual device/emulator
    
    // JUnit for Android tests
    androidTestImplementation(libs.androidx.junit)
    
    // Espresso - UI testing framework
    androidTestImplementation(libs.androidx.espresso.core)
    
    // Compose BOM for test dependencies (ensures version alignment)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    
    // Compose UI testing
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    
    // ========================================================================
    // DEBUG TOOLS
    // ========================================================================
    // Only included in debug builds, not release
    
    // Compose Tooling - Debug tools for Compose
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Compose Test Manifest - For testing
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ============================================================================
// DEPENDENCY CONFIGURATION KEYWORDS
// ============================================================================
// implementation - Use in production code (most common)
// api - Expose to dependent modules (use sparingly)
// compileOnly - Use at compile time only (not in APK)
// runtimeOnly - Use at runtime only
// testImplementation - Use in unit tests (test/ folder)
// androidTestImplementation - Use in instrumented tests (androidTest/ folder)
// debugImplementation - Use only in debug builds
