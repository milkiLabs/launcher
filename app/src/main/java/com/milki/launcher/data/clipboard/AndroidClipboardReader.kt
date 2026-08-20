package com.milki.launcher.data.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.milki.launcher.domain.search.ClipboardReader

/**
 * Android-backed [ClipboardReader] that reads the system clipboard service.
 */
class AndroidClipboardReader(
    private val context: Context
) : ClipboardReader {

    override fun readText(): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        val primaryClip = clipboardManager.primaryClip ?: return null
        if (primaryClip.itemCount <= 0) return null

        return primaryClip
            .getItemAt(0)
            .coerceToText(context)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}