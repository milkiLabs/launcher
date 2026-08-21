package com.milki.launcher.ui.components.launcher.widget

import androidx.compose.runtime.staticCompositionLocalOf
import com.milki.launcher.domain.widget.WidgetHostPort

/**
 * Process-wide access to the widget host from Compose.
 *
 * The host is a process singleton (exactly one AppWidgetHost per launcher), so
 * instead of threading it as a parameter through every intermediate composable,
 * consumers that actually touch widgets read it here. It is provided once at
 * the composition root and is null only in previews/tests.
 */
val LocalWidgetHost = staticCompositionLocalOf<WidgetHostPort?> { null }
