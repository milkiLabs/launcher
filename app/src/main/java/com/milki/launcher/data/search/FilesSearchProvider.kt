/**
 * FilesSearchProvider.kt - Search provider for files on the device
 * 
 * This provider searches the device's file storage when the user uses the "f" prefix.
 * It handles permission checking and returns either file results or a permission 
 * request placeholder.
 * 
 * SEARCHES ALL FILES:
 * - Documents (PDF, Word, Excel, etc.)
 * - Ebooks (EPUB, MOBI)
 * - Archives (ZIP, RAR)
 * - APKs, code files, and any other files
 * 
 * Images and videos are excluded from results.
 * 
 * PERMISSION REQUIREMENTS:
 * - Android 10 and below: READ_EXTERNAL_STORAGE (runtime permission)
 * - Android 11+: MANAGE_EXTERNAL_STORAGE (special permission via Settings)
 */

package com.milki.launcher.data.search

import android.Manifest
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import com.milki.launcher.domain.model.*
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.repository.SearchProvider

/**
 * Search provider for files on the device.
 * 
 * @property filesRepository Repository for accessing file data
 */
class FilesSearchProvider(
    private val filesRepository: FilesRepository
) : SearchProvider {

    override val config: SearchProviderConfig = SearchProviderConfig(
        prefix = "f",
        name = "Files",
        description = "Search all files on device",
        color = androidx.compose.ui.graphics.Color(0xFFFF9800),
        icon = Icons.AutoMirrored.Filled.InsertDriveFile
    )

    /**
     * Search files or return permission request.
     *
     * Flow:
     * 1. Check if storage permission is granted
     * 2. If not granted, return PermissionRequestResult
     * 3. If granted and query is blank, return empty list (UI handles empty state)
     * 4. If granted and query exists, search files
     *
     * ARCHITECTURAL NOTE:
     * This provider no longer creates fake "hint" or "empty" results.
     * Empty states are now properly handled in the UI layer where they belong.
     * This keeps search logic separate from display logic.
     *
     * @param query The search query (without the "f " prefix)
     * @return List of FileDocumentSearchResult or PermissionRequestResult, or empty list
     */
    override suspend fun search(query: String): List<SearchResult> {
        if (!filesRepository.hasFilesPermission()) {
            /**
             * On Android 11+, MANAGE_EXTERNAL_STORAGE is required to access all files.
             * This is a special permission that opens Settings for user approval.
             * On Android 10 and below, we use READ_EXTERNAL_STORAGE runtime permission.
             */
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            
            val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "Allow file access in Settings to search all files"
            } else {
                "Storage permission required to search files"
            }
            
            return listOf(
                PermissionRequestResult(
                    permission = permission,
                    providerPrefix = config.prefix,
                    message = message,
                    buttonText = "Grant Permission"
                )
            )
        }

        // Permission granted - perform actual search
        if (query.isBlank()) {
            // Return empty list - UI will show appropriate empty state
            return emptyList()
        }

        // Search files and map to results
        // If no files found, returns empty list (UI handles empty state)
        val files = filesRepository.searchFiles(query)
        return files.map { file ->
            FileDocumentSearchResult(file = file)
        }
    }
}
