package com.milki.launcher.app.activity

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Dedicated coordinator bridging an [androidx.activity.result.ActivityResultLauncher]
 * into a suspending call.
 *
 * Registers against the host's [ActivityResultRegistry] under a stable [key]
 * (surviving configuration changes) so callers get a plain
 * `suspend fun launchForResult(intent): Boolean` instead of hand-rolled
 * pending-callback plumbing at each call site.
 */
class SuspendActivityResultLauncher(
    registry: ActivityResultRegistry,
    key: String
) {
    private var pendingResult: ((Boolean) -> Unit)? = null

    private val launcher = registry.register(
        key,
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val deliver = pendingResult
        pendingResult = null
        deliver?.invoke(result.resultCode == RESULT_OK)
    }

    /**
     * Launches [intent] and suspends until a result arrives. Resumes with
     * `false` if launching fails or the awaiting coroutine is cancelled.
     */
    suspend fun launchForResult(intent: Intent): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingResult = { granted ->
                if (continuation.isActive) {
                    continuation.resume(granted)
                }
            }

            runCatching {
                launcher.launch(intent)
            }.onFailure {
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
