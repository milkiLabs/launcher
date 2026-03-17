package com.milki.launcher.presentation.drawer

import com.milki.launcher.domain.model.AppInfo

sealed interface DrawerAdapterItem {
    data class SectionHeader(
        val sectionKey: String,
        val title: String
    ) : DrawerAdapterItem

    data class AppEntry(
        val app: AppInfo,
        val sectionKey: String
    ) : DrawerAdapterItem
}
