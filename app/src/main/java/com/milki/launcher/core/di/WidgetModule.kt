package com.milki.launcher.core.di

import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.data.widget.WidgetPickerCatalogStore
import org.koin.dsl.module

val widgetModule = module {
    single {
        WidgetHostManager(get())
    }

    single {
        WidgetPickerCatalogStore(
            context = get(),
            widgetHostManager = get(),
            packageChangeMonitor = get<PackageChangeMonitor>()
        )
    }
}
