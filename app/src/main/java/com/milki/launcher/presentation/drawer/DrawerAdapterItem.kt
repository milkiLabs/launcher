package com.milki.launcher.presentation.drawer

import androidx.compose.runtime.Immutable
import com.milki.launcher.domain.model.AppInfo

@Immutable
sealed interface DrawerAdapterItem {
    @Immutable
    data class SectionHeader(
        val title: String
    ) : DrawerAdapterItem

    @Immutable
    data class AppEntry(
        val app: AppInfo
    ) : DrawerAdapterItem
}
