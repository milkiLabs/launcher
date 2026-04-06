/**
 * WidgetModule.kt - Widget Feature Koin Dependency Injection Module
 *
 * This module provides all dependencies related to the WIDGET system:
 * - WidgetHostManager (wraps Android's AppWidgetHost framework)
 *
 * WHY A SEPARATE WIDGET MODULE?
 * The widget system is an Android-framework-heavy feature with its own lifecycle
 * requirements (AppWidgetHost must be started/stopped with the Activity). Isolating
 * it in its own module:
 * 1. Makes it explicit that WidgetHostManager is a global, cross-cutting singleton.
 * 2. Keeps widget-specific infrastructure out of the core module.
 * 3. Allows future expansion (e.g., widget configuration providers, widget preview cache).
 *
 * DEPENDENCIES ON OTHER MODULES:
 * This module depends on the shared package-change monitor from coreModule so
 * widget catalog caches stay in sync with app install/remove/update broadcasts.
 *
 * For a full explanation of Koin concepts, see: docs/KoinDependencyInjection.md
 */

package com.milki.launcher.core.di

import com.milki.launcher.data.repository.apps.PackageChangeMonitor
import com.milki.launcher.data.widget.WidgetHostManager
import org.koin.dsl.module

/**
 * Widget module — widget infrastructure dependencies.
 *
 * Currently contains only WidgetHostManager, but provides a natural home for
 * future widget-related dependencies (widget configuration storage, widget
 * preview caching, etc.).
 */
val widgetModule = module {

    // ========================================================================
    // WIDGET INFRASTRUCTURE - SINGLETON
    // ========================================================================

    /**
     * WidgetHostManager - Wraps Android's AppWidgetHost framework.
     *
     * There should be EXACTLY ONE AppWidgetHost per launcher app. AppWidgetHost
     * is responsible for:
     * - Allocating widget IDs (each widget on the home screen gets a unique ID)
     * - Creating widget views (AppWidgetHostView) for rendering widgets
     * - Receiving widget update broadcasts from widget providers
     * - Managing the bind/configure flow when the user adds a new widget
     *
     * Having multiple AppWidgetHost instances would cause:
     * - Widget ID conflicts (two hosts allocating overlapping IDs)
     * - Duplicate update handling (widget updates delivered to both hosts)
     * - Resource leaks (each host registers broadcast receivers)
     *
     * SINGLETON: Yes — exactly one per app, for the reasons above.
     *
     * USED BY: MainActivity (starts/stops the host), HomeViewModel (widget placement),
     *          LauncherScreen (widget rendering)
     *
     * DEPENDENCY: Android Context + PackageChangeMonitor
     */
    single {
        WidgetHostManager(
            context = get(),
            packageChangeMonitor = get<PackageChangeMonitor>()
        )
    }

}
