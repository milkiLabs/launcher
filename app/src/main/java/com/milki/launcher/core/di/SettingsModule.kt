package com.milki.launcher.core.di

import com.milki.launcher.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        SettingsViewModel(
            settingsReader = get(),
            searchSourceRepository = get(),
            prefixOwnerRepository = get(),
            homeTriggerRepository = get(),
            appRepository = get(),
            actionShortcutRepository = get(),
            // Resolved lazily on first export/import so opening Settings does
            // not instantiate the backup graph (WidgetHostManager, etc.).
            launcherBackupRepository = { get() }
        )
    }
}
