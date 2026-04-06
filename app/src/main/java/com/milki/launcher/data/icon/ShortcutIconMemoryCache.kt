package com.milki.launcher.data.icon

import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Small in-memory cache for shortcut artwork resolved through LauncherApps.
 *
 * Shortcut icons should survive drag/drop recomposition without falling back to
 * the parent app icon for a frame.
 */
object ShortcutIconMemoryCache {

    private const val MAX_ENTRIES = 160

    private val cache = LruCache<String, Drawable.ConstantState?>(MAX_ENTRIES)
    private val cacheLock = Any()

    fun get(key: String): Drawable? {
        val constantState = synchronized(cacheLock) {
            cache.get(key)
        }
        return constantState?.newDrawable()?.mutate()
    }

    fun preload(key: String, drawable: Drawable) {
        val constantState = drawable.constantState ?: return
        synchronized(cacheLock) {
            cache.put(key, constantState)
        }
    }

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
