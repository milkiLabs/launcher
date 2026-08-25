package com.milki.launcher.core.crash

import kotlin.system.exitProcess

/**
 * Global uncaught-exception handler that persists a crash report to disk
 * before handing control back to the system's default crash flow.
 *
 * Installing it in [com.milki.launcher.app.LauncherApplication.onCreate]
 * means even startup crashes (Koin init, activity launch) get recorded.
 *
 * The previous default handler is preserved and invoked at the end, so the
 * standard Android "app has stopped" behaviour is untouched.
 */
class GlobalCrashHandler(
    private val writer: CrashLogWriter,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            writer.writeCrashReport(thread, throwable)
        }
        previousHandler?.uncaughtException(thread, throwable) ?: exitProcess(10)
    }

    companion object {
        /**
         * Installs the handler. Safe to call exactly once from Application.onCreate;
         * returns the previously active handler for testing purposes.
         */
        fun install(writer: CrashLogWriter): Thread.UncaughtExceptionHandler? {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(writer, previous))
            return previous
        }
    }
}
