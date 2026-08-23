package com.milki.launcher.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.milki.launcher.R
import com.milki.launcher.domain.model.LauncherSettings
import com.milki.launcher.domain.model.SearchLayout
import com.milki.launcher.ui.components.settings.DropdownSettingItem
import com.milki.launcher.ui.components.settings.SettingsCategory

@Composable
internal fun SearchLayoutSection(
    settings: LauncherSettings,
    onSetSearchLayout: (SearchLayout) -> Unit
) {
    SettingsCategory(title = stringResource(R.string.settings_group_search_title))

    val classicLabel = stringResource(R.string.search_layout_classic)
    val oneHandedLabel = stringResource(R.string.search_layout_one_handed)

    DropdownSettingItem(
        title = stringResource(R.string.search_layout_title),
        selectedValue = when (settings.searchLayout) {
            SearchLayout.CLASSIC -> classicLabel
            SearchLayout.ONE_HANDED -> oneHandedLabel
        },
        options = listOf(
            classicLabel to SearchLayout.CLASSIC,
            oneHandedLabel to SearchLayout.ONE_HANDED
        ),
        onOptionSelected = onSetSearchLayout
    )
}
