/**
 * LauncherApplication.kt - Application class for global configuration
 * 
 * This class extends Application and provides:
 * 1. Coil ImageLoader configuration for loading app icons
 * 2. Dependency injection container for the entire app
 * 
 * Key responsibilities:
 * 1. Configure Coil ImageLoader with custom settings
 * 2. Register custom AppIconFetcher for loading app icons
 * 3. Set up memory caching for optimal performance
 * 4. Initialize the AppContainer for dependency injection
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
// IMPORTS - Dependency Injection
// ============================================================================
import com.milki.launcher.di.AppContainer

// ============================================================================
// APPLICATION CLASS
// ============================================================================
/**
 * LauncherApplication is the custom Application class for our launcher.
 * 
 * Why do we need a custom Application class?
 * 
 * 1. Coil Configuration: We need to register our custom AppIconFetcher
 *    and configure caching settings. This must happen before any images
 *    are loaded.
 * 
 * 2. App-Wide Initialization: The Application is created before any
 *    Activity, so it's the perfect place for one-time setup.
 * 
 * 3. Dependency Injection: The AppContainer is created here and lives
 *    for the entire app session, providing all dependencies.
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
     * Dependency injection container for the entire app.
     * Created once and accessed from Activities and ViewModels.
     */
    lateinit var container: AppContainer
        private set
    
    /**
     * Called when the application is starting.
     * Initialize the DI container here before any Activity is created.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize the dependency container
        // This must happen before any Activity is created
        container = AppContainer(this)
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
