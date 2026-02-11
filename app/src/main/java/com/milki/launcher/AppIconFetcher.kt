package com.milki.launcher

import android.content.Context
import android.content.pm.PackageManager
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * Wrapper class to identify app icon requests
 */
data class AppIconRequest(val packageName: String)

/**
 * Custom Coil Fetcher that loads app icons from package names.
 * Enables lazy loading with automatic LRU caching.
 */
class AppIconFetcher(
    private val data: AppIconRequest,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val pm = options.context.packageManager
        val drawable = try {
            pm.getApplicationIcon(data.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // Return default icon if app not found
            pm.defaultActivityIcon
        }

        return DrawableResult(
            drawable = drawable,
            isSampled = false,
            dataSource = DataSource.MEMORY // Icons loaded from PackageManager, not disk
        )
    }

    class Factory : Fetcher.Factory<AppIconRequest> {
        override fun create(
            data: AppIconRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return AppIconFetcher(data, options)
        }
    }
}
