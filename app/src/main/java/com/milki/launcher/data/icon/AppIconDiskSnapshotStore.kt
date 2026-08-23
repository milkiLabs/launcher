package com.milki.launcher.data.icon

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort disk snapshot cache for app icons used after process restarts.
 *
 * THREADING CONTRACT:
 * [load] and [save] perform disk I/O (file reads/writes, PNG compression,
 * directory pruning). Both are suspending functions that internally shift work
 * onto [ioDispatcher], so callers can never accidentally block the main thread.
 */
class AppIconDiskSnapshotStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext = context.applicationContext

    private val lock = Any()
    @Volatile
    private var cacheDir: File? = null

    private val appResources: Resources
        get() = appContext.resources

    suspend fun load(packageName: String, packageManager: PackageManager): Drawable? {
        return withContext(ioDispatcher) {
            loadBlocking(packageName, packageManager)
        }
    }

    suspend fun save(
        packageName: String,
        packageManager: PackageManager,
        drawable: Drawable
    ) {
        withContext(ioDispatcher) {
            saveBlocking(packageName, packageManager, drawable)
        }
    }

    private fun loadBlocking(packageName: String, packageManager: PackageManager): Drawable? {
        val snapshotFile = buildSnapshotFile(packageName, packageManager)
        val bitmap = snapshotFile
            ?.takeIf(File::exists)
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }

        if (snapshotFile != null && snapshotFile.exists() && bitmap == null) {
            snapshotFile.delete()
        }

        return bitmap?.let { BitmapDrawable(appResources, it) }
    }

    private fun saveBlocking(
        packageName: String,
        packageManager: PackageManager,
        drawable: Drawable
    ) {
        val snapshotDirectory = resolveCacheDir()
        val snapshotFile = buildSnapshotFile(packageName, packageManager)
        val bitmap = drawable.toBitmapOrNull()
        val shouldSaveSnapshot = snapshotFile?.exists() == false && bitmap != null

        if (snapshotDirectory != null && snapshotFile != null && shouldSaveSnapshot) {
            pruneObsoleteSnapshotsForPackage(snapshotDirectory, packageName, snapshotFile.name)

            runCatching {
                FileOutputStream(snapshotFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_COMPRESSION_QUALITY, output)
                    output.flush()
                }
            }.onFailure { exception ->
                snapshotFile.delete()
                Log.w(TAG, "Failed to save icon snapshot for $packageName", exception)
            }
        }
    }

    /**
     * Lazily creates and returns the snapshot directory.
     *
     * Replaces the former eager `initialize(context)` call from Application:
     * the directory is materialized on first use instead of requiring an
     * explicit startup hook whose omission silently disabled the cache.
     */
    private fun resolveCacheDir(): File? {
        cacheDir?.let { return it }
        return synchronized(lock) {
            cacheDir ?: run {
                val directory = File(appContext.cacheDir, CACHE_DIR_NAME)
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                if (!directory.isDirectory) {
                    Log.w(TAG, "Icon snapshot directory unavailable: ${directory.absolutePath}")
                    null
                } else {
                    directory.also { cacheDir = it }
                }
            }
        }
    }

    private fun pruneObsoleteSnapshotsForPackage(
        directory: File,
        packageName: String,
        keepFileName: String
    ) {
        val packagePrefix = packageName.replace(INVALID_FILE_KEY_CHARS, "_") + "_"
        directory.listFiles()?.forEach { file ->
            if (
                file.isFile &&
                file.name != keepFileName &&
                file.name.startsWith(packagePrefix)
            ) {
                file.delete()
            }
        }
    }

    private fun buildCacheKey(
        packageName: String,
        packageManager: PackageManager
    ): String? {
        val packageInfo = readPackageInfo(packageName, packageManager) ?: return null
        val densityDpi = appResources.displayMetrics.densityDpi
        val normalizedPackageName = packageName.replace(INVALID_FILE_KEY_CHARS, "_")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return "${normalizedPackageName}_${versionCode}_${packageInfo.lastUpdateTime}_${densityDpi}"
    }

    private fun buildSnapshotFile(
        packageName: String,
        packageManager: PackageManager
    ): File? {
        val directory = resolveCacheDir()
        val cacheKey = buildCacheKey(packageName, packageManager)

        return if (directory != null && cacheKey != null) {
            File(directory, "$cacheKey.png")
        } else {
            null
        }
    }

    private fun readPackageInfo(
        packageName: String,
        packageManager: PackageManager
    ): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
    }

    private fun Drawable.toBitmapOrNull(): Bitmap? {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }

        val width = intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_BITMAP_SIZE_PX
        val height = intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_BITMAP_SIZE_PX

        return runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        }.getOrNull()
    }

    private companion object {
        const val TAG = "AppIconDiskCache"
        const val CACHE_DIR_NAME = "app_icon_snapshots"
        const val DEFAULT_BITMAP_SIZE_PX = 192
        const val PNG_COMPRESSION_QUALITY = 100
        val INVALID_FILE_KEY_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
