package com.milki.launcher.core.di

import com.milki.launcher.data.repository.ActionShortcutRepositoryImpl
import com.milki.launcher.data.repository.AppRepositoryImpl
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.repository.settings.SettingsRepositoryImpl
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HiddenAppsRepository
import com.milki.launcher.domain.repository.HomeTriggerRepository
import com.milki.launcher.domain.repository.PrefixConfigurationRepository
import com.milki.launcher.domain.repository.SearchSourceRepository
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.search.UrlHandlerResolver
import org.koin.dsl.module

val coreModule = module {
    single {
        PackageChangeMonitor(get())
    }

    single<AppRepository> {
        AppRepositoryImpl(
            application = get(),
            packageChangeMonitor = get()
        )
    }

    single<SettingsReader> {
        SettingsRepositoryImpl(get())
    }
    single<SearchSourceRepository> { get<SettingsReader>() as SettingsRepositoryImpl }
    single<PrefixConfigurationRepository> { get<SettingsReader>() as SettingsRepositoryImpl }
    single<HomeTriggerRepository> { get<SettingsReader>() as SettingsRepositoryImpl }
    single<HiddenAppsRepository> { get<SettingsReader>() as SettingsRepositoryImpl }

    single<ActionShortcutRepository> {
        ActionShortcutRepositoryImpl(get())
    }

    single {
        UrlHandlerResolver(
            context = get(),
            packageChangeMonitor = get()
        )
    }
}
