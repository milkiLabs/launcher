package com.milki.launcher.data.repository.apps

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Emits a signal whenever launcher-relevant package broadcasts are received.
 *
 * Contract: [events] is a best-effort change *signal*, not a reliable
 * per-package event stream. Events may be coalesced or dropped under burst
 * (e.g. bulk installs), and no ordering/delivery guarantee is made between
 * packages. Consumers must treat every emission as "something changed" and
 * refresh accordingly; never act on a single event as if it were exhaustive.
 *
 * The receiver lives for the process lifetime (singleton) and is never
 * unregistered by design.
 */
class PackageChangeMonitor(
    private val application: Application
) {

    private val packageSignal = MutableSharedFlow<PackageChangeEvent>(
        replay = 0,
        // Sized to absorb install/update bursts (e.g. Play Store batch
        // updates); overflow still drops oldest, which is safe because
        // consumers perform full refreshes — see class contract above.
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<PackageChangeEvent> = packageSignal

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                packageSignal.tryEmit(PackageChangeEvent.fromIntent(intent))
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
        }
    }
}

data class PackageChangeEvent(
    val packageName: String?,
    val action: String?
) {
    val isInitialLoad: Boolean
        get() = action == null

    companion object {
        val Initial = PackageChangeEvent(packageName = null, action = null)

        fun fromIntent(intent: Intent): PackageChangeEvent {
            return PackageChangeEvent(
                packageName = intent.data?.schemeSpecificPart,
                action = intent.action
            )
        }
    }
}
