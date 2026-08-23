package com.milki.launcher.data.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import com.milki.launcher.data.cache.SnapshotCache
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.domain.model.UrlHandlerApp
import com.milki.launcher.domain.search.UrlHandlerPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val URL_HANDLER_RESOLVER_TAG = "UrlHandlerResolver"

class UrlHandlerResolver(
    private val context: Context,
    packageChangeMonitor: PackageChangeMonitor,
    applicationScope: CoroutineScope
) : UrlHandlerPort {

    private val packageManager: PackageManager = context.packageManager

    private val scope = applicationScope
    private val browserPackagesCache = SnapshotCache(BrowserPackagesSnapshot.Empty)
    private val handlerAppCache = LruCache<String, UrlHandlerApp>(HANDLER_CACHE_SIZE)

    init {
        // Warm the browser set off the main thread so isBrowserPackage never
        // pays for a MATCH_ALL PackageManager query on the caller.
        scope.launch { refreshBrowserPackages() }
        scope.launch {
            packageChangeMonitor.events.collectLatest { event ->
                if (event.packageName != null) {
                    invalidatePackage(event.packageName)
                } else {
                    handlerAppCache.evictAll()
                }
                refreshBrowserPackages()
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

    override fun resolveNonBrowserUrlHandler(url: String): UrlHandlerApp? {
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

        return packageName in browserPackages.packageNames
    }

    private fun refreshBrowserPackages() {
        val packages = runCatching { getDynamicBrowserPackages() }
            .onFailure { throwable ->
                Log.w(URL_HANDLER_RESOLVER_TAG, "Failed to query browser packages", throwable)
            }
            .getOrDefault(emptySet())
        browserPackagesCache.replace(
            BrowserPackagesSnapshot(isLoaded = true, packageNames = packages)
        )
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
            handlerAppCache.get(cacheKey)?.let { cachedHandler ->
                return@runCatching cachedHandler
            }

            val label = resolveInfo.loadLabel(packageManager).toString()

            val handlerApp = UrlHandlerApp(
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                label = label,
                isDefault = false
            )

            handlerAppCache.put(cacheKey, handlerApp)
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
        val prefix = "$packageName/"
        handlerAppCache.snapshot().keys
            .filter { it.startsWith(prefix) }
            .forEach(handlerAppCache::remove)
    }

    private fun handlerCacheKey(packageName: String, activityName: String): String {
        return "$packageName/$activityName"
    }

    private companion object {
        const val HANDLER_CACHE_SIZE = 128
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
