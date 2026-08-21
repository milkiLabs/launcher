package com.milki.launcher.presentation.home

import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.HomeItem

/**
 * Narrow controller surface for pinning/unpinning items on the home grid.
 *
 * WHY THIS EXISTS:
 * Consumers such as ActionExecutor and PinShortcutRequestCoordinator only need
 * pinning operations, not the full HomeViewModel. Programming against this
 * interface keeps them decoupled from the ViewModel implementation and makes
 * the required `internal` visibility on HomeViewModel unnecessary.
 */
interface HomePinningController {
    fun pinFile(file: FileDocument)
    fun pinContact(contact: Contact)
    fun unpinItem(itemId: String)
    fun pinAppShortcut(shortcut: HomeItem.AppShortcut)
    suspend fun isPinned(itemId: String): Boolean
}
