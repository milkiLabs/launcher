/**
 * Global [Application] class for the Milki Launcher.
 * * Handles early-stage initialization, specifically Dependency Injection with Koin.
 * Ensure this is registered in the `AndroidManifest.xml` under `android:name`.
 */
package com.milki.launcher.app

// ============================================================================
// IMPORTS - Android Framework
// ============================================================================
import android.app.Application

// ============================================================================
// IMPORTS - Koin (Dependency Injection)
// ============================================================================
import com.milki.launcher.core.di.allModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeKoin()
    }

     /**
     * Sets up the Koin DI container with the application context and app modules.
     */
    private fun initializeKoin() {
        startKoin {
            // Provide the Application context to Koin
            // This is used by dependencies that need Context (repositories, etc.)
            androidContext(this@LauncherApplication)
            
            // Logs Koin errors to Android logcat
            androidLogger(Level.ERROR)
            
            // Load our dependency modules
            // allModules aggregates all feature modules: core, search, home, widget, settings, drawer
            // Each module defines dependencies for its own feature, keeping the DI graph organized.
            modules(allModules)
        }
    }
}
