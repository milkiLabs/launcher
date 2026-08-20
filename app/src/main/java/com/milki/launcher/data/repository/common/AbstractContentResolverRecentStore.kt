package com.milki.launcher.data.repository.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Base class for content-resolver-backed repositories that keep a "recent items" store
 * alongside their query access (contacts, files).
 *
 * Shared plumbing folded in from the two repository implementations that previously
 * duplicated it:
 *  - the [Context] is cached in the base constructor and used to resolve [ContentResolver]
 *    once;
 *  - queries run on [Dispatchers.IO];
 *  - recent-item storage is a single shared [RecentListStorage] configured with the
 *    type's encode/decode lambdas.
 *
 * The inline permission gate has *identical* semantics for every query across subclasses:
 * when [hasPermission] returns false, the query logs a warning and returns an empty result.
 * A missing permission is never surfaced as a [SecurityException] from a repository method
 * (see [withPermissionOr]).
 */
abstract class AbstractContentResolverRecentStore<T>(
    context: Context,
) {
    protected val appContext: Context = context.applicationContext
    protected val contentResolver: android.content.ContentResolver = appContext.contentResolver

    /**
     * The recent-item list for [T], configured once (data store, preference key, max size,
     * and encode/decode lambdas).
     */
    protected abstract val recentStore: RecentListStorage<T>

    /** Returns true when the backing permission for the source (contacts/files) is granted. */
    protected abstract fun hasPermission(): Boolean

    /** Saves [item] to the recent list. Local DataStore write; requires no permission. */
    protected suspend fun saveRecent(item: T) {
        recentStore.saveRecent(item)
    }

    /** Stream of recent saved items, most recent first, trimmed to the configured size. */
    protected fun observeRecent(): Flow<List<T>> = recentStore.observeRecent()

    /**
     * Runs [whenGranted] when [hasPermission] returns true, otherwise logs a warning and
     * runs [whenDenied].
     *
     * The granted block executes on [Dispatchers.IO]; the result is mapped back on the
     * caller's context. Cancellation is always honored before and after the query.
     */
    protected suspend fun <O> withPermissionOr(
        whenGranted: suspend () -> O?,
        whenDenied: () -> O,
    ): O? {
        return if (hasPermission()) {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                whenGranted()
            }
        } else {
            Log.w(TAG, "Repository query called without permission; returning empty result")
            whenDenied()
        }
    }

    private companion object {
        const val TAG = "AbstractContentResolverRecentStore"
    }
}