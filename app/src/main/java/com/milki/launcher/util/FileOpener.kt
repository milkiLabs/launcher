/**
 * FileOpener.kt - Utility for opening files with external apps
 *
 * This file provides a centralized utility for opening files using the system's
 * built-in file handling. It uses Android's Intent system to let users choose
 * an app to open the file with.
 *
 * The FileOpener handles:
 * - Creating the appropriate Intent with the file's MIME type
 * - Granting URI permissions for content:// URIs
 * - Showing a file picker chooser (optional)
 * - Error handling when no app is available
 *
 * Usage:
 * ```kotlin
 * // Open with chooser (user picks app)
 * FileOpener.openFile(context, uri, mimeType, fileName)
 *
 * // Open directly (system picks default app)
 * FileOpener.openFileDirect(context, uri, mimeType)
 * ```
 */

package com.milki.launcher.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens a file with an external app, showing a chooser dialog.
 *
 * This function creates an Intent with ACTION_VIEW and the appropriate MIME type,
 * then shows a chooser dialog so the user can pick which app to use.
 *
 * @param context The Android context (Activity, Application, or any Context)
 * @param uri The content URI of the file to open
 * @param mimeType The MIME type of the file (e.g., "application/pdf")
 *                 If blank, defaults to "application/octet-stream"
 * @param fileName The display name of the file (shown in chooser title)
 *
 * Example:
 * ```kotlin
 * val uri = Uri.parse("content://media/external/downloads/123")
 * FileOpener.openFile(context, uri, "application/pdf", "Report.pdf")
 * ```
 */
fun openFile(
    context: Context,
    uri: Uri,
    mimeType: String,
    fileName: String
) {
    // Create an ACTION_VIEW intent with the file's URI and MIME type
    // MIME type defaults to octet-stream if not specified (generic binary data)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
        
        // FLAG_GRANT_READ_URI_PERMISSION is critical for content:// URIs
        // It grants temporary read permission to the receiving app
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        // FLAG_ACTIVITY_NEW_TASK is needed because we're launching from
        // a non-Activity context (e.g., Compose, Service, or broadcast receiver)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    // Create a chooser intent so the user can pick which app to use
    // The title shows the filename being opened
    val chooserIntent = Intent.createChooser(intent, "Open $fileName").apply {
        // Also add NEW_TASK to the chooser for consistency
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        // Attempt to start the activity
        // This will throw ActivityNotFoundException if no app can handle the file
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        // Show a toast error message if no app is available
        // This is a user-friendly fallback
        Toast.makeText(
            context,
            "No app found to open $fileName",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Opens a file directly without showing a chooser.
 *
 * This function is similar to openFile() but uses the system's default handler
 * for the file type instead of showing a chooser dialog. If no default is set,
 * the system will still show a chooser.
 *
 * @param context The Android context
 * @param uri The content URI of the file to open
 * @param mimeType The MIME type of the file
 *
 * Example:
 * ```kotlin
 * // Opens PDF directly in default PDF viewer
 * FileOpener.openFileDirect(context, uri, "application/pdf")
 * ```
 */
fun openFileDirect(
    context: Context,
    uri: Uri,
    mimeType: String
) {
    // Create an ACTION_VIEW intent with the file's URI and MIME type
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
        
        // Grant temporary read permission to the receiving app
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        // New task required because we're not in an Activity context
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        // Attempt to start the activity with the default handler
        context.startActivity(intent)
    } catch (e: Exception) {
        // Show a generic error toast if no app can handle the file
        Toast.makeText(
            context,
            "No app found to open this file",
            Toast.LENGTH_SHORT
        ).show()
    }
}
