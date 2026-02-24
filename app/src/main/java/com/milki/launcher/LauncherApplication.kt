/**
 * LauncherApplication.kt - Application class for global configuration
 * 
 * This class extends Application and provides:
 * 1. Coil ImageLoader configuration for loading app icons
 * 2. Koin dependency injection initialization
 * 
 * Key responsibilities:
 * 1. Initialize Koin DI container with all app modules
 * 2. Configure Coil ImageLoader with custom settings
 * 3. Register custom AppIconFetcher for loading app icons
 * 4. Set up memory caching for optimal performance
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
// IMPORTS - Coil (Image Loading Library)
// ============================================================================
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

// ============================================================================
// IMPORTS - Koin (Dependency Injection)
// ============================================================================
import com.milki.launcher.di.appModule
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
 * 2. Coil Configuration: We need to register our custom AppIconFetcher
 *    and configure caching settings. This must happen before any images
 *    are loaded.
 * 
 * 3. App-Wide Initialization: The Application is created before any
 *    Activity, so it's the perfect place for one-time setup.
 * 
 * 4. Singleton Pattern: The Application instance is a singleton that
 *    lives for the entire app session.
 * 
 * IMPORTANT: This class MUST be declared in AndroidManifest.xml:
 * <application android:name=".LauncherApplication" ...>
 * 
 * Implements:
 * - Application: Base class for app-wide state
 * - ImageLoaderFactory: Interface for providing custom ImageLoader to Coil
 */
class LauncherApplication : Application(), ImageLoaderFactory {

    /**
     * Called when the application is starting.
     * 
     * INITIALIZATION ORDER:
     * 1. super.onCreate() - Always call first
     * 2. initializeKoin() - Start Koin DI before any dependencies are needed
     * 3. Coil is configured lazily when newImageLoader() is called
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
            // appModule contains all repositories, providers, use cases, and ViewModels
            modules(appModule)
        }
    }
    
    /**
     * Creates and returns a custom ImageLoader configuration.
     * 
     * This method is called by Coil when it needs an ImageLoader instance.
     * By implementing ImageLoaderFactory, we ensure Coil uses our custom
     * configuration instead of default settings.
     * 
     * The ImageLoader is created lazily (only when first needed) and then
     * cached for the app's lifetime.
     * 
     * @return Configured ImageLoader instance
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache(null)
            .allowHardware(true)
            .respectCacheHeaders(false)
            .build()
    }
}
