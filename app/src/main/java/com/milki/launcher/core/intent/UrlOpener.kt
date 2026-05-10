package com.milki.launcher.core.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val URL_OPENER_TAG = "UrlOpener"

fun openUrlDestination(
    context: Context,
    url: String,
    preferredPackageName: String? = null,
    onFailure: (() -> Unit)? = null
): Boolean {
    val preferredIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        preferredPackageName?.let(::setPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (tryStartUrlIntent(context, preferredIntent)) {
        return true
    }

    if (preferredPackageName != null) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartUrlIntent(context, fallbackIntent)) {
            return true
        }
    }

    onFailure?.invoke()
    return false
}

private fun tryStartUrlIntent(
    context: Context,
    intent: Intent
): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(URL_OPENER_TAG, "No activity available for URL intent", e)
        false
    } catch (e: SecurityException) {
        Log.w(URL_OPENER_TAG, "Security exception while opening URL", e)
        false
    }
}
