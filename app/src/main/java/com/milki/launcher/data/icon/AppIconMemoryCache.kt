/**
 * AppIconMemoryCache.kt - Launcher-focused in-memory cache for application icons
 *
 * WHY THIS FILE EXISTS:
 * A launcher must render app icons immediately. Even small per-item async overhead
 * can become visible when a grid/list first appears. This cache provides a direct,
 * lightweight path to serve Drawables from memory with minimal work on the UI thread.
 *
 * DESIGN GOALS:
 * 1. Keep the API simple and explicit: synchronous reads for cache hits,
 *    suspending slow paths that own their threading.
 * 2. Avoid duplicate icon loads when many composables request the same package.
 * 3. Stay safe for multi-threaded access from repository loading + UI fallback loads.
 * 4. Avoid third-party image-pipeline overhead for local PackageManager icons.
 *
 * THREADING CONTRACT:
 * - [get] and [contains] are pure in-memory reads, safe from any thread
 *   (including the UI thread) at any time.
 * - [getOrLoad], [loadAndCache], and [preloadMissing] may hit PackageManager
 *   and the disk snapshot store. They are suspending functions that internally
 *   shift work onto [ioDispatcher], so callers cannot accidentally block the
 *   main thread by calling them.
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
import com.milki.launcher.domain.icon.IconPriorityStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thread-safe process-wide cache for app icons.
 *
 * CAPACITY STRATEGY:
 * - The cache size is based on icon count, not memory bytes.
 * - A launcher usually displays a few hundred apps at most.
 * - 300 entries is a practical default for typical devices and prevents
 *   unbounded growth while still covering most app drawers fully.
 */
class AppIconMemoryCache(
    private val diskSnapshotStore: AppIconDiskSnapshotStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val metrics: IconCacheMetrics = IconCacheMetrics()
) : IconPriorityStore {

    private val generalIconCache = DrawableConstantStateCache(MAX_GENERAL_ENTRIES)
    private val homePriorityIconCache = DrawableConstantStateCache(MAX_HOME_PRIORITY_ENTRIES)

    /**
     * Guards [homePriorityPackages]. All reads and writes of the set must hold
     * this lock; tier moves below read it under the lock and then perform
     * lock-free cache operations.
     */
    private val priorityLock = Any()
    private val homePriorityPackages = linkedSetOf<String>()

    private data class LoadResult(
        val drawable: Drawable,
        val shouldPersistToDisk: Boolean
    )

    private companion object {
        const val MAX_GENERAL_ENTRIES = 300
        const val MAX_HOME_PRIORITY_ENTRIES = 120
    }

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
     * Tier moves use raw ConstantState removal so LRU recency of surviving
     * entries is untouched.
     */
    override fun updateHomePriorityPackages(packageNames: Set<String>) {
        val removed: Set<String>
        val added: Set<String>
        synchronized(priorityLock) {
            if (homePriorityPackages == packageNames) return

            removed = LinkedHashSet(homePriorityPackages - packageNames)
            added = LinkedHashSet(packageNames - homePriorityPackages)

            homePriorityPackages.clear()
            homePriorityPackages.addAll(packageNames)
        }

        removed.forEach { packageName ->
            homePriorityIconCache.removeState(packageName)?.let { state ->
                generalIconCache.putState(packageName, state)
            }
        }

        added.forEach { packageName ->
            generalIconCache.removeState(packageName)?.let { state ->
                homePriorityIconCache.putState(packageName, state)
            }
        }
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
        val isHomePriority = synchronized(priorityLock) {
            packageName in homePriorityPackages
        }
        if (isHomePriority) {
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
     * Suspends while shifting PackageManager/disk work onto [ioDispatcher];
     * safe to call from any thread or dispatcher.
     *
     * @param packageName Package name whose icon should be loaded.
     * @param packageManager Android PackageManager.
     * @return Loaded icon drawable, or default activity icon when package is missing.
     */
    suspend fun loadAndCache(
        packageName: String,
        packageManager: PackageManager
    ): Drawable {
        return withContext(ioDispatcher) {
            val startedAt = SystemClock.elapsedRealtime()
            val drawable = resolveAndCache(packageName, packageManager)

            metrics.recordSingleLoadDuration(
                packageName = packageName,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
            )

            drawable
        }
    }

    /**
     * Returns an icon using cache-first lookup.
     *
     * Fast path (cache hit) resolves synchronously; only misses suspend on IO.
     *
     * @param packageName Package name whose icon should be loaded.
     * @param packageManager Android PackageManager.
     * @return Icon drawable.
     */
    suspend fun getOrLoad(
        packageName: String,
        packageManager: PackageManager
    ): Drawable {
        val cached = get(packageName)
        if (cached != null) {
            metrics.recordRequest(wasHit = true)
            return cached
        }

        metrics.recordRequest(wasHit = false)
        return loadAndCache(packageName = packageName, packageManager = packageManager)
    }

    /**
     * Preloads only missing package icons; already-cached entries are skipped.
     */
    suspend fun preloadMissing(
        packageNames: Collection<String>,
        packageManager: PackageManager
    ) {
        withContext(ioDispatcher) {
            val startedAt = SystemClock.elapsedRealtime()
            var loadedCount = 0

            packageNames.forEach { packageName ->
                if (!contains(packageName)) {
                    resolveAndCache(packageName, packageManager)
                    loadedCount += 1
                }
            }

            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            metrics.recordPreloadBatchDuration(
                elapsedMs = elapsedMs,
                loadedCount = loadedCount
            )
        }
    }

    private suspend fun resolveAndCache(
        packageName: String,
        packageManager: PackageManager
    ): Drawable {
        val loadResult = resolveIcon(
            packageName = packageName,
            packageManager = packageManager
        )

        if (loadResult.shouldPersistToDisk) {
            diskSnapshotStore.save(
                packageName = packageName,
                packageManager = packageManager,
                drawable = loadResult.drawable
            )
        }

        preload(packageName = packageName, icon = loadResult.drawable)

        return loadResult.drawable
    }

    private suspend fun resolveIcon(
        packageName: String,
        packageManager: PackageManager
    ): LoadResult {
        diskSnapshotStore.load(
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
}
