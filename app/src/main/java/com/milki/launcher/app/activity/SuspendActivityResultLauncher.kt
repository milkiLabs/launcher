package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dedicated coordinator bridging an [androidx.activity.result.ActivityResultLauncher]
 * into a suspending call.
 *
 * Registers against the host's [ActivityResultRegistry] under a stable [key]
 * (surviving configuration changes) so callers get a plain
 * `suspend fun launchForResult(intent): Boolean` instead of hand-rolled
 * pending-callback plumbing at each call site.
 *
 * Robustness guarantees:
 * - Concurrent invocations are rejected (fail fast with `false`) instead of
 *   silently overwriting the in-flight request.
 * - The await path is bounded by [timeoutMs], so a lost continuation (e.g. the
 *   host activity being recreated while the dialog is up) cannot hang the
 *   caller forever.
 */
class SuspendActivityResultLauncher(
    registry: ActivityResultRegistry,
    private val key: String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    private companion object {
        const val TAG = "SuspendActivityResult"
        const val DEFAULT_TIMEOUT_MS = 120_000L
    }

    private var pendingResult: ((Boolean) -> Unit)? = null
    private val awaitingResult = Mutex()

    private val launcher = registry.register(
        key,
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val deliver = pendingResult
        pendingResult = null
        deliver?.invoke(result.resultCode == RESULT_OK)
            ?: Log.w(TAG, "Activity result arrived with no awaiting caller (key=$key)")
    }

    /**
     * Launches [intent] and suspends until a result arrives. Resumes with
     * `false` if launching fails, the awaiting coroutine is cancelled, another
     * launch is already in flight, or [timeoutMs] elapses without a result.
     */
    suspend fun launchForResult(intent: Intent): Boolean {
        if (!awaitingResult.tryLock()) {
            Log.w(TAG, "Concurrent launchForResult rejected (key=$key)")
            return false
        }

        return try {
            withTimeoutOrNull(timeoutMs) {
                awaitResult(intent)
            } ?: false.also {
                Log.w(TAG, "launchForResult timed out after ${timeoutMs}ms (key=$key)")
            }
        } finally {
            pendingResult = null
            awaitingResult.unlock()
        }
    }

    private suspend fun awaitResult(intent: Intent): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingResult = { granted ->
                if (continuation.isActive) {
                    continuation.resume(granted)
                }
            }

            runCatching {
                launcher.launch(intent)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to launch activity result intent", throwable)
                pendingResult = null
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                pendingResult = null
            }
        }
}
