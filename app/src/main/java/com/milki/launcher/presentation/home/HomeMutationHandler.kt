package com.milki.launcher.presentation.home

import com.milki.launcher.domain.model.AppInfo
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
     * Pins an app to home using the unified home mutation pipeline.
     */
    fun pinApp(appInfo: AppInfo)

    /**
     * Pins a file shortcut to home using the unified home mutation pipeline.
     */
    fun pinFile(file: FileDocument)

    /**
     * Removes a pinned item from home using the unified home mutation pipeline.
     */
    fun unpinItem(itemId: String)
}
