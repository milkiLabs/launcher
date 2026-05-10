/**
 * LocalSearchActionHandler.kt - CompositionLocal for search action handling
 *
 * This file provides a CompositionLocal that allows any composable in the
 * search UI hierarchy to emit search actions without prop drilling.
 *
 * WHY COMPOSITIONLOCAL:
 * Previously, callbacks were passed through multiple layers:
 * MainActivity → LauncherScreen → AppSearchDialog → SearchResultsList → MixedResultsList → ContactSearchResultItem
 *
 * With CompositionLocal, any descendant can access the handler directly:
 * ```kotlin
 * val actionHandler = LocalSearchActionHandler.current
 * actionHandler(SearchResultAction.Tap(result))
 * ```
 *
 * BENEFITS:
 * - No prop drilling
 * - Easy to add new actions
 * - Clear separation between UI and action handling
 * - Better testability
 *
 * USAGE:
 * 1. In MainActivity, provide the handler:
 *    CompositionLocalProvider(LocalSearchActionHandler provides executor::execute) {
 *        LauncherScreen(...)
 *    }
 *
 * 2. In any descendant composable:
 *    val actionHandler = LocalSearchActionHandler.current
 *    actionHandler(SearchResultAction.DialContact(contact, phone))
 */

package com.milki.launcher.presentation.search

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for search action handling.
 *
 * Provides the action handler to all composables in the search UI hierarchy.
 * Any composable can call `LocalSearchActionHandler.current` to get
 * the handler and emit actions.
 *
 * IMPORTANT:
 * This must be provided at the top of the search UI hierarchy (in MainActivity).
 * If accessed without a provider, it will throw an error.
 *
 * EXAMPLE:
 * ```kotlin
 * @Composable
 * fun ContactSearchResultItem(result: ContactSearchResult) {
 *     val actionHandler = LocalSearchActionHandler.current
 *     
 *     Row(
 *         modifier = Modifier.clickable {
 *             actionHandler(SearchResultAction.Tap(result))
 *         }
 *     ) {
 *         // ... contact info ...
 *         
 *         Icon(
 *             modifier = Modifier.clickable {
 *                 val phone = result.contact.phoneNumbers.firstOrNull()
 *                 if (phone != null) {
 *                     actionHandler(SearchResultAction.DialContact(result.contact, phone))
 *                 }
 *             }
 *         )
 *     }
 * }
 * ```
 */
val LocalSearchActionHandler: ProvidableCompositionLocal<(SearchResultAction) -> Unit> = compositionLocalOf {
    error("LocalSearchActionHandler not provided. Wrap your UI in CompositionLocalProvider.")
}
