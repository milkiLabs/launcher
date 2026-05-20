package com.milki.launcher.presentation.common

import kotlinx.coroutines.flow.SharingStarted

/**
 * Standard [SharingStarted] policy for ViewModel state flows.
 *
 * Keeps the flow active for 5 seconds after the last subscriber disconnects,
 * balancing between recomposition speed and resource usage.
 */
val ViewModelSharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5_000)
