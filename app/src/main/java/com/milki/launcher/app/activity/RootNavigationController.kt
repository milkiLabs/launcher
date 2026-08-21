package com.milki.launcher.app.activity

import androidx.navigation3.runtime.NavBackStack

/**
 * Seam between the root back stack and intent handling (`onNewIntent`) that
 * runs outside composition.
 *
 * Unlike a composition-bound holder, this owns the [NavBackStack] eagerly
 * (created in `onCreate`), so home intents delivered before the first
 * composition — or after disposal during recreation — are still handled.
 *
 * @param onResetExtras invoked after non-home entries are popped; used to
 *   clear transient launcher routes owned by the host runtime.
 */
internal class RootNavigationController(
    initial: MainRoute,
    private val onResetExtras: () -> Unit
) {
    val backStack: NavBackStack<MainRoute> = NavBackStack(initial)

    /** Whether the root back stack currently shows the home entry. */
    fun isAtHome(): Boolean = backStack.lastOrNull() == MainRoute.Home

    /** Pops any non-home entries and clears transient launcher routes. */
    fun resetToHome() {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        onResetExtras()
    }

    /** Pushes [route] unless it is already the current entry. */
    fun pushIfAbsent(route: MainRoute) {
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    /** Pops the current entry unless only home remains. */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }
}

/** Stable string keys used to persist the stack across process death. */
internal val MainRoute.routeKey: String
    get() = when (this) {
        MainRoute.Home -> "home"
        MainRoute.Settings -> "settings"
    }

internal fun mainRouteFromKey(key: String): MainRoute? = when (key) {
    "home" -> MainRoute.Home
    "settings" -> MainRoute.Settings
    else -> null
}
