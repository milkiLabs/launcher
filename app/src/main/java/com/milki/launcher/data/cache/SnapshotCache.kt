package com.milki.launcher.data.cache

/**
 * Tiny thread-safe holder for immutable cache snapshots.
 *
 * Cache owners still define what the snapshot means and how it is loaded; this
 * class keeps the read/replace/clear mechanics consistent across features.
 */
class SnapshotCache<T : Any>(
    private val emptySnapshot: T
) {
    @Volatile
    private var snapshot: T = emptySnapshot

    fun get(): T = snapshot

    fun replace(value: T) {
        snapshot = value
    }

    fun clear() {
        snapshot = emptySnapshot
    }
}
