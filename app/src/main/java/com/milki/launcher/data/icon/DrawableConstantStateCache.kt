package com.milki.launcher.data.icon

import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Thread-safe LRU cache for [Drawable.ConstantState] values.
 *
 * Stores constant states rather than Drawable instances so callers receive
 * a fresh mutable Drawable on every [get] call.
 *
 * @param maxSize maximum number of entries.
 */
internal class DrawableConstantStateCache(
    private val maxSize: Int
) {
    private val cache = LruCache<String, Drawable.ConstantState>(maxSize)
    private val lock = Any()

    /**
     * Returns a fresh Drawable from cached ConstantState, or null on miss.
     */
    fun get(key: String): Drawable? {
        val state = synchronized(lock) { cache.get(key) }
        return state?.newDrawable()?.mutate()
    }

    /**
     * Returns true when a ConstantState already exists for the key.
     */
    fun contains(key: String): Boolean =
        synchronized(lock) { cache.get(key) != null }

    /**
     * Caches the drawable's ConstantState. No-op if constantState is null.
     */
    fun put(key: String, drawable: Drawable) {
        val state = drawable.constantState ?: return
        synchronized(lock) { cache.put(key, state) }
    }

    /**
     * Removes a single entry.
     */
    fun remove(key: String) {
        synchronized(lock) { cache.remove(key) }
    }

    /**
     * Removes all entries whose keys match the predicate.
     */
    fun removeWhere(predicate: (String) -> Boolean) {
        synchronized(lock) {
            cache.snapshot().keys.filter(predicate).forEach { cache.remove(it) }
        }
    }

    /**
     * Evicts all entries.
     */
    fun evictAll() {
        synchronized(lock) { cache.evictAll() }
    }

    /**
     * Returns a snapshot of current keys.
     */
    fun keys(): Set<String> =
        synchronized(lock) { cache.snapshot().keys }
}
