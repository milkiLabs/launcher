/**
 * AppIconMemoryCache.kt - Launcher-focused in-memory cache for application icons
 *
 * WHY THIS FILE EXISTS:
 * A launcher must render app icons immediately. Even small per-item async overhead
 * can become visible when a grid/list first appears. This cache provides a direct,
 * lightweight path to serve Drawables from memory with minimal work on the UI thread.
 *
 * DESIGN GOALS:
 * 1. Keep the API simple and explicit (get, preload, loadAndCache).
 * 2. Avoid duplicate icon loads when many composables request the same package.
 * 3. Stay safe for multi-threaded access from repository loading + UI fallback loads.
 * 4. Avoid third-party image-pipeline overhead for local PackageManager icons.
 *
 * IMPORTANT IMPLEMENTATION DETAIL:
 * We store Drawable.ConstantState instead of Drawable instances. A Drawable object
 * is stateful and can be mutated by callers. ConstantState lets us create a fresh
 * Drawable instance on every read while still sharing underlying icon resources.
 */

package com.milki.launcher.data.icon

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Thread-safe process-wide cache for app icons.
 *
 * CAPACITY STRATEGY:
 * - The cache size is based on icon count, not memory bytes.
 * - A launcher usually displays a few hundred apps at most.
 * - 300 entries is a practical default for typical devices and prevents
 *   unbounded growth while still covering most app drawers fully.
 */
object AppIconMemoryCache {

    /**
     * Maximum number of package entries stored in memory.
     */
    private const val MAX_ENTRIES = 300

    /**
     * Internal LRU cache keyed by package name.
     *
     * Value is Drawable.ConstantState? because some Drawables might not expose
     * a constant state. In those cases we simply skip caching for that icon.
     */
    private val iconStateCache = LruCache<String, Drawable.ConstantState?>(MAX_ENTRIES)

    /**
     * Lock object used to synchronize all cache access.
     *
     * LruCache itself is not documented as thread-safe for concurrent read/write,
     * so we enforce synchronization at this wrapper boundary.
     */
    private val cacheLock = Any()

    /**
     * Returns a fresh Drawable from cache if present, otherwise null.
     *
     * @param packageName Package name used as cache key.
     * @return New drawable instance from cached ConstantState, or null on cache miss.
     */
    fun get(packageName: String): Drawable? {
        val constantState = synchronized(cacheLock) {
            iconStateCache.get(packageName)
        }
        return constantState?.newDrawable()?.mutate()
    }

    /**
     * Preloads an icon into the cache when a caller already has a Drawable.
     *
     * This is used by repository loading code so UI can hit memory instantly.
     *
     * @param packageName Package name used as cache key.
     * @param icon Drawable to cache.
     */
    fun preload(packageName: String, icon: Drawable) {
        val constantState = icon.constantState ?: return
        synchronized(cacheLock) {
            iconStateCache.put(packageName, constantState)
        }
    }

    /**
     * Loads icon from PackageManager and stores it in cache.
     *
     * This method should be called from a background thread when possible.
     * It is intentionally tiny so callers can control threading policy.
     *
     * @param packageName Package name whose icon should be loaded.
     * @param packageManager Android PackageManager.
     * @return Loaded icon drawable, or default activity icon when package is missing.
     */
    fun loadAndCache(
        packageName: String,
        packageManager: PackageManager
    ): Drawable {
        val loadedIcon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (exception: PackageManager.NameNotFoundException) {
            packageManager.defaultActivityIcon
        }

        preload(packageName = packageName, icon = loadedIcon)
        return loadedIcon
    }

    /**
     * Returns an icon using cache-first lookup.
     *
     * Fast path:
     * 1. Try cache hit and return immediately.
     * Slow path:
     * 2. Load from PackageManager, cache, then return.
     *
     * @param packageName Package name whose icon is requested.
     * @param packageManager Android PackageManager.
     * @return Icon drawable.
     */
    fun getOrLoad(
        packageName: String,
        packageManager: PackageManager
    ): Drawable {
        return get(packageName) ?: loadAndCache(
            packageName = packageName,
            packageManager = packageManager
        )
    }
}
