package com.milki.launcher.util

import android.webkit.MimeTypeMap

/**
 * Central MIME utilities for the launcher.
 *
 * DESIGN GOAL:
 * - Use Android's system-maintained MIME database (`MimeTypeMap`) as the primary source of truth.
 * - Keep only a small curated fallback table for edge cases where platform lookups can be blank
 *   or inconsistent across devices.
 *
 * WHY THIS APPROACH:
 * - Manual full MIME tables drift over time and are expensive to maintain.
 * - System lookups get updates with Android/WebKit improvements.
 * - A small fallback map still protects common document formats when lookups fail.
 */
object MimeTypeUtil {
    const val MIME_BINARY_FALLBACK = "application/octet-stream"

    private const val MIME_PDF = "application/pdf"
    private const val MIME_EPUB = "application/epub+zip"
    private const val MIME_WORD_DOC = "application/msword"
    private const val MIME_WORD_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val MIME_EXCEL_XLS = "application/vnd.ms-excel"
    private const val MIME_EXCEL_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val MIME_POWERPOINT_PPT = "application/vnd.ms-powerpoint"
    private const val MIME_POWERPOINT_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    private const val MIME_ZIP = "application/zip"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /**
     * Small fallback table for high-value document formats.
     *
     * We intentionally avoid storing an exhaustive map. The platform owns the full registry;
     * this table is only used when platform resolution returns null/blank.
     */
    private val fallbackExtensionToMimeType: Map<String, String> = mapOf(
        "pdf" to MIME_PDF,
        "epub" to MIME_EPUB,
        "doc" to MIME_WORD_DOC,
        "docx" to MIME_WORD_DOCX,
        "xls" to MIME_EXCEL_XLS,
        "xlsx" to MIME_EXCEL_XLSX,
        "ppt" to MIME_POWERPOINT_PPT,
        "pptx" to MIME_POWERPOINT_PPTX,
        "txt" to "text/plain",
        "md" to "text/markdown",
        "json" to "application/json",
        "xml" to "application/xml",
        "zip" to MIME_ZIP,
        "apk" to MIME_APK,
        "csv" to "text/csv",
        "rtf" to "application/rtf"
    )

    /**
     * Returns best-effort MIME from extension.
     *
     * Resolution order:
     * 1) Android `MimeTypeMap`
     * 2) Internal curated fallback map
     * 3) `application/octet-stream`
     */
    fun getMimeTypeFromExtension(extension: String): String {
        val normalizedExtension = extension
            .trim()
            .removePrefix(".")
            .lowercase()

        if (normalizedExtension.isBlank()) {
            return MIME_BINARY_FALLBACK
        }

        val systemMimeType = MimeTypeMap
            .getSingleton()
            .getMimeTypeFromExtension(normalizedExtension)

        return systemMimeType
            ?: fallbackExtensionToMimeType[normalizedExtension]
            ?: MIME_BINARY_FALLBACK
    }

    /**
     * Returns best-effort MIME for a specific file name.
     *
     * If a MIME value already exists and is not generic, it is preserved.
     * If the MIME is blank or generic (`application/octet-stream`), resolution is retried from extension.
     */
    fun normalizeMimeType(fileName: String, providedMimeType: String): String {
        val trimmedProvidedMime = providedMimeType.trim()
        if (trimmedProvidedMime.isNotBlank() && trimmedProvidedMime != MIME_BINARY_FALLBACK) {
            return trimmedProvidedMime
        }

        val extension = fileName.substringAfterLast('.', "")
        return getMimeTypeFromExtension(extension)
    }

    /**
     * Check if a file is a PDF document.
     *
     * Checks both the MIME type and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is a PDF
     */
    fun isPdf(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return normalizeMimeType(fileName, mimeType) == MIME_PDF || extension == "pdf"
    }

    /**
     * Check if a file is an EPUB ebook.
     *
     * Checks both the MIME type and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is an EPUB
     */
    fun isEpub(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return normalizeMimeType(fileName, mimeType) == MIME_EPUB || extension == "epub"
    }

    /**
     * Check if a file is a Microsoft Word document.
     *
     * Covers both .doc and .docx formats.
     * Checks both the MIME type and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is a Word document
     */
    fun isWordDocument(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = normalizeMimeType(fileName, mimeType)
        return normalizedMimeType in listOf(MIME_WORD_DOC, MIME_WORD_DOCX) ||
                extension in listOf("doc", "docx")
    }

    /**
     * Check if a file is a Microsoft Excel spreadsheet.
     *
     * Covers both .xls and .xlsx formats.
     * Checks both the MIME type and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is an Excel spreadsheet
     */
    fun isExcelSpreadsheet(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = normalizeMimeType(fileName, mimeType)
        return normalizedMimeType in listOf(MIME_EXCEL_XLS, MIME_EXCEL_XLSX) ||
                extension in listOf("xls", "xlsx")
    }

    /**
     * Check if a file is a Microsoft PowerPoint presentation.
     *
     * Covers both .ppt and .pptx formats.
     * Checks both the MIME type and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is a PowerPoint presentation
     */
    fun isPowerPoint(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = normalizeMimeType(fileName, mimeType)
        return normalizedMimeType in listOf(MIME_POWERPOINT_PPT, MIME_POWERPOINT_PPTX) ||
                extension in listOf("ppt", "pptx")
    }

    /**
     * Check if a file is a text file.
     *
     * Includes plain text files and common text-based formats
     * like markdown, JSON, and XML.
     * Checks both the MIME type (any text type) and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is a text file
     */
    fun isTextFile(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = normalizeMimeType(fileName, mimeType)
        return normalizedMimeType.startsWith("text/") ||
                extension in listOf("txt", "rtf", "md", "json", "xml")
    }

    /**
     * Check if a file is a ZIP archive.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is a ZIP archive
     */
    fun isZip(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return normalizeMimeType(fileName, mimeType) == MIME_ZIP || extension == "zip"
    }

    /**
     * Check if a file is an Android APK package.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is an APK
     */
    fun isApk(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return normalizeMimeType(fileName, mimeType) == MIME_APK || extension == "apk"
    }
}
