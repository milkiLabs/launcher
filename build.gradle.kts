/**
 * Project-level build.gradle.kts - Root build configuration
 * 
 * This file configures settings that apply to all modules in the project.
 * Unlike the app-level build.gradle.kts, this file doesn't build the app
 * directly but provides shared configuration.
 * 
 * Key purposes:
 * 1. Define plugins used across all modules
 * 2. Configure project-wide settings
 * 3. Serve as entry point for multi-module projects
 * 
 * For our single-module project, this file is simple. In larger projects
 * with multiple modules (app, library, feature modules), this file becomes
 * more important for sharing configuration.
 * 
 * For detailed documentation, see: docs/BuildConfiguration.md
 */

// Top-level comment explaining the file purpose
// This build file is where you add configuration options common to all 
// sub-projects/modules in the project.

// ============================================================================
// PLUGINS
// ============================================================================
// Plugins extend Gradle with Android and Kotlin functionality.
// 
// Note: We use 'apply false' because we don't apply these plugins to the root
// project directly. Instead, we declare them here and each module applies
// them in their own build.gradle.kts file.
//
// This approach:
// 1. Centralizes plugin version management
// 2. Allows modules to opt-in to plugins they need
// 3. Supports multi-module projects where different modules use different plugins
plugins {
    // Android Application plugin
    // Used by: app module (and any other app modules)
    // Provides: Building Android APKs/AABs
    // 
    // alias(libs.plugins...) uses the version catalog (libs.versions.toml)
    // for centralized version management
    // 
    // apply false means: "Declare this plugin but don't apply it to root project"
    alias(libs.plugins.android.application) apply false
    
    // Kotlin Compose plugin
    // Used by: app module (for Jetpack Compose support)
    // Provides: Compose compiler, preview support
    alias(libs.plugins.kotlin.compose) apply false
    
    // Kotlin Serialization plugin
    // Used by: app module (for JSON serialization of data classes)
    // Provides: @Serializable annotation, compiler-generated serializers
    alias(libs.plugins.kotlin.serialization) apply false
}

// ============================================================================
// NOTES
// ============================================================================
// This file is intentionally minimal for a single-module project.
// 
// Additional configurations you might add here for larger projects:
// 
// 1. Allprojects block - repositories for all modules:
//    allprojects {
//        repositories {
//            google()
//            mavenCentral()
//        }
//    }
//
// 2. Subprojects configuration - settings for all subprojects:
//    subprojects {
//        // Common configuration for all modules
//    }
//
// 3. Clean task - custom clean behavior:
//    tasks.register<Delete>("clean") {
//        delete(rootProject.buildDir)
//    }
//
// 4. Ext properties - variables shared across modules:
//    ext {
//        set("compileSdk", 34)
//        set("minSdk", 24)
//    }

// ============================================================================
// BUILD STRUCTURE
// ============================================================================
// Typical Android project structure:
//
// root/
// ├── build.gradle.kts          (this file - project-level config)
// ├── settings.gradle.kts       (project settings, modules list)
// ├── gradle.properties         (Gradle properties)
// ├── gradle/libs.versions.toml (version catalog - dependency versions)
// └── app/
//     └── build.gradle.kts      (app module config - dependencies, SDK versions)
//
// For multi-module projects:
// root/
// ├── build.gradle.kts
// ├── settings.gradle.kts
// ├── app/build.gradle.kts
// ├── library/build.gradle.kts
// ├── feature1/build.gradle.kts
// └── feature2/build.gradle.kts
