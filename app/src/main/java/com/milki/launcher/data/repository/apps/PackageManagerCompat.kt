package com.milki.launcher.data.repository.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * PackageManager compatibility helpers for API 33+ flag-based methods.
 */
internal fun PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
}

@Throws(PackageManager.NameNotFoundException::class)
internal fun PackageManager.getApplicationInfoCompat(packageName: String): ApplicationInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }
}

@Throws(PackageManager.NameNotFoundException::class)
internal fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
    }
}
