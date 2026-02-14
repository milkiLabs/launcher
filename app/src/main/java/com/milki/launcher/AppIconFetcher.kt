/**
 * AppIconFetcher.kt - Custom Coil component for loading Android app icons
 * 
 * This file contains a custom Coil Fetcher that teaches the Coil image loading
 * library how to load Android app icons from package names. Since Coil doesn't
 * know about Android's PackageManager by default, we create this custom component.
 * 
 * The file contains:
 * - AppIconRequest: A data class to identify app icon requests
 * - AppIconFetcher: The Fetcher implementation that loads icons
 * - AppIconFetcher.Factory: Factory class that creates Fetcher instances
 * 
 * For detailed documentation, see: docs/AppIconFetcher.md
 */

package com.milki.launcher

// ============================================================================
// IMPORTS - Android Framework
// ============================================================================
// Context is needed to access the PackageManager system service
import android.content.Context

// PackageManager is the system service that provides app information,
// including the ability to load app icons
import android.content.pm.PackageManager

// ============================================================================
// IMPORTS - Coil (Image Loading)
// ============================================================================
// ImageLoader is Coil's main class - we receive it in the Factory
import coil.ImageLoader

// DataSource indicates where the image came from (MEMORY, DISK, NETWORK)
import coil.decode.DataSource

// DrawableResult is used when the fetcher returns a Drawable (not a Bitmap)
import coil.fetch.DrawableResult

// FetchResult is the base type for all fetch results
import coil.fetch.FetchResult

// Fetcher is the interface we implement to create custom loaders
import coil.fetch.Fetcher

// Options contains metadata about the request (size, context, etc.)
import coil.request.Options

// ============================================================================
// DATA CLASS: AppIconRequest
// ============================================================================
/**
 * Wrapper class to identify app icon requests.
 * 
 * This data class wraps a package name and serves as a type-safe identifier
 * for app icon loading requests. By using a custom type instead of a plain
 * String, we ensure:
 * 
 * 1. Type Safety: Only AppIconRequest objects are handled by our Fetcher
 * 2. Clear Intent: It's obvious this represents an app icon request
 * 3. Extensibility: We could add more fields later (icon size, density, etc.)
 * 
 * Example usage in Compose:
 * Image(
 *     painter = rememberAsyncImagePainter(AppIconRequest("com.google.android.youtube")),
 *     contentDescription = null
 * )
 * 
 * @property packageName The Android package name (e.g., "com.google.android.youtube")
 */
data class AppIconRequest(val packageName: String)

// ============================================================================
// FETCHER: AppIconFetcher
// ============================================================================
/**
 * Custom Coil Fetcher that loads app icons from package names.
 * 
 * A Fetcher is a Coil component that knows how to load a specific type of data.
 * This Fetcher knows how to:
 * 1. Take an AppIconRequest (containing a package name)
 * 2. Use PackageManager to load the app's icon
 * 3. Return it as a DrawableResult
 * 
 * The Fetcher pattern allows Coil to support any data type - not just URLs
 * and file paths. We "teach" Coil about Android app icons by providing
 * this custom implementation.
 * 
 * Key characteristics:
 * - Runs on a background thread (suspend function)
 * - Caches results automatically via Coil's memory cache
 * - Handles errors gracefully (returns default icon if app not found)
 * - Fast operation (icons cached by PackageManager)
 * 
 * @param data The AppIconRequest containing the package name to load
 * @param options Coil Options containing context and request metadata
 */
class AppIconFetcher(
    private val data: AppIconRequest,
    private val options: Options
) : Fetcher {

    /**
     * Loads the app icon from PackageManager.
     * 
     * This suspend function runs on a background thread and:
     * 1. Gets PackageManager from the context in options
     * 2. Calls getApplicationIcon() to load the icon
     * 3. Handles NameNotFoundException by returning default icon
     * 4. Wraps the result in a DrawableResult
     * 
     * The 'suspend' keyword means this function can be paused and resumed,
     * allowing it to run asynchronously without blocking threads.
     * 
     * @return FetchResult containing the loaded icon Drawable
     */
    override suspend fun fetch(): FetchResult {
        // Get PackageManager from the context provided in options.
        // This context comes from the ImageLoader and is always valid.
        val pm = options.context.packageManager
        
        // Try to load the app icon. getApplicationIcon() retrieves the
        // icon Drawable from the PackageManager's cache.
        val drawable = try {
            pm.getApplicationIcon(data.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // App not found - may have been uninstalled since we loaded the list.
            // Return the default Android icon instead of crashing.
            pm.defaultActivityIcon
        }

        // Return the result wrapped in DrawableResult.
        // DrawableResult is used when we have a Drawable (not a Bitmap).
        return DrawableResult(
            // The actual icon Drawable
            drawable = drawable,
            
            // isSampled = false means we didn't downsample/resize the image.
            // App icons are already appropriately sized by the system.
            isSampled = false,
            
            // DataSource.MEMORY indicates the icon came from memory (PackageManager cache).
            // This helps Coil manage its own cache correctly.
            // Alternative values: DISK, NETWORK
            dataSource = DataSource.MEMORY
        )
    }

    // ========================================================================
    // FACTORY CLASS
    // ========================================================================
    /**
     * Factory that creates AppIconFetcher instances.
     * 
     * Coil uses a Factory pattern to determine which Fetcher can handle
     * a particular request. When an image request comes in:
     * 
     * 1. Coil iterates through all registered Factories
     * 2. Each Factory checks if it can handle the request type
     * 3. The first matching Factory creates a Fetcher instance
     * 4. Coil calls fetch() on the Fetcher
     * 
     * Our Factory handles AppIconRequest objects. When Coil sees an
     * AppIconRequest, it uses this Factory to create an AppIconFetcher.
     * 
     * Extends: Fetcher.Factory<AppIconRequest>
     * - The generic type parameter tells Coil what data type we handle
     */
    class Factory : Fetcher.Factory<AppIconRequest> {
        
        /**
         * Creates an AppIconFetcher instance for the given request.
         * 
         * This method is called by Coil when:
         * 1. An image request contains an AppIconRequest
         * 2. Our Factory is checked and can handle AppIconRequest
         * 3. Coil calls create() to get a Fetcher instance
         * 
         * @param data The AppIconRequest to load (contains package name)
         * @param options Coil Options with context and request metadata
         * @param imageLoader The ImageLoader instance (not used here but required by interface)
         * @return A new AppIconFetcher configured for this request
         */
        override fun create(
            data: AppIconRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            // Create and return a new AppIconFetcher instance.
            // Each request gets its own Fetcher instance.
            return AppIconFetcher(data, options)
        }
    }
}
