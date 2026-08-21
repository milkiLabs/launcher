package com.milki.launcher.ui.screens.settings

import androidx.compose.runtime.Composable
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.SearchLayout
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory

@Composable
internal fun SearchLayoutSection(
    settings: LauncherSettings,
    onSetSearchLayout: (SearchLayout) -> Unit
) {
    SettingsCategory(title = "Search")

    DropdownSettingItem(
        title = "Search layout",
        selectedValue = when (settings.searchLayout) {
            SearchLayout.CLASSIC -> "Classic"
            SearchLayout.ONE_HANDED -> "One-handed"
        },
        options = listOf(
            "Classic" to SearchLayout.CLASSIC,
            "One-handed" to SearchLayout.ONE_HANDED
        ),
        onOptionSelected = onSetSearchLayout
    )
}
