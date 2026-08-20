package com.milki.launcher.domain.search

/**
 * Reads the currently active clipboard text.
 *
 * Implementations perform a single snapshot read and do not observe clipboard
 * change events. A null/blank result means no usable text is available.
 */
interface ClipboardReader {
    fun readText(): String?
}