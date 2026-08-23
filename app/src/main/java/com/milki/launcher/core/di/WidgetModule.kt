package com.milki.launcher.core.di

import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import com.milki.launcher.domain.widget.WidgetHostPort
import org.koin.dsl.module

val widgetModule = module {
    single {
        WidgetHostManager(get())
    }

    // UI/presentation injects the port; data-layer collaborators (backup,
    // picker catalog) may keep injecting the concrete WidgetHostManager.
    single<WidgetHostPort> { get<WidgetHostManager>() }

    single {
        WidgetPickerCatalogStore(
            context = get(),
            widgetHost = get(),
            packageChangeMonitor = get<PackageChangeMonitor>(),
            applicationScope = get(ApplicationScope)
        )
    }
}
