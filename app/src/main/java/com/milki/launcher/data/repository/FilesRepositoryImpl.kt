/**
 * FilesRepositoryImpl.kt - Implementation of FilesRepository
 * 
 * This class implements the FilesRepository interface using Android's MediaStore API
 * to query for files on the device. Files are filtered using FileFilterConfig to
 * remove temporary files, cache files, media files, and other "noise".
 * 
 * FILTERING BEHAVIOR:
 * ===================
 * Files are excluded from results if they match ANY of these criteria:
 * - Hidden files (starting with . or ~)
 * - Temporary/cache extensions (.tmp, .temp, .cache, .lock, .log, etc.)
 * - Backup files (.bak, .backup)
 * - Partial downloads (.part, .crdownload, etc.)
 * - Media files (images, videos, audio - determined by MIME type)
 * - Files in cache/temp directories
 * - Files smaller than 1KB (likely placeholders or corrupted)
 * 
 * See FileFilterConfig.kt for the complete list of filtering rules.
 * 
 * MEDIASTORE COLLECTIONS:
 * ======================
 * On Android 11+ with scoped storage, we query multiple MediaStore collections:
 * - MediaStore.Files (general files)
 * - MediaStore.Downloads (downloaded files)
 * 
 * PERMISSION REQUIREMENTS:
 * =======================
 * - Android 10 (API 29) and below: READ_EXTERNAL_STORAGE runtime permission
 * - Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE ("All files access" in Settings)
 *   This is required because scoped storage restricts MediaStore.Files to only
 *   app-created files.
 */

package com.milki.launcher.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.milki.launcher.util.PermissionUtil
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileFilterConfig
import com.milki.launcher.domain.repository.FilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of FilesRepository using Android MediaStore.
 * 
 * Searches ALL files on the device, filtering out noise using FileFilterConfig.
 * 
 * @property context Android application context for accessing ContentResolver
 */
