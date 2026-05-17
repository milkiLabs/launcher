package com.milki.launcher.data.icon

import android.graphics.drawable.Drawable

/**
 * Small in-memory cache for shortcut artwork resolved through LauncherApps.
 *
 * Shortcut icons should survive drag/drop recomposition without falling back to
 * the parent app icon for a frame.
 */
object ShortcutIconMemoryCache {

    private const val MAX_ENTRIES = 160

    private val cache = DrawableConstantStateCache(MAX_ENTRIES)

    fun get(key: String): Drawable? = cache.get(key)

    fun preload(key: String, drawable: Drawable) = cache.put(key, drawable)

    fun invalidatePackage(packageName: String) {
        cache.removeWhere { it.startsWith("$packageName/") }
    }

    fun clear() = cache.evictAll()

    fun getOrLoad(
        key: String,
        loader: () -> Drawable?
    ): Drawable? {
        get(key)?.let { return it }

        val loaded = loader() ?: return null
        preload(key, loaded)
        return get(key) ?: loaded
    }
}
