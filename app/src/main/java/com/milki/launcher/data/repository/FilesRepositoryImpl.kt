
package com.milki.launcher.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.milki.launcher.core.permission.PermissionUtil
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.repository.FilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class FilesRepositoryImpl(
    private val context: Context
) : FilesRepository {

        private val recentStorage = FilesRecentStorage(context)
    private val cursorReader = MediaStoreFileCursorReader()

    companion object {
        private const val TAG = "FilesRepositoryImpl"
    }

        private val contentResolver = context.contentResolver

        override fun hasFilesPermission(): Boolean {
        return PermissionUtil.hasFilesPermission(context)
    }

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
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            Log.d(TAG, "Querying URI: $uri with query: $query")
            val cursor = contentResolver.query(
                uri,
                cursorReader.projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )
            
            Log.d(TAG, "Query $uri returned ${cursor?.count ?: 0} rows")
            cursor?.use {
                val columns = cursorReader.resolveColumns(it)
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (files.size >= maxItems) {
                        break
                    }

                    cursorReader.addFileFromCursorRow(
                        cursor = it,
                        columns = columns,
                        collectionUri = uri,
                        files = files,
                        addedFileIds = addedFileIds,
                        logFilteredOut = true
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error querying URI: $uri", e)
        } catch (e: SecurityException) {
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
                val cursor = contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    cursorReader.projection,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"
                )
                
                cursor?.use {
                    val columns = cursorReader.resolveColumns(it)
                    
                    while (it.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        cursorReader.addFileFromCursorRow(
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
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error getting recent files", e)
                emptyList()
            } catch (e: SecurityException) {
                Log.e(TAG, "Error getting recent files", e)
                emptyList()
            }
        }
    }

    override suspend fun saveRecentFile(fileId: Long) {
        recentStorage.saveRecent(fileId)
    }

    override fun getRecentFileIds(): kotlinx.coroutines.flow.Flow<List<Long>> {
        return recentStorage.observeRecent().flowOn(Dispatchers.IO)
    }

    override suspend fun getFilesByIds(ids: List<Long>): Map<Long, FileDocument> {
        if (!hasFilesPermission() || ids.isEmpty()) {
            return emptyMap()
        }

        return withContext(Dispatchers.IO) {
            try {
                val filesMap = mutableMapOf<Long, FileDocument>()
                val addedFileIds = mutableSetOf<Long>()
                val chunkedIds = ids.chunked(500)
                for (chunk in chunkedIds) {
                    val placeholders = chunk.joinToString(",") { "?" }
                    val selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders)"
                    val selectionArgs = chunk.map { it.toString() }.toTypedArray()

                    val cursor = contentResolver.query(
                        MediaStore.Files.getContentUri("external"),
                        cursorReader.projection,
                        selection,
                        selectionArgs,
                        null
                    )

                    cursor?.use {
                        val columns = cursorReader.resolveColumns(it)

                        while (it.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val tempFiles = mutableListOf<FileDocument>()
                            cursorReader.addFileFromCursorRow(
                                cursor = it,
                                columns = columns,
                                collectionUri = MediaStore.Files.getContentUri("external"),
                                files = tempFiles,
                                addedFileIds = addedFileIds,
                                logFilteredOut = false
                            )
                            if (tempFiles.isNotEmpty()) {
                                val doc = tempFiles.first()
                                filesMap[doc.id] = doc
                            }
                        }
                    }
                }
                filesMap
            } catch (e: Exception) {
                Log.e(TAG, "Error getting files by ids", e)
                emptyMap()
            }
        }
    }


}
