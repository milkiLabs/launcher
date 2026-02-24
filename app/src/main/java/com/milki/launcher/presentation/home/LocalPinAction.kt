/**
 * LocalPinAction.kt - CompositionLocal for pinning items to the home screen
 *
 * This provides a way for any composable in the search hierarchy to pin items
 * to the home screen without prop drilling callbacks through multiple layers.
 *
 * WHY COMPOSITIONLOCAL:
 * The pin action needs to be accessible from:
 * - AppListItem (app results)
 * - AppGridItem (app grid)
 * - FileDocumentSearchResultItem (file results)
 *
 * Passing callbacks through all these components would create:
 * - Many parameter additions
 * - Boilerplate in every component
 * - Prop drilling through intermediate components
 *
 * With CompositionLocal:
 * - Single source of truth
 * - No prop drilling
 * - Easy to add new pin-able items
 */

package com.milki.launcher.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.HomeItem

/**
 * CompositionLocal for pinning items to the home screen.
 *
 * This is provided by MainActivity and can be accessed by any
 * descendant composable to pin apps, files, or shortcuts.
 *
 * USAGE:
 * ```kotlin
 * // In MainActivity
 * CompositionLocalProvider(
 *     LocalPinAction provides { item -> homeViewModel.pinItem(item) }
 * ) {
 *     // content
 * }
 *
 * // In any descendant composable
 * val pinAction = LocalPinAction.current
 * pinAction(HomeItem.PinnedApp.fromAppInfo(appInfo))
 * ```
 */
val LocalPinAction: ProvidableCompositionLocal<(HomeItem) -> Unit> = staticCompositionLocalOf {
    { _: HomeItem -> }
}

/**
 * Convenience function to pin an app using the pin action.
 */
fun pinApp(action: (HomeItem) -> Unit, appInfo: AppInfo) {
    action(HomeItem.PinnedApp.fromAppInfo(appInfo))
}

/**
 * Convenience function to pin a file using the pin action.
 */
fun pinFile(action: (HomeItem) -> Unit, file: FileDocument) {
    action(HomeItem.PinnedFile.fromFileDocument(file))
}
