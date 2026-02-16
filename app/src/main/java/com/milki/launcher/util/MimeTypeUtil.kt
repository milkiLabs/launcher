/**
 * MimeTypeUtil.kt - Utility class for MIME type operations
 *
 * This file provides centralized MIME type definitions and helper functions
 * for working with file types throughout the app.
 *
 * WHY THIS FILE EXISTS:
 * Previously, MIME type strings were duplicated across multiple files:
 * - MainActivity.kt had a when block mapping extensions to MIME types
 * - FileDocument.kt had MIME strings in each type-checking function
 * This centralizes all MIME type knowledge in one place.
 *
 * USAGE:
 * ```kotlin
 * // Get MIME type from file extension
 * val mimeType = MimeTypeUtil.getMimeTypeFromExtension("pdf")
 * // Returns: "application/pdf"
 *
 * // Check if a file is a PDF
 * val isPdf = MimeTypeUtil.isPdf("application/pdf", "document.pdf")
 * // Returns: true
 * ```
 */

package com.milki.launcher.util

/**
 * Utility object for MIME type operations.
 *
 * This object contains:
 * 1. MIME type constants for all supported file types
 * 2. Extension-to-MIME type mappings
 * 3. Helper functions for type checking
 *
 * All MIME type strings are defined as constants to avoid typos
 * and provide autocomplete support in the IDE.
 */
object MimeTypeUtil {

    // ========================================================================
    // MIME TYPE CONSTANTS
    // ========================================================================

    /**
     * MIME type for PDF documents.
     * Standard MIME type as defined by Adobe.
     */
    const val MIME_PDF = "application/pdf"

    /**
     * MIME type for EPUB ebooks.
     * Standard MIME type for electronic publication format.
     */
    const val MIME_EPUB = "application/epub+zip"

    /**
     * MIME type for Microsoft Word documents (.doc).
     * Legacy Word format.
     */
    const val MIME_WORD_DOC = "application/msword"

    /**
     * MIME type for Microsoft Word documents (.docx).
     * Modern Word format (Open XML).
     */
    const val MIME_WORD_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /**
     * MIME type for Microsoft Excel spreadsheets (.xls).
     * Legacy Excel format.
     */
    const val MIME_EXCEL_XLS = "application/vnd.ms-excel"

    /**
     * MIME type for Microsoft Excel spreadsheets (.xlsx).
     * Modern Excel format (Open XML).
     */
    const val MIME_EXCEL_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /**
     * MIME type for Microsoft PowerPoint presentations (.ppt).
     * Legacy PowerPoint format.
     */
    const val MIME_POWERPOINT_PPT = "application/vnd.ms-powerpoint"

    /**
     * MIME type for Microsoft PowerPoint presentations (.pptx).
     * Modern PowerPoint format (Open XML).
     */
    const val MIME_POWERPOINT_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    /**
     * MIME type for plain text files.
     * Used for .txt, .md, .json, .xml files.
     */
    const val MIME_TEXT_PLAIN = "text/plain"

    /**
     * MIME type for ZIP archives.
     */
    const val MIME_ZIP = "application/zip"

    /**
     * MIME type for Android APK files.
     */
    const val MIME_APK = "application/vnd.android.package-archive"

    /**
     * Wildcard MIME type for unknown files.
     * Used when we can't determine the file type.
     */
    const val MIME_UNKNOWN = "*/*"

    // ========================================================================
    // EXTENSION TO MIME TYPE MAPPINGS
    // ========================================================================

    /**
     * Map of file extensions to their MIME types.
     *
     * This map is used to determine the MIME type of a file
     * when only the file extension is known.
     *
     * Extensions are stored in lowercase without the dot.
     */
    private val extensionToMimeType: Map<String, String> = mapOf(
        // Document formats
        "pdf" to MIME_PDF,
        "epub" to MIME_EPUB,

        // Microsoft Office - Word
        "doc" to MIME_WORD_DOC,
        "docx" to MIME_WORD_DOCX,

        // Microsoft Office - Excel
        "xls" to MIME_EXCEL_XLS,
        "xlsx" to MIME_EXCEL_XLSX,

        // Microsoft Office - PowerPoint
        "ppt" to MIME_POWERPOINT_PPT,
        "pptx" to MIME_POWERPOINT_PPTX,

        // Text formats
        "txt" to MIME_TEXT_PLAIN,
        "md" to MIME_TEXT_PLAIN,
        "json" to MIME_TEXT_PLAIN,
        "xml" to MIME_TEXT_PLAIN,

        // Archive formats
        "zip" to MIME_ZIP,

        // Android packages
        "apk" to MIME_APK
    )

    // ========================================================================
    // HELPER FUNCTIONS
    // ========================================================================

    /**
     * Get the MIME type for a given file extension.
     *
     * This function looks up the extension in the mapping and returns
     * the corresponding MIME type. If the extension is not recognized,
     * it returns the wildcard type "*/*".
     *
     * @param extension The file extension without the dot (e.g., "pdf", "docx")
     * @return The MIME type string, or "*/*" if unknown
     *
     * Example:
     * ```kotlin
     * val mimeType = MimeTypeUtil.getMimeTypeFromExtension("pdf")
     * // mimeType = "application/pdf"
     *
     * val unknown = MimeTypeUtil.getMimeTypeFromExtension("xyz")
     * // unknown = "*/*"
     * ```
     */
    fun getMimeTypeFromExtension(extension: String): String {
        return extensionToMimeType[extension.lowercase()] ?: MIME_UNKNOWN
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
        return mimeType == MIME_PDF || extension == "pdf"
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
        return mimeType == MIME_EPUB || extension == "epub"
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
        return mimeType in listOf(MIME_WORD_DOC, MIME_WORD_DOCX) ||
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
        return mimeType in listOf(MIME_EXCEL_XLS, MIME_EXCEL_XLSX) ||
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
        return mimeType in listOf(MIME_POWERPOINT_PPT, MIME_POWERPOINT_PPTX) ||
                extension in listOf("ppt", "pptx")
    }

    /**
     * Check if a file is a text file.
     *
     * Includes plain text files and common text-based formats
     * like markdown, JSON, and XML.
     * Checks both the MIME type (any text/* type) and the file extension.
     *
     * @param mimeType The file's MIME type (may be blank)
     * @param fileName The file name with extension
     * @return True if the file is a text file
     */
    fun isTextFile(mimeType: String, fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return mimeType.startsWith("text/") ||
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
        return mimeType == MIME_ZIP || extension == "zip"
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
        return mimeType == MIME_APK || extension == "apk"
    }
}
