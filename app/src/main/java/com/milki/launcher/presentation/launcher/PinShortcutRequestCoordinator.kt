package com.milki.launcher.presentation.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.widget.Toast
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.presentation.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Accepts launcher pin-shortcut requests from apps like Chrome and persists
 * them into the launcher's home layout.
 */
internal class PinShortcutRequestCoordinator(
    private val context: Context,
    private val homeRepository: HomeRepository,
    private val homeViewModel: HomeViewModel,
    private val scope: CoroutineScope
) {

    fun handleIntent(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            return false
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        val request = launcherApps.getPinItemRequest(intent) ?: return false

        if (!request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            return false
        }

        val shortcutInfo = request.shortcutInfo ?: return false
        val homeShortcut = HomeItem.AppShortcut.fromShortcutInfo(shortcutInfo)

        scope.launch {
            val alreadyPinned = homeRepository.isPinned(homeShortcut.id)
            val accepted = runCatching { request.accept() }.getOrDefault(false)
            if (!accepted) {
                Toast.makeText(context, "Couldn't add shortcut to home screen", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (!alreadyPinned) {
                homeViewModel.pinAppShortcut(homeShortcut)
            }

            val label = homeShortcut.shortLabel.ifBlank { shortcutInfo.shortLabel?.toString().orEmpty() }
            val message = if (alreadyPinned) {
                if (label.isBlank()) {
                    "Shortcut already on home screen"
                } else {
                    "$label is already on the home screen"
                }
            } else {
                if (label.isBlank()) {
                    "Shortcut added to home screen"
                } else {
                    "Added $label to home screen"
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        return true
    }
}
