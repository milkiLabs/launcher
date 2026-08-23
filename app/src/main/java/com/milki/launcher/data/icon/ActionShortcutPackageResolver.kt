package com.milki.launcher.data.icon

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cache resolving an action shortcut's destination URI to the
 * package that would handle it (the same query LauncherSettings uses when
 * launching).
 *
 * WHY THIS EXISTS:
 * `PackageManager.resolveActivity` is binder IPC and must never run on the
 * main thread during composition. Icon cells (home grid, folder previews,
 * shortcut manager, trigger picker) render many of these at once, so results
 * are resolved off-thread once and reused everywhere.
 *
 * Negative outcomes (no handler found, resolver threw) are cached as well so
 * unresolvable URIs do not re-trigger IPC on every recomposition.
 *
 * Entries are keyed by destinationUri; the empty string sentinel encodes a
 * cached negative result because ConcurrentHashMap rejects null values.
 */
object ActionShortcutPackageResolver {

    private val cache = ConcurrentHashMap<String, String>()

    fun getCached(destinationUri: String): String? {
        return cache[destinationUri]?.takeIf { it.isNotEmpty() }
    }

    fun getOrLoad(
        packageManager: PackageManager,
        destinationUri: String
    ): String? {
        cache[destinationUri]?.let { return it.takeIf { value -> value.isNotEmpty() } }

        val resolved = resolve(packageManager, destinationUri) ?: ""
        cache[destinationUri] = resolved
        return resolved.takeIf { it.isNotEmpty() }
    }

    fun clear() = cache.clear()

    private fun resolve(
        packageManager: PackageManager,
        destinationUri: String
    ): String? {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destinationUri))
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolveInfo?.activityInfo?.packageName
        }.getOrNull()
    }
}
