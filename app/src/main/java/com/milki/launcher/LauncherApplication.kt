/**
 * LauncherApplication.kt - Application class for global configuration
 * 
 * This class extends Application and implements ImageLoaderFactory to provide
 * app-wide configuration for Coil (our image loading library).
 * 
 * Key responsibilities:
 * 1. Configure Coil ImageLoader with custom settings
 * 2. Register custom AppIconFetcher for loading app icons
 * 3. Set up memory caching for optimal performance
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
// Application is the base class for maintaining global application state.
// It's instantiated before any Activity and lives for the app's lifetime.
import android.app.Application

// ============================================================================
// IMPORTS - Coil (Image Loading Library)
// ============================================================================
// ImageLoader is Coil's main class for loading images. It's a singleton
// (one instance per app) that manages caching, fetching, and decoding.
import coil.ImageLoader

// ImageLoaderFactory is an interface that Coil uses to get a custom ImageLoader.
// When our Application implements this, Coil calls newImageLoader() to get
// our configured instance instead of using defaults.
import coil.ImageLoaderFactory

// MemoryCache manages the in-memory cache for loaded images.
// This makes subsequent loads instant and reduces memory allocations.
import coil.memory.MemoryCache

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
 * 3. Singleton Pattern: The Application instance is a singleton that
 *    lives for the entire app session.
 * 
 * IMPORTANT: This class MUST be declared in AndroidManifest.xml:
 * <application android:name=".LauncherApplication" ...>
 * 
 * Without this declaration, Android will use the default Application
 * class, and our custom Coil configuration won't be applied!
 * 
 * Implements:
 * - Application: Base class for app-wide state
 * - ImageLoaderFactory: Interface for providing custom ImageLoader to Coil
 */
class LauncherApplication : Application(), ImageLoaderFactory {
    
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
        // Use ImageLoader.Builder to configure our custom ImageLoader.
        // We pass 'this' (the Application) as the context.
        return ImageLoader.Builder(this)
            
            // ============================================================
            // CUSTOM COMPONENTS
            // ============================================================
            // Register our custom AppIconFetcher.Factory.
            // 
            // Components are pluggable parts of Coil:
            // - Fetchers: Know how to load specific data types
            // - Decoders: Know how to decode specific formats
            // - Mappers: Transform requests
            //
            // Our AppIconFetcher knows how to load Android app icons from
            // package names using PackageManager. Without registering it,
            // Coil wouldn't know how to handle AppIconRequest objects.
            .components {
                add(AppIconFetcher.Factory())
            }
            
            // ============================================================
            // MEMORY CACHE CONFIGURATION
            // ============================================================
            // Configure the in-memory cache for loaded images.
            // 
            // Why memory cache?
            // - Makes repeated loads instant (RAM access vs PackageManager)
            // - Reduces battery usage (no repeated processing)
            // - Smooth scrolling in app list
            //
            // We configure it to use 15% of available app memory.
            // This balances performance vs memory usage.
            .memoryCache {
                MemoryCache.Builder(this)
                    // Use 15% of available memory for the cache
                    // Too small: frequent reloading, slow UI
                    // Too large: risk of OutOfMemoryError
                    // 15% is a good balance for most apps
                    .maxSizePercent(0.15)
                    .build()
            }
            
            // ============================================================
            // DISK CACHE CONFIGURATION
            // ============================================================
            // Disable disk cache for app icons.
            // 
            // Why no disk cache?
            // 1. Icons are already cached by Android's PackageManager
            // 2. We load from PackageManager, not network
            // 3. Disk cache would be redundant and waste storage
            // 4. PackageManager handles icon updates when apps update
            //
            // If we were loading from network, we'd want disk cache
            // for offline viewing. But for system icons, it's unnecessary.
            .diskCache(null)
            
            // ============================================================
            // HARDWARE BITMAP CONFIGURATION
            // ============================================================
            // Disable hardware bitmaps.
            //
            // Hardware bitmaps store pixel data in GPU memory:
            // Pros: Faster rendering, less CPU usage
            // Cons: Can't be read by CPU, compatibility issues on some devices
            //
            // Why disable for a launcher?
            // 1. App icons are small (40dp), performance difference is negligible
            // 2. Safer compatibility across all devices
            // 3. Launcher is critical - must work on all devices
            // 4. No complex drawing that would benefit from hardware bitmaps
            .allowHardware(false)
            
            // ============================================================
            // CACHE HEADERS
            // ============================================================
            // Disable respect for HTTP cache headers.
            //
            // HTTP cache headers tell clients how long to cache responses.
            // Example: "Cache-Control: max-age=3600" means cache for 1 hour.
            //
            // Why disable?
            // We're not loading from network, so HTTP cache headers don't apply.
            // This setting only matters for network requests.
            // Disabling it slightly reduces processing overhead.
            .respectCacheHeaders(false)
            
            // Build the ImageLoader with all our configurations
            .build()
    }
}
