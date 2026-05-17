package com.milki.launcher.core.di

import com.milki.launcher.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        SettingsViewModel(
            settingsReader = get(),
            searchSourceRepository = get(),
            prefixConfigRepository = get(),
            homeTriggerRepository = get(),
            hiddenAppsRepository = get(),
            appRepository = get(),
            actionShortcutRepository = get(),
            launcherBackupRepository = get(),
            urlHandlerResolver = get()
        )
    }
}
