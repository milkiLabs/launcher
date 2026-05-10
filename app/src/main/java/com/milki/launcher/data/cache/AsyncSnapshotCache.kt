package com.milki.launcher.data.cache

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shared async cache pattern for expensive process-wide snapshots.
 *
 * Guarantees:
 * - synchronous peek for UI fast paths
 * - at most one active load per cache
 * - invalidation cancels stale work
 * - stale loads cannot publish after a newer invalidation
 */
class AsyncSnapshotCache<T : Any>(
    private val scope: CoroutineScope,
    private val tag: String,
    private val emptySnapshot: T,
    private val loader: suspend () -> T
) {
    private var snapshot: T? = null
    private var activeLoad: Deferred<T>? = null
    private var version: Long = 0L

    fun peek(): T? = synchronized(this) {
        snapshot
    }

    fun prewarm() {
        getOrStartLoad()
    }

    suspend fun await(): T {
        val cached = synchronized(this) { snapshot }
        if (cached != null) return cached

        return getOrStartLoad().await()
    }

    fun invalidate(prewarmAfterInvalidation: Boolean = false) {
        val loadToCancel = synchronized(this) {
            version += 1L
            snapshot = null
            activeLoad.also {
                activeLoad = null
            }
        }

        loadToCancel?.cancel()

        if (prewarmAfterInvalidation) {
            prewarm()
        }
    }

    private fun getOrStartLoad(): Deferred<T> =
        synchronized(this) {
            snapshot?.let { cached ->
                return@synchronized CompletableDeferred(cached)
            }

            activeLoad?.let { existingLoad ->
                return@synchronized existingLoad
            }

            val loadVersion = version
            scope.async {
                try {
                    loader()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    Log.e(tag, "Failed to build cached snapshot", throwable)
                    emptySnapshot
                }
            }.also { load ->
                activeLoad = load
                scope.launch {
                    try {
                        val loadedSnapshot = load.await()
                        synchronized(this@AsyncSnapshotCache) {
                            if (activeLoad === load && version == loadVersion) {
                                snapshot = loadedSnapshot
                            }
                        }
                    } finally {
                        synchronized(this@AsyncSnapshotCache) {
                            if (activeLoad === load) {
                                activeLoad = null
                            }
                        }
                    }
                }
            }
        }
}
