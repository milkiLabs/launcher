/**
 * FilesRepositoryImpl.kt - Implementation of FilesRepository
 * 
 * This class implements the FilesRepository interface using Android's MediaStore API
 * to query for files on the device. It excludes images and videos from results.
 * 
 * On Android 11+ with scoped storage, we query multiple MediaStore collections:
 * - MediaStore.Files (general files)
 * - MediaStore.Downloads (downloaded files)
 * - MediaStore.Documents (document files on Android 10+)
 */

package com.milki.launcher.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.milki.launcher.util.PermissionUtil
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.repository.FilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of FilesRepository using Android MediaStore.
 * Searches ALL files on the device, excluding images and videos.
 */
class FilesRepositoryImpl(
    private val context: Context
) : FilesRepository {

    companion object {
        private const val TAG = "FilesRepositoryImpl"
        
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "ico", "tiff", "tif"
        )
        
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpeg", "mpg"
        )
    }

    private val contentResolver = context.contentResolver

    override fun hasFilesPermission(): Boolean {
        /**
         * Permission requirements differ by Android version:
         * 
         * Android 10 (API 29) and below:
         *   - Need READ_EXTERNAL_STORAGE permission
         * 
         * Android 11+ (API 30+):
         *   - Scoped storage restricts MediaStore.Files to only see app-created files
         *   - To see ALL files, need MANAGE_EXTERNAL_STORAGE (special permission)
         *   - Without MANAGE_EXTERNAL_STORAGE, MediaStore.Files returns limited results
         */
        return PermissionUtil.hasFilesPermission(context)
    }

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
     */
    private fun queryMediaStoreCollection(
        uri: Uri,
        query: String,
        files: MutableList<FileDocument>,
        addedFileIds: MutableSet<Long>
    ) {
        try {
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            Log.d(TAG, "Querying URI: $uri with query: $query")
            
            val cursor = contentResolver.query(
                uri,
                PROJECTION,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )
            
            Log.d(TAG, "Query $uri returned ${cursor?.count ?: 0} rows")
            
            cursor?.use {
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
                        
                        // Skip duplicates
                        if (id in addedFileIds) continue
                        
                        val name = it.getString(nameColumn) ?: continue
                        val mimeType = it.getString(mimeTypeColumn) ?: ""
                        val size = it.getLong(sizeColumn)
                        val dateModified = it.getLong(dateModifiedColumn) * 1000
                        
                        // Get folder path
                        val bucket = if (bucketColumn >= 0) it.getString(bucketColumn) else null
                        val relativePath = if (relativePathColumn >= 0) it.getString(relativePathColumn) else null
                        val folderPath = bucket ?: relativePath?.substringAfterLast('/')?.trimEnd('/') ?: "Storage"
                        
                        Log.d(TAG, "Found file: $name, mimeType: $mimeType, size: $size")
                        
                        // Skip images and videos
                        if (isImageOrVideo(mimeType, name)) {
                            Log.d(TAG, "Skipping image/video: $name")
                            continue
                        }
                        
                        addedFileIds.add(id)
                        
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
                        Log.e(TAG, "Error reading file from cursor", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying URI: $uri", e)
        }
    }

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
                val cursor = contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    PROJECTION,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"
                )
                
                cursor?.use {
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
                            val folderPath = bucket ?: relativePath?.substringAfterLast('/')?.trimEnd('/') ?: "Storage"
                            
                            if (isImageOrVideo(mimeType, name)) {
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

    private fun isImageOrVideo(mimeType: String, name: String): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true) || 
            mimeType.startsWith("video/", ignoreCase = true)) {
            return true
        }
        
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS || extension in VIDEO_EXTENSIONS
    }

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
