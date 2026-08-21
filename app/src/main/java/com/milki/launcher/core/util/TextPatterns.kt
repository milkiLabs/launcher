package com.milki.launcher.core.util

/**
 * Text pattern helpers that mirror android.util.Patterns without
 * pulling Android framework types into non-Android modules.
 */
object TextPatterns {

    /**
     * Matches email addresses, equivalent to android.util.Patterns.EMAIL_ADDRESS.
     */
    val EMAIL_ADDRESS: Regex =
        Regex("[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+")
}
