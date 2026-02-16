/**
 * FilesRepository.kt - Domain interface for files/documents data access
 * 
 * This interface defines the contract for accessing document files on the device.
 * It is specifically designed for productivity files (PDFs, documents, ebooks)
 * and explicitly excludes media files (images, videos).
 * 
 * The implementation is in data/repository/FilesRepositoryImpl.kt
 * 
 * PERMISSION REQUIREMENTS:
 * - Android 10 (API 29) and below: READ_EXTERNAL_STORAGE runtime permission
 * - Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE ("All files access" in Settings)
 *   This is required because scoped storage restricts MediaStore.Files to only
 *   app-created files. Without MANAGE_EXTERNAL_STORAGE, file search will not work.
 * 
 * NOTE: The READ_EXTERNAL_STORAGE permission must be declared in AndroidManifest.xml
 * for Android 10 and below. MANAGE_EXTERNAL_STORAGE must also be declared for Android 11+.
 */

package com.milki.launcher.domain.repository

import com.milki.launcher.domain.model.FileDocument

/**
 * Interface for accessing document files on the device.
 * 
 * This repository focuses specifically on document-type files:
 * - PDF documents
 * - EPUB ebooks
 * - Office documents (Word, Excel, PowerPoint)
 * - Text files
 * 
 * Media files (images, videos, audio) are intentionally excluded
 * to keep the search focused on productivity documents.
 */
interface FilesRepository {
    
    /**
     * Check if the app has permission to read files.
     * 
     * On Android 11+ (API 30+), this checks for MANAGE_EXTERNAL_STORAGE permission
     * via Environment.isExternalStorageManager(). This is required because scoped
     * storage restricts MediaStore.Files to only app-created files.
     * 
     * On Android 10 and below, this checks for READ_EXTERNAL_STORAGE runtime permission.
     * 
     * @return True if the app can access all files, false otherwise
     */
    fun hasFilesPermission(): Boolean
    
    /**
     * Search for document files by name.
     * 
     * Searches through all accessible storage locations using MediaStore.
     * Results are filtered to only include document types (no images/videos).
     * 
     * The search is case-insensitive and matches partial file names.
     * For example, searching "report" would match "Annual_Report_2024.pdf".
     * 
     * @param query The search query (file name to search for)
     * @return List of matching FileDocument objects, sorted by date modified (newest first)
     */
    suspend fun searchFiles(query: String): List<FileDocument>
    
    /**
     * Get recently modified document files.
     * 
     * Returns the most recently modified documents, useful for showing
     * recent files when the search query is empty.
     * 
     * @param limit Maximum number of files to return (default: 20)
     * @return List of recent FileDocument objects, sorted by date modified
     */
    suspend fun getRecentFiles(limit: Int = 20): List<FileDocument>
}
