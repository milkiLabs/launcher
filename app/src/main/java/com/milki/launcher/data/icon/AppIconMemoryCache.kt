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
import android.os.SystemClock
import android.graphics.drawable.Drawable
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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

    private const val TAG = "AppIconMemoryCache"

    private const val MAX_GENERAL_ENTRIES = 300
    private const val MAX_HOME_PRIORITY_ENTRIES = 120

    private const val SLOW_SINGLE_LOAD_MS = 24L
    private const val SLOW_PRELOAD_BATCH_MS = 120L
    private const val HIT_RATE_LOG_INTERVAL = 200L

    private val generalIconCache = DrawableConstantStateCache(MAX_GENERAL_ENTRIES)
    private val homePriorityIconCache = DrawableConstantStateCache(MAX_HOME_PRIORITY_ENTRIES)

    private val homePriorityPackages = linkedSetOf<String>()

    private val requestCount = AtomicLong(0)
    private val hitCount = AtomicLong(0)

    private data class LoadResult(
        val drawable: Drawable,
        val shouldPersistToDisk: Boolean
    )

    /**
     * Returns a fresh Drawable from cache if present, otherwise null.
     *
     * @param packageName Package name used as cache key.
     * @return New drawable instance from cached ConstantState, or null on cache miss.
     */
    fun get(packageName: String): Drawable? {
        return homePriorityIconCache.get(packageName)
            ?: generalIconCache.get(packageName)
    }

    fun contains(packageName: String): Boolean {
        return homePriorityIconCache.contains(packageName) ||
            generalIconCache.contains(packageName)
    }

    /**
     * Updates which package names are treated as home-priority cache entries.
     *
     * Existing cached icons are promoted/demoted between tiers immediately.
     */
    fun updateHomePriorityPackages(packageNames: Set<String>) {
        if (homePriorityPackages == packageNames) return

        val removed = homePriorityPackages - packageNames
        removed.forEach { packageName ->
            homePriorityIconCache.get(packageName)?.let { drawable ->
                homePriorityIconCache.remove(packageName)
                generalIconCache.put(packageName, drawable)
            }
        }

        val added = packageNames - homePriorityPackages
        added.forEach { packageName ->
            generalIconCache.get(packageName)?.let { drawable ->
                generalIconCache.remove(packageName)
                homePriorityIconCache.put(packageName, drawable)
            }
        }

        homePriorityPackages.clear()
        homePriorityPackages.addAll(packageNames)
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
        if (packageName in homePriorityPackages) {
            homePriorityIconCache.put(packageName, icon)
            generalIconCache.remove(packageName)
        } else {
            generalIconCache.put(packageName, icon)
        }
    }

    fun invalidatePackage(packageName: String) {
        generalIconCache.remove(packageName)
        homePriorityIconCache.remove(packageName)
    }

    fun clear() {
        generalIconCache.evictAll()
        homePriorityIconCache.evictAll()
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
        val startedAt = SystemClock.elapsedRealtime()

        val loadResult = resolveIcon(
            packageName = packageName,
            packageManager = packageManager
        )

        if (loadResult.shouldPersistToDisk) {
            AppIconDiskSnapshotStore.save(
                packageName = packageName,
                packageManager = packageManager,
                drawable = loadResult.drawable
            )
        }

        preload(packageName = packageName, icon = loadResult.drawable)

        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (elapsedMs >= SLOW_SINGLE_LOAD_MS) {
            Log.w(TAG, "Slow icon load for $packageName: ${elapsedMs}ms")
        }

        return loadResult.drawable
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
        val cached = get(packageName)
        if (cached != null) {
            recordRequest(wasHit = true)
            return cached
        }

        recordRequest(wasHit = false)
        return loadAndCache(packageName = packageName, packageManager = packageManager)
    }

    /**
     * Preloads only missing package icons; already-cached entries are skipped.
     */
    fun preloadMissing(
        packageNames: Collection<String>,
        packageManager: PackageManager
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        var loadedCount = 0

        packageNames.forEach { packageName ->
            if (!contains(packageName)) {
                loadAndCache(
                    packageName = packageName,
                    packageManager = packageManager
                )
                loadedCount += 1
            }
        }

        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (loadedCount > 0 && elapsedMs >= SLOW_PRELOAD_BATCH_MS) {
            Log.w(
                TAG,
                "Slow icon preload batch: ${elapsedMs}ms for $loadedCount packages"
            )
        }
    }

    private fun resolveIcon(
        packageName: String,
        packageManager: PackageManager
    ): LoadResult {
        AppIconDiskSnapshotStore.load(
            packageName = packageName,
            packageManager = packageManager
        )?.let { diskSnapshot ->
            return LoadResult(
                drawable = diskSnapshot,
                shouldPersistToDisk = false
            )
        }

        return try {
            LoadResult(
                drawable = packageManager.getApplicationIcon(packageName),
                shouldPersistToDisk = true
            )
        } catch (exception: PackageManager.NameNotFoundException) {
            LoadResult(
                drawable = packageManager.defaultActivityIcon,
                shouldPersistToDisk = false
            )
        }
    }

    private fun recordRequest(wasHit: Boolean) {
        val totalRequests = requestCount.incrementAndGet()
        if (wasHit) {
            hitCount.incrementAndGet()
        }

        if (totalRequests % HIT_RATE_LOG_INTERVAL != 0L) {
            return
        }

        val currentHits = hitCount.get()
        val hitRatePercent = if (totalRequests == 0L) {
            0.0
        } else {
            (currentHits.toDouble() * 100.0) / totalRequests.toDouble()
        }

        Log.d(
            TAG,
            String.format(
                Locale.US,
                "Icon cache hit rate %.1f%% (%d/%d)",
                hitRatePercent,
                currentHits,
                totalRequests
            )
        )
    }
}
