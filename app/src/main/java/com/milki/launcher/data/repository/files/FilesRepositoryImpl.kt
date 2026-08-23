package com.milki.launcher.data.repository.files

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.milki.launcher.core.content.forEachRow
import com.milki.launcher.core.content.sqlInSelection
import com.milki.launcher.core.permission.PermissionChecker
import com.milki.launcher.data.repository.common.AbstractContentResolverRecentStore
import com.milki.launcher.data.repository.common.RecentListStorage
import com.milki.launcher.data.repository.common.filesRecentDataStore
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileSearchExtensionConfig
import com.milki.launcher.domain.repository.FilesRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

class FilesRepositoryImpl(
    context: Context
) : AbstractContentResolverRecentStore<Long>(context), FilesRepository {

    override val recentStore: RecentListStorage<Long> = RecentListStorage(
        dataStore = appContext.filesRecentDataStore,
        key = stringPreferencesKey("recent_files"),
        maxSize = 8,
        encoder = { fileId -> fileId.toString() },
        decoder = { raw -> raw.toLongOrNull() }
    )

    private val cursorReader = MediaStoreFileCursorReader()

    override fun hasFilesPermission(): Boolean = PermissionChecker.hasFilesPermission(appContext)

    override fun hasPermission(): Boolean = hasFilesPermission()

    override suspend fun saveRecentFile(fileId: Long) {
        saveRecent(fileId)
    }

    override fun getRecentFileIds(): Flow<List<Long>> {
        return observeRecent()
    }

    override suspend fun searchFiles(
        query: String,
        maxItems: Int,
        extensionConfig: FileSearchExtensionConfig,
    ): List<FileDocument> {
        return withPermissionOr(
            whenGranted = {
                if (maxItems <= 0) {
                    emptyList()
                } else {
                    queryMediaStore(searchQuery = query, maxItems = maxItems, extensionConfig = extensionConfig)
                }
            },
            whenDenied = { emptyList() }
        ) ?: emptyList()
    }

    override suspend fun getFilesByIds(
        ids: List<Long>,
        extensionConfig: FileSearchExtensionConfig,
    ): Map<Long, FileDocument> {
        if (ids.isEmpty()) {
            return emptyMap()
        }

        return withPermissionOr(
            whenGranted = { queryMediaStoreByIds(ids = ids, extensionConfig = extensionConfig) },
            whenDenied = { emptyMap() }
        ) ?: emptyMap()
    }

    private suspend fun queryMediaStore(
        searchQuery: String,
        maxItems: Int,
        extensionConfig: FileSearchExtensionConfig,
    ): List<FileDocument> {
        val files = mutableListOf<FileDocument>()
        val addedFileIds = mutableSetOf<Long>()

        Log.d(TAG, "Searching files with query: $searchQuery")
        currentCoroutineContext().ensureActive()

        queryMediaStoreCollection(
            uri = MediaStore.Files.getContentUri("external"),
            query = searchQuery,
            files = files,
            addedFileIds = addedFileIds,
            maxItems = maxItems,
            extensionConfig = extensionConfig
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && files.size < maxItems) {
            currentCoroutineContext().ensureActive()
            queryMediaStoreCollection(
                uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                query = searchQuery,
                files = files,
                addedFileIds = addedFileIds,
                maxItems = maxItems,
                extensionConfig = extensionConfig
            )
        }

        Log.d(TAG, "Returning ${files.size} files")
        return files
    }

    private suspend fun queryMediaStoreCollection(
        uri: Uri,
        query: String,
        files: MutableList<FileDocument>,
        addedFileIds: MutableSet<Long>,
        maxItems: Int,
        extensionConfig: FileSearchExtensionConfig,
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

                    cursorReader.readFileFromCursorRow(
                        cursor = it,
                        columns = columns,
                        collectionUri = uri,
                        allowedExtensions = extensionConfig.resolveAllowedExtensions(),
                        excludedMimePrefixes = extensionConfig.resolveExcludedMimePrefixes()
                    )?.let { doc ->
                        if (addedFileIds.add(doc.id)) {
                            files.add(doc)
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error querying URI: $uri", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error querying URI: $uri", e)
        }
    }

    private suspend fun queryMediaStoreByIds(
        ids: List<Long>,
        extensionConfig: FileSearchExtensionConfig,
    ): Map<Long, FileDocument> {
        val filesMap = mutableMapOf<Long, FileDocument>()

        for (chunk in ids.chunked(500)) {
            val selection = sqlInSelection(MediaStore.Files.FileColumns._ID, chunk) ?: continue

            var columns: MediaStoreFileCursorReader.MediaStoreColumns? = null
            contentResolver.forEachRow(
                uri = MediaStore.Files.getContentUri("external"),
                projection = cursorReader.projection,
                selection = selection.first,
                selectionArgs = selection.second
            ) { cursor ->
                currentCoroutineContext().ensureActive()
                val resolvedColumns = columns ?: cursorReader.resolveColumns(cursor).also { columns = it }
                val tempFiles = mutableListOf<FileDocument>()
                cursorReader.addFileFromCursorRow(
                    cursor = cursor,
                    columns = resolvedColumns,
                    collectionUri = MediaStore.Files.getContentUri("external"),
                    files = tempFiles,
                    addedFileIds = mutableSetOf(),
                    logFilteredOut = false,
                    allowedExtensions = extensionConfig.resolveAllowedExtensions(),
                    excludedMimePrefixes = extensionConfig.resolveExcludedMimePrefixes()
                )
                if (tempFiles.isNotEmpty()) {
                    val doc = tempFiles.first()
                    filesMap[doc.id] = doc
                }
            }
        }

        return filesMap
    }

    private companion object {
        const val TAG = "FilesRepositoryImpl"
    }
}
