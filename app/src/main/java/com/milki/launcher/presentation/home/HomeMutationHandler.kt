package com.milki.launcher.presentation.home

import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument

/**
 * HomeMutationHandler defines the write-only home layout operations that can be
 * triggered from UI action layers such as ActionExecutor.
 *
 * WHY THIS ABSTRACTION EXISTS:
 * - It lets ActionExecutor depend on a small behavior contract instead of a
 *   full repository API.
 * - It guarantees all home layout writes can be routed through one coordinator
 *   (HomeViewModel), which serializes ordering and error handling.
 */
interface HomeMutationHandler {

    /**
     * Pins a file shortcut to home using the unified home mutation pipeline.
     */
    fun pinFile(file: FileDocument)

    /**
     * Pins a contact shortcut to home using the unified home mutation pipeline.
     */
    fun pinContact(contact: Contact)

    /**
     * Removes a pinned item from home using the unified home mutation pipeline.
     */
    fun unpinItem(itemId: String)
}
