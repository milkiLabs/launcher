package com.milki.launcher.data.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetch-once disk+memory cache for website favicons shown in suggestion pills.
 *
 * CHAIN (per host):
 * 1) Memory Bitmap hit → instant, no I/O.
 * 2) Disk PNG hit → decode, promote to memory.
 * 3) Network (single load): try `https://<host>/favicon.ico`, fall back to
 *    Google S2 PNG (reliably decodable; direct .ico often isn't ICO-decodable
 *    by BitmapFactory). First success is saved to disk+memory.
 * 4) Failure → negative in-memory mark (no disk write), caller renders the
 *    monogram fallback. No retry within the process; next cold start retries
 *    once. This keeps per-keystroke recomposition free of network.
 *
 * THREADING: [getCached] is a pure memory read (UI-thread safe).
 * [getOrLoad] suspends and shifts all I/O onto [ioDispatcher].
 *
 * PRIVACY: requires INTERNET. Direct fetch contacts the site itself (which
 * the user is about to visit anyway); the S2 fallback leaks the host to
 * Google. Both happen only for visible suggestion pills, once per host.
 */
class FaviconCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext = context.applicationContext
    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)

    /** Hosts with a known negative result this process (avoid refetch loops). */
    private val negative = Collections.synchronizedSet(LinkedHashSet<String>())

    private val lock = Any()
    @Volatile
    private var cacheDir: File? = null

    fun getCached(host: String): Bitmap? {
        if (host.isBlank()) return null
        return memory.get(normalizeHost(host))
    }

    suspend fun getOrLoad(host: String): Bitmap? {
        val key = normalizeHost(host)
        if (key.isBlank()) return null
        memory.get(key)?.let { return it }
        // Known failure this process (disk already missed when marked) → no
        // network; caller keeps the monogram. Next cold start retries once.
        if (key in negative) return null

        return withContext(ioDispatcher) {
            memory.get(key)?.let { return@withContext it }

            loadFromDisk(key)?.let { bitmap ->
                memory.put(key, bitmap)
                return@withContext bitmap
            }

            if (key in negative) return@withContext null

            val fetched = fetchHostIcon(key)
            if (fetched != null) {
                memory.put(key, fetched)
                saveToDisk(key, fetched)
            } else {
                negative.add(key)
            }
            fetched
        }
    }

    fun faviconFile(host: String): File? {
        val dir = resolveCacheDir() ?: return null
        return File(dir, "${sanitize(normalizeHost(host))}.png")
    }

    private fun loadFromDisk(key: String): Bitmap? {
        val file = faviconFile(key) ?: return null
        if (!file.exists()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) {
            runCatching { file.delete() }
        }
        return bitmap
    }

    private fun saveToDisk(key: String, bitmap: Bitmap) {
        val dir = resolveCacheDir() ?: return
        val file = File(dir, "${sanitize(key)}.png")
        if (file.exists()) return
        runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                out.flush()
            }
            pruneIfNeeded(dir)
        }.onFailure { error ->
            Log.w(TAG, "Failed to save favicon for $key", error)
            runCatching { file.delete() }
        }
    }

    private fun pruneIfNeeded(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.extension == "png" } ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_FILES)
            .forEach { runCatching { it.delete() } }
    }

    private fun fetchHostIcon(host: String): Bitmap? {
        // Direct first (no third party), S2 fallback (PNG, reliably decodable).
        val candidates = listOf(
            "https://$host/favicon.ico",
            "https://www.google.com/s2/favicons?domain=$host&sz=64"
        )
        for (url in candidates) {
            downloadBitmap(url)?.let { return it }
        }
        return null
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        return runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) return null
                val contentType = connection.contentType?.lowercase().orEmpty()
                if (contentType.contains("text/html")) return null
                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                bitmap?.takeIf { it.width in MIN_ICON_PX..MAX_ICON_PX && it.height in MIN_ICON_PX..MAX_ICON_PX }
            } finally {
                runCatching { connection.disconnect() }
            }
        }.getOrNull()
    }

    private fun resolveCacheDir(): File? {
        cacheDir?.let { return it }
        return synchronized(lock) {
            cacheDir ?: run {
                val directory = File(appContext.cacheDir, CACHE_DIR_NAME)
                if (!directory.exists()) directory.mkdirs()
                if (directory.isDirectory) directory.also { cacheDir = it } else null
            }
        }
    }

    private fun normalizeHost(host: String): String =
        host.trim().lowercase().removePrefix("www.")

    private fun sanitize(host: String): String =
        host.replace(INVALID_FILE_KEY_CHARS, "_").take(MAX_FILE_NAME_LENGTH)

    private companion object {
        const val TAG = "FaviconCache"
        const val CACHE_DIR_NAME = "favicons"
        const val MEMORY_ENTRIES = 64
        const val MAX_DISK_FILES = 128
        const val MAX_FILE_NAME_LENGTH = 96
        const val CONNECT_TIMEOUT_MS = 4000
        const val READ_TIMEOUT_MS = 4000
        const val MIN_ICON_PX = 8
        const val MAX_ICON_PX = 256
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L
        const val PNG_QUALITY = 100
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) MilkiLauncher/1.0"
        val INVALID_FILE_KEY_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
