package com.milki.launcher.ui.screens.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.core.intent.launchAppShortcut
import com.milki.launcher.core.intent.launchPinnedApp
import com.milki.launcher.core.intent.openFile

/**
 * Handles opening a pinned item from the launcher surface.
 *
 * This helper keeps item-opening behavior out of the main screen file so the
 * screen can stay focused on UI composition and event routing.
 */
fun openPinnedItem(
    item: HomeItem,
    context: Context,
    onUnavailableItem: (String) -> Unit = {}
) {
    when (item) {
        is HomeItem.PinnedApp -> openPinnedApp(item, context, onUnavailableItem)
        is HomeItem.PinnedFile -> openPinnedFile(item, context)
        is HomeItem.PinnedContact -> openPinnedContact(item, context)
        is HomeItem.AppShortcut -> openAppShortcut(item, context, onUnavailableItem)
        // Folder taps are handled upstream by opening the folder overlay.
        is HomeItem.FolderItem -> Unit
        // Widgets handle their own clicks through RemoteViews PendingIntents.
        is HomeItem.WidgetItem -> Unit
    }
}

/**
 * Launches a pinned app.
 */
private fun openPinnedApp(
    item: HomeItem.PinnedApp,
    context: Context,
    onUnavailableItem: (String) -> Unit = {}
) {
    if (!launchPinnedApp(context, item)) {
        onUnavailableItem(item.id)
        Toast.makeText(context, "App not found: ${item.label}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a pinned file with an appropriate app.
 */
private fun openPinnedFile(item: HomeItem.PinnedFile, context: Context) {
    val uri = Uri.parse(item.uri)
    openFile(context, uri, item.mimeType, item.name)
}

/**
 * Opens a pinned contact using the dialer with the contact's primary number.
 */
private fun openPinnedContact(item: HomeItem.PinnedContact, context: Context) {
    val phoneNumber = item.primaryPhone
    if (phoneNumber.isNullOrBlank()) {
        Toast.makeText(context, "No phone number for ${item.displayName}", Toast.LENGTH_SHORT).show()
        return
    }

    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(dialIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens an app shortcut.
 */
private fun openAppShortcut(
    item: HomeItem.AppShortcut,
    context: Context,
    onUnavailableItem: (String) -> Unit = {}
) {
    if (!launchAppShortcut(context, item)) {
        onUnavailableItem(item.id)
        Toast.makeText(context, "App not found: ${item.shortLabel}", Toast.LENGTH_SHORT).show()
    }
}
