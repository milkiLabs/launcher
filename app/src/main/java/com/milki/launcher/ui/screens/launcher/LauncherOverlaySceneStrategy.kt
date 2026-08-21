package com.milki.launcher.ui.screens.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Navigation 3 scene strategy for transient launcher surfaces.
 *
 * The strategy supplies overlay semantics while allowing each destination to
 * preserve its existing custom sheet, dialog, or folder implementation.
 */
internal class LauncherOverlaySceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>
    ): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        if (entry.metadata[LauncherOverlayKey] != true) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return LauncherOverlayScene(
            key = entry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = entry,
            onBack = onBack
        )
    }

    companion object {
        private object LauncherOverlayKey : NavMetadataKey<Boolean>

        /**
         * Marks an entry as a launcher overlay destination.
         */
        fun overlay() = metadata {
            put(LauncherOverlayKey, true)
        }
    }
}

/**
 * An overlay scene whose destination content owns its visual presentation.
 *
 * This intentionally adds no generic container: existing launcher sheets keep
 * their drag behavior, search keeps its dialog, and folders retain their
 * anchored popup layout.
 */
private data class LauncherOverlayScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        BackHandler(onBack = onBack)
        entry.Content()
    }
}
