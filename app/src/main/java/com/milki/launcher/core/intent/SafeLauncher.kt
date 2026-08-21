package com.milki.launcher.core.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

private const val SAFE_LAUNCH_TAG = "SafeLaunch"

/**
 * Single safe-launch primitive for starting activities from non-Activity contexts.
 *
 * Attempts each intent in order until one starts successfully. This covers the
 * common "primary intent with fallback" pattern (e.g. package-pinned URL intent
 * falling back to an unpinned one, or a non-browser intent falling back to a
 * plain VIEW) without duplicating try/catch stacks at every call site.
 *
 * @param description Short human-readable description used in log messages.
 * @param intents Ordered candidate intents; the first that launches wins.
 * @param failureMessage Optional toast shown when every intent fails. Ignored
 *                       when [onFailure] is provided.
 * @param onFailure Optional callback invoked with the last failure when every
 *                  intent fails, for callers needing error-specific UX.
 * @return True if any intent was started, false otherwise.
 */
fun Context.launchSafe(
    description: String,
    intents: List<Intent>,
    failureMessage: String? = null,
    onFailure: ((Throwable) -> Unit)? = null
): Boolean {
    var lastError: Throwable? = null
    for (intent in intents) {
        try {
            startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(SAFE_LAUNCH_TAG, "No activity found for $description", e)
            lastError = e
        } catch (e: SecurityException) {
            Log.w(SAFE_LAUNCH_TAG, "Security exception while launching $description", e)
            lastError = e
        } catch (e: Exception) {
            Log.w(SAFE_LAUNCH_TAG, "Unexpected error while launching $description", e)
            lastError = e
        }
    }
    val failure = onFailure
    if (failure != null) {
        lastError?.let(failure)
    } else if (failureMessage != null) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
    return false
}

/** Convenience overload for the single-intent case. */
fun Context.launchSafe(
    description: String,
    intent: Intent,
    failureMessage: String? = null,
    onFailure: ((Throwable) -> Unit)? = null
): Boolean = launchSafe(description, listOf(intent), failureMessage, onFailure)
