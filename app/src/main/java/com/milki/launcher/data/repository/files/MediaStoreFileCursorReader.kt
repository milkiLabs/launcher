package com.milki.launcher.data.repository.files

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.milki.launcher.core.file.MimeTypeResolver
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileFilterConfig
import kotlinx.coroutines.CancellationException

private const val MEDIA_STORE_FILE_CURSOR_READER_TAG = "MediaStoreFileCursorReader"
private const val MILLISECONDS_PER_SECOND = 1_000L

internal class MediaStoreFileCursorReader {
    data class MediaStoreColumns(
        val idColumn: Int,
        val nameColumn: Int,
        val mimeTypeColumn: Int,
        val sizeColumn: Int,
        val dateModifiedColumn: Int,
        val bucketColumn: Int,
        val relativePathColumn: Int
    )

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.RELATIVE_PATH
    )

    fun resolveColumns(cursor: Cursor): MediaStoreColumns {
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

    suspend fun addFileFromCursorRow(
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
                        Log.d(MEDIA_STORE_FILE_CURSOR_READER_TAG, "Filtered out: ${outcome.fileName}")
                    }
                }

                is CursorRowOutcome.Include -> {
                    addedFileIds.add(outcome.fileDocument.id)
                    if (logFilteredOut) {
                        Log.d(
                            MEDIA_STORE_FILE_CURSOR_READER_TAG,
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
            Log.e(MEDIA_STORE_FILE_CURSOR_READER_TAG, "Error reading file from cursor", e)
        } catch (e: IllegalStateException) {
            Log.e(MEDIA_STORE_FILE_CURSOR_READER_TAG, "Error reading file from cursor", e)
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
        MimeTypeResolver.normalizeMimeType(
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

}
