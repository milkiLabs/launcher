package com.milki.launcher.core.di

import com.milki.launcher.core.crash.CrashLogWriter
import com.milki.launcher.data.repository.shortcut.ActionShortcutRepositoryImpl
import com.milki.launcher.data.repository.apps.AppRepositoryImpl
import com.milki.launcher.data.repository.apps.InstalledAppsCatalog
import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.repository.settings.SettingsRepositoryImpl
import com.milki.launcher.data.search.UrlHandlerResolver
import com.milki.launcher.domain.repository.ActionShortcutRepository
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HomeTriggerRepository
import com.milki.launcher.domain.repository.PrefixOwnerRepository
import com.milki.launcher.domain.repository.SearchSourceRepository
import com.milki.launcher.domain.repository.SettingsReader
import com.milki.launcher.domain.search.UrlHandlerPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val ApplicationScope = named("applicationScope")
val IoDispatcher = named("ioDispatcher")

val coreModule = module {
    single(IoDispatcher) { Dispatchers.IO }

    single { CrashLogWriter(get()) }

    single(ApplicationScope) {
        val ioDispatcher = get<CoroutineDispatcher>(IoDispatcher)
        CoroutineScope(SupervisorJob() + ioDispatcher)
    }

    single {
        PackageChangeMonitor(get())
    }

    single {
        InstalledAppsCatalog(get(), get<CoroutineDispatcher>(IoDispatcher))
    }

    single<AppRepository> {
        AppRepositoryImpl(
            application = get(),
            packageChangeMonitor = get(),
            appIconMemoryCache = get(),
            contextDataCache = get(),
            installedAppsCatalog = get(),
            applicationScope = get(ApplicationScope)
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
            packageChangeMonitor = get(),
            applicationScope = get(ApplicationScope)
        )
    }

    single<UrlHandlerPort> { get<UrlHandlerResolver>() }
}
