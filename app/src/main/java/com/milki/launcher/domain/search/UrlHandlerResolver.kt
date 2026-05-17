
package com.milki.launcher.domain.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import com.milki.launcher.data.cache.SnapshotCache
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.domain.model.UrlHandlerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val URL_HANDLER_RESOLVER_TAG = "UrlHandlerResolver"

class UrlHandlerResolver(
    private val context: Context,
    packageChangeMonitor: PackageChangeMonitor
) {
        private val packageManager: PackageManager = context.packageManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val browserPackagesCache = SnapshotCache(BrowserPackagesSnapshot.Empty)
    private val handlerAppCache = SnapshotCache<Map<String, UrlHandlerApp>>(emptyMap())

    init {
        scope.launch {
            packageChangeMonitor.events.collectLatest { event ->
                if (event.packageName == null) {
                    browserPackagesCache.clear()
                    handlerAppCache.clear()
                } else {
                    invalidatePackage(event.packageName)
                }
            }
        }
    }

        fun resolveUrlHandler(url: String): UrlHandlerApp? {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

        return runCatching {
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            resolveInfo?.let(::createHandlerApp)
        }.onFailure { throwable ->
            Log.w(URL_HANDLER_RESOLVER_TAG, "Failed to resolve default URL handler for $url", throwable)
        }.getOrNull()
    }

        fun resolveNonBrowserUrlHandler(url: String): UrlHandlerApp? {
        val handler = resolveUrlHandler(url) ?: return null
        return handler.takeUnless { isBrowserPackage(it.packageName) }
    }

    fun resolvePreferredUrlHandler(url: String): UrlHandlerApp? {
        val nonBrowserHandler = resolveNonBrowserUrlHandler(url)
        if (nonBrowserHandler != null) return nonBrowserHandler

        val scheme = Uri.parse(url).scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") {
            resolveDefaultBrowser()
        } else {
            resolveUrlHandler(url)
        }
    }

        fun getAllUrlHandlers(url: String): List<UrlHandlerApp> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

        return runCatching {
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            val defaultHandler = resolveUrlHandler(url)

            resolveInfos
                .filter { it.activityInfo.packageName != context.packageName }
                .mapNotNull { info ->
                    createHandlerApp(info)?.let { handlerApp ->
                        if (handlerApp.id == defaultHandler?.id) {
                            handlerApp.copy(isDefault = true)
                        } else {
                            handlerApp
                        }
                    }
                }
                .sortedByDescending { it.isDefault }
        }.onFailure { throwable ->
            Log.w(URL_HANDLER_RESOLVER_TAG, "Failed to query URL handlers for $url", throwable)
        }.getOrElse { emptyList() }
    }

        fun isDeepLink(url: String): Boolean {
        return resolveNonBrowserUrlHandler(url) != null
    }

        fun resolveDefaultBrowser(): UrlHandlerApp? {
        val genericHttpUrl = "https://example.com"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(genericHttpUrl))

        return runCatching {
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolveInfo?.let { createHandlerApp(it) }
        }.onFailure { throwable ->
            Log.w(URL_HANDLER_RESOLVER_TAG, "Failed to resolve default browser", throwable)
        }.getOrNull()
    }

        private fun isBrowserPackage(packageName: String): Boolean {
        val browserPackages = browserPackagesCache.get().takeIf { it.isLoaded }
            ?: BrowserPackagesSnapshot(
                isLoaded = true,
                packageNames = getDynamicBrowserPackages()
            ).also(browserPackagesCache::replace)

                if (packageName in browserPackages.packageNames) {
            return true
        }

                val knownBrowsers = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser"
        )

        return packageName in knownBrowsers
    }

        private fun getDynamicBrowserPackages(): Set<String> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        
                browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)

                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                browserIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        }

                return resolveInfos.map { it.activityInfo.packageName }.toSet()
    }

        private fun createHandlerApp(resolveInfo: ResolveInfo): UrlHandlerApp? {
        return runCatching {
            val activityInfo = resolveInfo.activityInfo
            val cacheKey = handlerCacheKey(activityInfo.packageName, activityInfo.name)
            handlerAppCache.get()[cacheKey]?.let { cachedHandler ->
                return@runCatching cachedHandler
            }

            val label = resolveInfo.loadLabel(packageManager).toString()

            val handlerApp = UrlHandlerApp(
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                label = label,
                isDefault = false
            )

            handlerAppCache.replace(handlerAppCache.get() + (cacheKey to handlerApp))
            handlerApp
        }.onFailure { throwable ->
            Log.w(
                URL_HANDLER_RESOLVER_TAG,
                "Failed to build handler app for ${resolveInfo.activityInfo.packageName}",
                throwable
            )
        }.getOrNull()
    }

    private fun invalidatePackage(packageName: String) {
        browserPackagesCache.clear()
        handlerAppCache.replace(
            handlerAppCache.get().filterKeys { key ->
                !key.startsWith("$packageName/")
            }
        )
    }

    private fun handlerCacheKey(packageName: String, activityName: String): String {
        return "$packageName/$activityName"
    }
}

private data class BrowserPackagesSnapshot(
    val isLoaded: Boolean,
    val packageNames: Set<String>
) {
    companion object {
        val Empty = BrowserPackagesSnapshot(
            isLoaded = false,
            packageNames = emptySet()
        )
    }
}
