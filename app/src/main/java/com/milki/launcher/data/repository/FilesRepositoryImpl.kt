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
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.milki.launcher.core.permission.PermissionUtil
import com.milki.launcher.core.file.MimeTypeUtil
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileFilterConfig
import com.milki.launcher.domain.repository.FilesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
        private const val MILLISECONDS_PER_SECOND = 1_000L
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
    override suspend fun searchFiles(query: String, maxItems: Int): List<FileDocument> {
        val hasPermission = hasFilesPermission()
        if (!hasPermission) {
            Log.w(TAG, "searchFiles called without permission")
        }

        return if (hasPermission && maxItems > 0) {
            withContext(Dispatchers.IO) {
                try {
                    val files = mutableListOf<FileDocument>()
                    val addedFileIds = mutableSetOf<Long>()

                    Log.d(TAG, "Searching files with query: $query")
                    currentCoroutineContext().ensureActive()

                    queryMediaStoreCollection(
                        uri = MediaStore.Files.getContentUri("external"),
                        query = query,
                        files = files,
                        addedFileIds = addedFileIds,
                        maxItems = maxItems
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && files.size < maxItems) {
                        currentCoroutineContext().ensureActive()
                        queryMediaStoreCollection(
                            uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            query = query,
                            files = files,
                            addedFileIds = addedFileIds,
                            maxItems = maxItems
                        )
                    }

                    Log.d(TAG, "Returning ${files.size} files")
                    files
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Error searching files", e)
                    emptyList()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error searching files", e)
                    emptyList()
                }
            }
        } else {
            emptyList()
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
    private suspend fun queryMediaStoreCollection(
        uri: Uri,
        query: String,
        files: MutableList<FileDocument>,
        addedFileIds: MutableSet<Long>,
        maxItems: Int
    ) {
        try {
            currentCoroutineContext().ensureActive()
            if (files.size >= maxItems) return
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
                mediaStoreProjection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )
            
            Log.d(TAG, "Query $uri returned ${cursor?.count ?: 0} rows")
            
            // cursor?.use { } automatically closes the cursor when done
            // This is important for preventing memory leaks
            cursor?.use {
                val columns = resolveMediaStoreColumns(it)
                
                // Iterate through all rows in the result set
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (files.size >= maxItems) {
                        break
                    }

                    addFileFromCursorRow(
                        cursor = it,
                        columns = columns,
                        collectionUri = uri,
                        files = files,
                        addedFileIds = addedFileIds,
                        logFilteredOut = true
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error querying URI: $uri", e)
        } catch (e: SecurityException) {
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
                    mediaStoreProjection,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"
                )
                
                cursor?.use {
                    val columns = resolveMediaStoreColumns(it)
                    
                    while (it.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        addFileFromCursorRow(
                            cursor = it,
                            columns = columns,
                            collectionUri = MediaStore.Files.getContentUri("external"),
                            files = files,
                            addedFileIds = addedFileIds,
                            logFilteredOut = false
                        )
                    }
                }
                
                files
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error getting recent files", e)
                emptyList()
            } catch (e: SecurityException) {
                Log.e(TAG, "Error getting recent files", e)
                emptyList()
            }
        }
    }

    private data class MediaStoreColumns(
        val idColumn: Int,
        val nameColumn: Int,
        val mimeTypeColumn: Int,
        val sizeColumn: Int,
        val dateModifiedColumn: Int,
        val bucketColumn: Int,
        val relativePathColumn: Int
    )

    private fun resolveMediaStoreColumns(cursor: Cursor): MediaStoreColumns {
        return MediaStoreColumns(
            idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID),
            nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME),
            mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE),
            sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE),
            dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED),
            bucketColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME),
            relativePathColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
        )
    }

    private suspend fun addFileFromCursorRow(
        cursor: Cursor,
        columns: MediaStoreColumns,
        collectionUri: Uri,
        files: MutableList<FileDocument>,
        addedFileIds: MutableSet<Long>,
        logFilteredOut: Boolean
    ) {
        try {
            when (val outcome = readCursorRow(cursor, columns, collectionUri, addedFileIds)) {
                CursorRowOutcome.Skip -> Unit
                is CursorRowOutcome.FilteredOut -> {
                    if (logFilteredOut) {
                        Log.d(TAG, "Filtered out: ${outcome.fileName}")
                    }
                }

                is CursorRowOutcome.Include -> {
                    addedFileIds.add(outcome.fileDocument.id)
                    if (logFilteredOut) {
                        Log.d(
                            TAG,
                            "Found file: ${outcome.fileDocument.name}, " +
                                "mimeType: ${outcome.normalizedMimeType}, size: ${outcome.fileDocument.size}"
                        )
                    }
                    files.add(outcome.fileDocument)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error reading file from cursor", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error reading file from cursor", e)
        }
    }

    private fun readCursorRow(
        cursor: Cursor,
        columns: MediaStoreColumns,
        collectionUri: Uri,
        addedFileIds: Set<Long>
    ): CursorRowOutcome {
        val id = cursor.getLong(columns.idColumn)
        val name = cursor.getString(columns.nameColumn)
        val rawMimeType = cursor.getString(columns.mimeTypeColumn) ?: ""
        val normalizedMimeType = name?.let {
            MimeTypeUtil.normalizeMimeType(
                fileName = it,
                providedMimeType = rawMimeType
            )
        }.orEmpty()
        val size = cursor.getLong(columns.sizeColumn)
        val dateModified = cursor.getLong(columns.dateModifiedColumn) * MILLISECONDS_PER_SECOND
        val bucket = readOptionalString(cursor, columns.bucketColumn)
        val relativePath = readOptionalString(cursor, columns.relativePathColumn)
        val folderPath = bucket
            ?: relativePath?.substringAfterLast('/')?.trimEnd('/')
            ?: "Storage"
        val shouldInclude = name != null &&
            id !in addedFileIds &&
            FileFilterConfig.shouldIncludeFile(
                fileName = name,
                mimeType = normalizedMimeType,
                size = size,
                relativePath = relativePath.orEmpty()
            )

        return when {
            id in addedFileIds || name == null -> CursorRowOutcome.Skip
            !shouldInclude -> CursorRowOutcome.FilteredOut(name)
            else -> CursorRowOutcome.Include(
                fileDocument = FileDocument(
                    id = id,
                    name = name,
                    mimeType = normalizedMimeType,
                    size = size,
                    dateModified = dateModified,
                    uri = Uri.withAppendedPath(collectionUri, id.toString()),
                    folderPath = folderPath
                ),
                normalizedMimeType = normalizedMimeType
            )
        }
    }

    private fun readOptionalString(cursor: Cursor, columnIndex: Int): String? {
        return if (columnIndex >= 0) cursor.getString(columnIndex) else null
    }

    private sealed interface CursorRowOutcome {
        data object Skip : CursorRowOutcome

        data class FilteredOut(
            val fileName: String
        ) : CursorRowOutcome

        data class Include(
            val fileDocument: FileDocument,
            val normalizedMimeType: String
        ) : CursorRowOutcome
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
    private val mediaStoreProjection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.RELATIVE_PATH
    )
}
