/**
 * FileDocument.kt - Domain model representing a file on the device
 * 
 * This file defines the FileDocument data class which represents a single file
 * from the device's storage. Images and videos are excluded from search results.
 * 
 * Used by the files search feature to display and open files.
 */

package com.milki.launcher.domain.model

import android.net.Uri

/**
 * Represents a single document/file from the device storage.
 * 
 * This model is specifically designed for document-type files, excluding
 * images and videos which are better handled by gallery apps.
 * 
 * @property id Unique file ID from the MediaStore
 * @property name The file name with extension (e.g., "resume.pdf")
 * @property mimeType The MIME type of the file (e.g., "application/pdf")
 * @property size File size in bytes
 * @property dateModified Last modified timestamp in milliseconds since epoch
 * @property uri Content URI for accessing the file
 * @property folderPath The folder path where the file is located (for display)
 * 
 * Example:
 * ```kotlin
 * val file = FileDocument(
 *     id = 123,
 *     name = "report.pdf",
 *     mimeType = "application/pdf",
 *     size = 1024000,
 *     dateModified = System.currentTimeMillis(),
 *     uri = Uri.parse("content://media/external/downloads/123"),
 *     folderPath = "Documents"
 * )
 * ```
 */
data class FileDocument(
    /**
     * Unique file ID from the MediaStore (_ID column).
     * This ID is used to construct the content URI for opening the file.
     */
    val id: Long,
    
    /**
     * The file name including extension.
     * Displayed prominently in the search results.
     */
    val name: String,
    
    /**
     * MIME type of the file (e.g., "application/pdf", "application/epub+zip").
     * Used to determine the appropriate app to open the file.
     */
    val mimeType: String,
    
    /**
     * File size in bytes.
     * Displayed in human-readable format (KB, MB, GB) in the UI.
     */
    val size: Long,
    
    /**
     * Last modified timestamp in milliseconds since Unix epoch.
     * Used to display relative time (e.g., "Modified 2 days ago").
     */
    val dateModified: Long,
    
    /**
     * Content URI for accessing this file.
     * This URI can be used with an Intent to open the file in an appropriate app.
     */
    val uri: Uri,
    
    /**
     * The folder name or path where this file is located.
     * Displayed as supporting text to help users identify the file location.
     * Examples: "Documents", "Downloads", "Books"
     */
    val folderPath: String
)

/**
 * Extension function to check if a file matches a search query.
 * Searches in the file name (case-insensitive).
 * 
 * @param query The search query
 * @return True if the file name contains the query
 */
fun FileDocument.matchesQuery(query: String): Boolean {
    return name.lowercase().contains(query.lowercase())
}

/**
 * Extension function to get a human-readable file size.
 * Converts bytes to KB, MB, or GB as appropriate.
 * 
 * @return Formatted file size string (e.g., "1.5 MB")
 */
fun FileDocument.formattedSize(): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    
    return when {
        size >= gb -> String.format("%.1f GB", size / gb)
        size >= mb -> String.format("%.1f MB", size / mb)
        size >= kb -> String.format("%.1f KB", size / kb)
        else -> "$size B"
    }
}

/**
 * Extension function to get the file extension from the name.
 * Returns the extension without the dot (e.g., "pdf" not ".pdf").
 * 
 * @return File extension in lowercase, or empty string if no extension
 */
fun FileDocument.extension(): String {
    val lastDot = name.lastIndexOf('.')
    return if (lastDot >= 0 && lastDot < name.length - 1) {
        name.substring(lastDot + 1).lowercase()
    } else {
        ""
    }
}

/**
 * Extension function to check if this file is a PDF.
 * Useful for displaying PDF-specific icons or actions.
 */
fun FileDocument.isPdf(): Boolean = mimeType == "application/pdf" || extension() == "pdf"

/**
 * Extension function to check if this file is an EPUB ebook.
 * Useful for displaying ebook-specific icons or actions.
 */
fun FileDocument.isEpub(): Boolean = mimeType == "application/epub+zip" || extension() == "epub"

/**
 * Extension function to check if this file is a Word document.
 * Covers both .doc and .docx formats.
 */
fun FileDocument.isWordDocument(): Boolean {
    return mimeType in listOf(
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ) || extension() in listOf("doc", "docx")
}

/**
 * Extension function to check if this file is an Excel spreadsheet.
 * Covers both .xls and .xlsx formats.
 */
fun FileDocument.isExcelSpreadsheet(): Boolean {
    return mimeType in listOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ) || extension() in listOf("xls", "xlsx")
}

/**
 * Extension function to check if this file is a PowerPoint presentation.
 * Covers both .ppt and .pptx formats.
 */
fun FileDocument.isPowerPoint(): Boolean {
    return mimeType in listOf(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    ) || extension() in listOf("ppt", "pptx")
}

/**
 * Extension function to check if this file is a text file.
 * Includes plain text, RTF, and other text-based formats.
 */
fun FileDocument.isTextFile(): Boolean {
    return mimeType.startsWith("text/") || extension() in listOf("txt", "rtf", "md", "json", "xml")
}
