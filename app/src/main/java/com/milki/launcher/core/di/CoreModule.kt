package com.milki.launcher.core.di

import com.milki.launcher.data.repository.shortcut.ActionShortcutRepositoryImpl
import com.milki.launcher.data.repository.apps.AppRepositoryImpl
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.repository.settings.SettingsRepositoryImpl
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HomeTriggerRepository
import com.milki.launcher.domain.repository.PrefixOwnerRepository
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

    single {
        SettingsRepositoryImpl(get())
    }
    single<SettingsReader> { get<SettingsRepositoryImpl>() }
    single<SearchSourceRepository> { get<SettingsRepositoryImpl>() }
    single<PrefixOwnerRepository> { get<SettingsRepositoryImpl>() }
    single<HomeTriggerRepository> { get<SettingsRepositoryImpl>() }

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
