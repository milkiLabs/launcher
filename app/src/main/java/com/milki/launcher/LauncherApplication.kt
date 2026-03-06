/**
 * LauncherApplication.kt - Application class for global configuration
 * 
 * This class extends Application and provides:
 * 1. Koin dependency injection initialization
 * 
 * Key responsibilities:
 * 1. Initialize Koin DI container with all app modules
 * 2. Provide one-time app-wide startup initialization
 * 
 * The Application class is the first component created when the app starts
 * (before any Activity). It's the perfect place for initialization that
 * needs to happen once per app session.
 * 
 * For detailed documentation, see: docs/LauncherApplication.md
 */

package com.milki.launcher

// ============================================================================
// IMPORTS - Android Framework
// ============================================================================
import android.app.Application

// ============================================================================
// IMPORTS - Koin (Dependency Injection)
// ============================================================================
import com.milki.launcher.di.allModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

// ============================================================================
// APPLICATION CLASS
// ============================================================================
/**
 * LauncherApplication is the custom Application class for our launcher.
 * 
 * Why do we need a custom Application class?
 * 
 * 1. Koin Initialization: We need to start Koin before any Activity is created.
 *    Koin provides all dependencies (repositories, view models, etc.)
 * 
 * 2. App-Wide Initialization: The Application is created before any
 *    Activity, so it's the perfect place for one-time setup.
 * 
 * 3. Singleton Pattern: The Application instance is a singleton that
 *    lives for the entire app session.
 * 
 * IMPORTANT: This class MUST be declared in AndroidManifest.xml:
 * <application android:name=".LauncherApplication" ...>
 * 
 * Implements:
 * - Application: Base class for app-wide state
 */
class LauncherApplication : Application() {

    /**
     * Called when the application is starting.
     * 
     * INITIALIZATION ORDER:
     * 1. super.onCreate() - Always call first
     * 2. initializeKoin() - Start Koin DI before any dependencies are needed
     */
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin dependency injection
        // This must happen before any Activity is created
        initializeKoin()
    }

    /**
     * Initialize Koin dependency injection.
     * 
     * KOIN SETUP:
     * 1. androidContext() - Provides Android Context to Koin
     *    This allows dependencies to request Context via get()
     * 
     * 2. androidLogger() - Enables Koin's Android logger
     *    Shows dependency resolution in Logcat for debugging
     *    Level.ERROR only shows errors (use Level.DEBUG for more detail)
     * 
     * 3. modules() - Register Koin modules
     *    Each module defines a set of dependencies
     *    We have one appModule that contains all our dependencies
     * 
     * WHY STARTKOIN IN APPLICATION?
     * - Application is created before any Activity
     * - Koin needs to be ready when Activities request ViewModels
     * - The Application context lives for the entire app session
     */
    private fun initializeKoin() {
        startKoin {
            // Provide the Application context to Koin
            // This is used by dependencies that need Context (repositories, etc.)
            androidContext(this@LauncherApplication)
            
            // Enable Koin's Android logger for debugging
            // Use Level.ERROR for production, Level.DEBUG for development
            androidLogger(Level.ERROR)
            
            // Load our dependency modules
            // allModules aggregates all feature modules: core, search, home, widget, settings, drawer
            // Each module defines dependencies for its own feature, keeping the DI graph organized.
            // See AppModule.kt for the full module list and docs/KoinDependencyInjection.md for details.
            modules(allModules)
        }
    }
}