class FilesRepositoryImpl(
    private val context: Context
) : FilesRepository {

    companion object {
        private const val TAG = "FilesRepositoryImpl"
    }

    /**
     * ContentResolver for querying the MediaStore database.
     * 
     * The ContentResolver is the standard Android API for accessing content providers.
     * MediaStore is a content provider that indexes files on the device.
     */
    private val contentResolver = context.contentResolver

    /**
     * Check if the app has permission to read files.
     * 
     * Permission requirements differ by Android version:
     * 
     * Android 10 (API 29) and below:
     *   - Need READ_EXTERNAL_STORAGE runtime permission
     * 
     * Android 11+ (API 30+):
     *   - Scoped storage restricts MediaStore.Files to only see app-created files
     *   - To see ALL files, need MANAGE_EXTERNAL_STORAGE (special permission)
     *   - Without MANAGE_EXTERNAL_STORAGE, MediaStore.Files returns limited results
     * 
     * @return True if the app can access all files, false otherwise
     */
    override fun hasFilesPermission(): Boolean {
        return PermissionUtil.hasFilesPermission(context)
    }

    /**
     * Search for document files by name.
     * 
     * Searches through all accessible storage locations using MediaStore.
     * Results are filtered using FileFilterConfig to remove noise files.
     * 
     * ARCHITECTURE NOTE:
     * The filtering logic is centralized in FileFilterConfig. This keeps
     * the repository focused on data access while the config handles filtering.
     * If you need to change what files are included/excluded, modify FileFilterConfig.
     * 
     * @param query The search query (file name to search for)
     * @return List of matching FileDocument objects, sorted by date modified (newest first)
     */
    override suspend fun searchFiles(query: String): List<FileDocument> {
        if (!hasFilesPermission()) {
            Log.w(TAG, "searchFiles called without permission")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val files = mutableListOf<FileDocument>()
                val addedFileIds = mutableSetOf<Long>()
                
                Log.d(TAG, "Searching files with query: $query")
                
                // Query multiple MediaStore collections to get all files
                // On Android 11+, scoped storage limits what MediaStore.Files returns
                
                // 1. Query MediaStore.Files (general files)
                queryMediaStoreCollection(
                    uri = MediaStore.Files.getContentUri("external"),
                    query = query,
                    files = files,
                    addedFileIds = addedFileIds
                )
                
                // 2. Query MediaStore.Downloads (downloaded files)
                // Available on Android 10 (API 29) and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryMediaStoreCollection(
                        uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        query = query,
                        files = files,
                        addedFileIds = addedFileIds
                    )
                }
                
                Log.d(TAG, "Returning ${files.size} files")
                files
            } catch (e: Exception) {
                Log.e(TAG, "Error searching files", e)
                emptyList()
            }
        }
    }

    /**
     * Query a specific MediaStore collection for files matching the query.
     * 
     * This method does the actual database query and filters results using FileFilterConfig.
     * 
     * ANDROID DEVELOPER NOTE:
     * MediaStore queries work like SQL database queries:
     * - uri: Which "table" to query (Files, Downloads, etc.)
     * - projection: Which columns to return (like SELECT)
     * - selection: WHERE clause (like WHERE)
     * - selectionArgs: Parameters for the WHERE clause (prevents SQL injection)
     * - sortOrder: ORDER BY clause (how to sort results)
     * 
     * @param uri The MediaStore URI to query (e.g., MediaStore.Files.getContentUri("external"))
     * @param query The search query to filter by file name
     * @param files Mutable list to add matching files to
     * @param addedFileIds Set of file IDs already added (to prevent duplicates)
     */
    private fun queryMediaStoreCollection(
        uri: Uri,
        query: String,
        files: MutableList<FileDocument>,
        addedFileIds: MutableSet<Long>
    ) {
        try {
            // Build the WHERE clause: find files whose name contains the query
            // The LIKE operator with % wildcards does partial matching
            // %query% means "anything before, then query, then anything after"
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            Log.d(TAG, "Querying URI: $uri with query: $query")
            
            // Execute the query
            // The cursor is like a ResultSet in JDBC - it points to rows of results
            val cursor = contentResolver.query(
                uri,
                PROJECTION,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )
            
            Log.d(TAG, "Query $uri returned ${cursor?.count ?: 0} rows")
            
            // cursor?.use { } automatically closes the cursor when done
            // This is important for preventing memory leaks
            cursor?.use {
                // Get column indices once for efficiency
                // getColumnIndexOrThrow throws an exception if the column doesn't exist
                // This is safer than returning -1 and potentially getting wrong data
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateModifiedColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                
                // These columns might not exist on all Android versions
                // getColumnIndex returns -1 if the column doesn't exist
                val bucketColumn = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val relativePathColumn = it.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                
                // Iterate through all rows in the result set
                while (it.moveToNext()) {
                    try {
                        // Extract the file ID
                        // MediaStore uses IDs to uniquely identify files
                        val id = it.getLong(idColumn)
                        
                        // Skip duplicates
                        // The same file might appear in multiple collections
                        if (id in addedFileIds) continue
                        
                        // Extract file metadata from the cursor
                        val name = it.getString(nameColumn) ?: continue
                        val mimeType = it.getString(mimeTypeColumn) ?: ""
                        val size = it.getLong(sizeColumn)
                        
                        // DATE_MODIFIED is stored as Unix timestamp in seconds
                        // Convert to milliseconds by multiplying by 1000
                        val dateModified = it.getLong(dateModifiedColumn) * 1000
                        
                        // Get the folder path
                        // BUCKET_DISPLAY_NAME is the folder name (e.g., "Downloads")
                        // RELATIVE_PATH is the full relative path (e.g., "Download/Documents/")
                        val bucket = if (bucketColumn >= 0) it.getString(bucketColumn) else null
                        val relativePath = if (relativePathColumn >= 0) it.getString(relativePathColumn) else null
                        
                        // Determine the folder name to display
                        // Priority: bucket name > last directory in relative path > "Storage"
                        val folderPath = bucket 
                            ?: relativePath?.substringAfterLast('/')?.trimEnd('/') 
                            ?: "Storage"
                        
                        Log.d(TAG, "Found file: $name, mimeType: $mimeType, size: $size")
                        
                        // Apply filters using FileFilterConfig
                        // This replaces the old isImageOrVideo() method with comprehensive filtering
                        if (!FileFilterConfig.shouldIncludeFile(
                            fileName = name,
                            mimeType = mimeType,
                            size = size,
                            relativePath = relativePath ?: ""
                        )) {
                            Log.d(TAG, "Filtered out: $name")
                            continue
                        }
                        
                        // File passed all filters - add to results
                        addedFileIds.add(id)
                        
                        // Build the content URI for this file
                        // This URI can be used to open the file with an Intent
                        val fileUri = Uri.withAppendedPath(uri, id.toString())
                        
                        files.add(
                            FileDocument(
                                id = id,
                                name = name,
                                mimeType = mimeType,
                                size = size,
                                dateModified = dateModified,
                                uri = fileUri,
                                folderPath = folderPath
                            )
                        )
                    } catch (e: Exception) {
                        // Log error but continue processing other files
                        Log.e(TAG, "Error reading file from cursor", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying URI: $uri", e)
        }
    }

    /**
     * Get recently modified document files.
     * 
     * Returns the most recently modified documents, useful for showing
     * recent files when the search query is empty.
     * 
     * The same filtering rules apply as searchFiles().
     * 
     * @param limit Maximum number of files to return (default: 20)
     * @return List of recent FileDocument objects, sorted by date modified
     */
    override suspend fun getRecentFiles(limit: Int): List<FileDocument> {
        if (!hasFilesPermission()) {
            Log.w(TAG, "getRecentFiles called without permission")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val files = mutableListOf<FileDocument>()
                val addedFileIds = mutableSetOf<Long>()
                
                // Query MediaStore.Files for recent files
                // No WHERE clause (null, null) means "get all files"
                // Sort by DATE_MODIFIED DESC to get newest first
                // LIMIT clause limits the number of results
                val cursor = contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    PROJECTION,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"
                )
                
                cursor?.use {
                    // Get column indices
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateModifiedColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val bucketColumn = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    val relativePathColumn = it.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    
                    while (it.moveToNext()) {
                        try {
                            val id = it.getLong(idColumn)
                            if (id in addedFileIds) continue
                            
                            val name = it.getString(nameColumn) ?: continue
                            val mimeType = it.getString(mimeTypeColumn) ?: ""
                            val size = it.getLong(sizeColumn)
                            val dateModified = it.getLong(dateModifiedColumn) * 1000
                            
                            val bucket = if (bucketColumn >= 0) it.getString(bucketColumn) else null
                            val relativePath = if (relativePathColumn >= 0) it.getString(relativePathColumn) else null
                            val folderPath = bucket 
                                ?: relativePath?.substringAfterLast('/')?.trimEnd('/') 
                                ?: "Storage"
                            
                            // Apply filters using FileFilterConfig
                            if (!FileFilterConfig.shouldIncludeFile(
                                fileName = name,
                                mimeType = mimeType,
                                size = size,
                                relativePath = relativePath ?: ""
                            )) {
                                continue
                            }
                            
                            addedFileIds.add(id)
                            
                            val fileUri = Uri.withAppendedPath(
                                MediaStore.Files.getContentUri("external"),
                                id.toString()
                            )
                            
                            files.add(
                                FileDocument(
                                    id = id,
                                    name = name,
                                    mimeType = mimeType,
                                    size = size,
                                    dateModified = dateModified,
                                    uri = fileUri,
                                    folderPath = folderPath
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading file from cursor", e)
                        }
                    }
                }
                
                files
            } catch (e: Exception) {
                Log.e(TAG, "Error getting recent files", e)
                emptyList()
            }
        }
    }

    /**
     * Column projection for MediaStore queries.
     * 
     * This defines which columns we want to retrieve from the MediaStore database.
     * Only requesting the columns we need improves query performance.
     * 
     * COLUMNS:
     * - _ID: Unique identifier for the file (used to build content URIs)
     * - DISPLAY_NAME: The file name (e.g., "document.pdf")
     * - MIME_TYPE: The MIME type (e.g., "application/pdf")
     * - SIZE: File size in bytes
     * - DATE_MODIFIED: Last modified time (Unix timestamp in seconds)
     * - BUCKET_DISPLAY_NAME: The folder name containing the file
     * - RELATIVE_PATH: The relative path to the file
     */
    private val PROJECTION = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.RELATIVE_PATH
    )
}
