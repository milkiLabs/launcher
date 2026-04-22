package com.milki.launcher.ui.components.launcher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal const val FILE_ICON_FOREGROUND_SCALE = 0.5f
internal const val FOLDER_PREVIEW_SLOT_COUNT = 4

private val PdfFileColor = Color(0xFFE53935)
private val ImageFileColor = Color(0xFF43A047)
private val VideoFileColor = Color(0xFFFB8C00)
private val AudioFileColor = Color(0xFF8E24AA)
private val DocumentFileColor = Color(0xFF1E88E5)
private val ArchiveFileColor = Color(0xFF757575)
private val UnknownFileColor = Color(0xFF9E9E9E)

private val SpreadsheetExtensions = setOf("xls", "xlsx", "csv")
private val DocumentExtensions = setOf("doc", "docx", "rtf")
private val ArchiveExtensions = setOf("zip", "rar", "7z", "tar")

internal data class FileTypeVisual(
    val icon: ImageVector,
    val backgroundColor: Color
)

internal fun resolveFileTypeVisual(mimeType: String, fileName: String): FileTypeVisual {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when {
        mimeType == PDF_MIME_TYPE || extension == PDF_EXTENSION ->
            FileTypeVisual(Icons.Outlined.PictureAsPdf, PdfFileColor)
        mimeType.startsWith(IMAGE_MIME_PREFIX) ->
            FileTypeVisual(Icons.Filled.Image, ImageFileColor)
        mimeType.startsWith(VIDEO_MIME_PREFIX) ->
            FileTypeVisual(Icons.Filled.VideoFile, VideoFileColor)
        mimeType.startsWith(AUDIO_MIME_PREFIX) ->
            FileTypeVisual(Icons.AutoMirrored.Filled.InsertDriveFile, AudioFileColor)
        mimeType.contains(SPREADSHEET_MIME_FRAGMENT) || extension in SpreadsheetExtensions ->
            FileTypeVisual(Icons.Filled.TableChart, ImageFileColor)
        mimeType.contains(DOCUMENT_MIME_FRAGMENT) || extension in DocumentExtensions ->
            FileTypeVisual(Icons.Filled.Description, DocumentFileColor)
        mimeType.isArchiveType() || extension in ArchiveExtensions ->
            FileTypeVisual(Icons.Filled.FolderZip, ArchiveFileColor)
        else ->
            FileTypeVisual(Icons.AutoMirrored.Filled.InsertDriveFile, UnknownFileColor)
    }
}

private fun String.isArchiveType(): Boolean {
    return contains(ARCHIVE_MIME_FRAGMENT) || contains(ZIP_MIME_FRAGMENT)
}

private const val PDF_MIME_TYPE = "application/pdf"
private const val PDF_EXTENSION = "pdf"
private const val IMAGE_MIME_PREFIX = "image/"
private const val VIDEO_MIME_PREFIX = "video/"
private const val AUDIO_MIME_PREFIX = "audio/"
private const val SPREADSHEET_MIME_FRAGMENT = "spreadsheet"
private const val DOCUMENT_MIME_FRAGMENT = "document"
private const val ARCHIVE_MIME_FRAGMENT = "archive"
private const val ZIP_MIME_FRAGMENT = "zip"
