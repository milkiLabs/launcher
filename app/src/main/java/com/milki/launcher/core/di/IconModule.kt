package com.milki.launcher.core.di

import android.content.Context
import com.milki.launcher.data.contextmenu.AppContextDataCache
import com.milki.launcher.data.icon.AppIconDiskSnapshotStore
import com.milki.launcher.data.icon.AppIconMemoryCache
import com.milki.launcher.domain.icon.IconPriorityStore
import org.koin.dsl.module

/**
 * Process-wide icon and context-menu caches.
 *
 * These are singletons by design (launcher-wide instant reads), but they are
 * DI-managed classes rather than Kotlin objects so tests can construct fakes
 * and swap dispatchers.
 */
val iconModule = module {
    single {
        AppIconDiskSnapshotStore(context = get<Context>())
    }

    single {
        AppIconMemoryCache(diskSnapshotStore = get())
    }

    single<IconPriorityStore> { get<AppIconMemoryCache>() }

    single {
        AppContextDataCache()
    }
}
