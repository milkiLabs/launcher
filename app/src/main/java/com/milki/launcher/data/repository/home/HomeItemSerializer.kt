package com.milki.launcher.data.repository.home

import com.milki.launcher.data.repository.common.NewlineJsonListSerializer
import com.milki.launcher.domain.model.HomeItem

/**
 * Converts between DataStore Preferences payloads and HomeItem lists.
 *
 * Storage format is newline-separated JSON: one HomeItem per line.
 * Corrupted rows are skipped so one bad line does not invalidate the full model.
 */
internal class HomeItemSerializer {

    private val delegate = NewlineJsonListSerializer(
        key = HomePreferenceKeys.PINNED_ITEMS,
        json = homeStorageJson,
        serializer = HomeItem.serializer(),
        default = { listOf(HomeItem.ActionShortcut.DefaultDocsShortcut) }
    )

    fun readFrom(preferences: androidx.datastore.preferences.core.Preferences): List<HomeItem> =
        delegate.readFrom(preferences)

    fun writeTo(
        items: List<HomeItem>,
        preferences: androidx.datastore.preferences.core.MutablePreferences
    ) = delegate.writeTo(items, preferences)
}
