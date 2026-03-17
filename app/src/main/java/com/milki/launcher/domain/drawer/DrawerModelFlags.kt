package com.milki.launcher.domain.drawer

/**
 * Metadata describing why a drawer model update was emitted.
 */
data class DrawerModelFlags(
    val source: String = "unknown",
    val deferredReason: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
