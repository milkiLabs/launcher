package com.milki.launcher.data.repository.home

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.milki.launcher.data.repository.common.newlineJsonListSerializer
import com.milki.launcher.domain.model.HomeItem

/**
 * Converts between DataStore Preferences payloads and HomeItem lists.
 *
 * Storage format is newline-separated JSON: one HomeItem per line.
 * Corrupted rows are skipped so one bad line does not invalidate the full model.
 */
internal class HomeItemSerializer {

    private val delegate = newlineJsonListSerializer(
        key = HomePreferenceKeys.PINNED_ITEMS,
        serializer = HomeItem.serializer(),
        default = { listOf(HomeItem.ActionShortcut.DefaultDocsShortcut) }
    )

    fun readFrom(preferences: Preferences): List<HomeItem> =
        delegate.readFrom(preferences)

    fun writeTo(
        items: List<HomeItem>,
        preferences: MutablePreferences
    ) = delegate.writeTo(items, preferences)
}
