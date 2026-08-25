package com.milki.launcher.core.crash

import android.content.Context
import android.os.Build
import com.milki.launcher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes crash reports to the app-specific external storage directory:
 * `/storage/emulated/0/Android/data/com.milki.launcher/files/crash-logs/`
 *
 * All operations are fail-safe by design: a crash handler must never throw.
 */
class CrashLogWriter(
    private val context: Context
) {

    fun writeCrashReport(thread: Thread, throwable: Throwable): File? {
        val timestamp = System.currentTimeMillis()
        val message = buildString {
            appendLine(HEADER_DIVIDER)
            appendLine("Milki Launcher crash report")
            appendLine("Timestamp      : ${formatTimestamp(timestamp)}")
            appendLine("Thread         : ${thread.name} (id=${thread.id})")
            appendDeviceSection(this)
            appendStackTrace(throwable, this)
        }
        return persist(message, timestamp)
    }

    /**
     * Records non-fatal errors so recurring issues can be diagnosed even
     * when they never escalate into an actual crash.
     */
    fun logNonFatal(tag: String, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val message = buildString {
            appendLine(HEADER_DIVIDER)
            appendLine("Non-fatal error")
            appendLine("Timestamp      : ${formatTimestamp(timestamp)}")
            appendLine("Tag            : $tag")
            appendDeviceSection(this)
            appendStackTrace(throwable, this)
        }
        persist(message, timestamp)
    }

    /** Returns all stored reports, newest first. */
    fun listReports(): List<File> =
        logDirectory()?.listFiles { file -> file.isFile && file.getName().endsWith(".txt") }
            ?.sortedByDescending { it.getName() }
            .orEmpty()

    /**
     * Concatenates every stored report into one shareable plain-text payload,
     * newest first. Returns an empty string when nothing was ever recorded.
     */
    fun buildShareableText(): String =
        listReports().joinToString(separator = "\n\n") { report ->
            runCatching {
                report.readText()
            }.getOrElse {
                "[unreadable report: ${report.getName()}]"
            }
        }

    private fun persist(content: String, timestamp: Long): File? = try {
        val directory = resolveDirectory() ?: return null
        val file = File(directory, "crash-${fileSafeTimestamp(timestamp)}.txt")
        file.writeText(content)
        rotate(directory)
        file
    } catch (_: Exception) {
        // Never propagate: we are likely mid-crash here.
        null
    }

    /**
     * Prefers external (user-accessible) storage; falls back to internal only
     * if external storage is unavailable, which is extremely rare.
     */
    private fun resolveDirectory(): File? {
        logDirectory()?.let { return it }
        return File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }.takeIf { it.isDirectory }
    }

    private fun logDirectory(): File? =
        context.getExternalFilesDir(null)?.let { File(it, DIRECTORY_NAME) }
            ?.apply { mkdirs() }
            ?.takeIf { it.isDirectory }

    /** Keeps only the [MAX_REPORTS] most recent reports. */
    private fun rotate(directory: File) {
        listReports().drop(MAX_REPORTS).forEach { report ->
            runCatching { report.delete() }
        }
    }

    private fun appendDeviceSection(builder: StringBuilder) {
        builder.appendLine(HEADER_DIVIDER)
        builder.appendLine("App version    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        builder.appendLine("Build type     : ${BuildConfig.BUILD_TYPE}")
        builder.appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        builder.appendLine("Device         : ${Build.MANUFACTURER} ${Build.MODEL}")
        builder.appendLine("Hardware       : ${Build.HARDWARE}, abi=${Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"}")
    }

    private fun appendStackTrace(throwable: Throwable, builder: StringBuilder) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        builder.appendLine(HEADER_DIVIDER)
        builder.appendLine("Stack trace:")
        builder.appendLine(writer.toString())
    }

    companion object {
        private const val DIRECTORY_NAME = "crash-logs"
        private const val MAX_REPORTS = 10
        private const val HEADER_DIVIDER =
            "=============================================================="

        private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
        private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        private fun formatTimestamp(timestamp: Long): String = displayDateFormat.format(Date(timestamp))
        private fun fileSafeTimestamp(timestamp: Long): String = fileDateFormat.format(Date(timestamp))
    }
}
