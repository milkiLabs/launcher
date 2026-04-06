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

/**
 * Best-effort disk snapshot cache for app icons used after process restarts.
 */
object AppIconDiskSnapshotStore {

    private const val TAG = "AppIconDiskCache"
    private const val CACHE_DIR_NAME = "app_icon_snapshots"
    private const val DEFAULT_BITMAP_SIZE_PX = 192
    private val INVALID_FILE_KEY_CHARS = Regex("[^A-Za-z0-9._-]")

    private val lock = Any()
    private var cacheDir: File? = null
    @Volatile
    private var appResources: Resources? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            val directory = File(context.cacheDir, CACHE_DIR_NAME)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            cacheDir = directory
            appResources = context.resources
        }
    }

    fun load(packageName: String, packageManager: PackageManager): Drawable? {
        val directory = cacheDir ?: return null
        val cacheKey = buildCacheKey(packageName, packageManager) ?: return null
        val snapshotFile = File(directory, "$cacheKey.png")

        if (!snapshotFile.exists()) {
            return null
        }

        val bitmap = BitmapFactory.decodeFile(snapshotFile.absolutePath)
            ?: run {
                snapshotFile.delete()
                return null
            }

        return BitmapDrawable(appResources ?: Resources.getSystem(), bitmap)
    }

    fun save(
        packageName: String,
        packageManager: PackageManager,
        drawable: Drawable
    ) {
        val directory = cacheDir ?: return
        val cacheKey = buildCacheKey(packageName, packageManager) ?: return
        val snapshotFile = File(directory, "$cacheKey.png")

        if (snapshotFile.exists()) {
            return
        }

        pruneObsoleteSnapshotsForPackage(directory, packageName, snapshotFile.name)

        val bitmap = drawable.toBitmapOrNull() ?: return

        runCatching {
            FileOutputStream(snapshotFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }
        }.onFailure { exception ->
            snapshotFile.delete()
            Log.w(TAG, "Failed to save icon snapshot for $packageName", exception)
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
        val densityDpi =
            (appResources ?: Resources.getSystem()).displayMetrics.densityDpi
        val normalizedPackageName = packageName.replace(INVALID_FILE_KEY_CHARS, "_")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return "${normalizedPackageName}_${versionCode}_${packageInfo.lastUpdateTime}_${densityDpi}"
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
}
