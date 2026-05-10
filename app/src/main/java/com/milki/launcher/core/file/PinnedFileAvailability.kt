package com.milki.launcher.core.file

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException

enum class ContentUriFailurePolicy {
    TREAT_AS_UNAVAILABLE,
    TREAT_AS_AVAILABLE
}

object PinnedFileAvailability {
    fun isAvailable(
        contentResolver: ContentResolver,
        uriString: String,
        contentUriFailurePolicy: ContentUriFailurePolicy = ContentUriFailurePolicy.TREAT_AS_UNAVAILABLE
    ): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val scheme = uri.scheme ?: return false

        if (scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return false
            return File(path).exists()
        }

        if (!scheme.equals("content", ignoreCase = true)) {
            return false
        }

        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                true
            } ?: false
        } catch (_: FileNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            contentUriFailurePolicy == ContentUriFailurePolicy.TREAT_AS_AVAILABLE
        } catch (_: Exception) {
            contentUriFailurePolicy == ContentUriFailurePolicy.TREAT_AS_AVAILABLE
        }
    }
}
