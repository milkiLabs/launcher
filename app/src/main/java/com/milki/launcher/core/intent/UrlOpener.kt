package com.milki.launcher.core.intent

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openUrlDestination(
    context: Context,
    url: String,
    preferredPackageName: String? = null,
    onFailure: (() -> Unit)? = null
): Boolean {
    if (tryStartUrlIntent(context, url, preferredPackageName)) {
        return true
    }

    if (preferredPackageName != null && tryStartUrlIntent(context, url, null)) {
        return true
    }

    onFailure?.invoke()
    return false
}

private fun tryStartUrlIntent(
    context: Context,
    url: String,
    packageName: String?
): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        packageName?.let(::setPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return context.launchSafe("URL destination $url", intent)
}
