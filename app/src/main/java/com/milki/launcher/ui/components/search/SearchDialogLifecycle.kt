package com.milki.launcher.ui.components.search

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

private const val SEARCH_LOADING_INDICATOR_DELAY_MS = 300L
private const val APP_SEARCH_DIALOG_LOG_TAG = "AppSearchDialog"

@Composable
internal fun rememberDelayedSearchLoadingIndicator(isLoading: Boolean): Boolean {
    return produceState(
        initialValue = false,
        key1 = isLoading
    ) {
        if (!isLoading) {
            value = false
            return@produceState
        }

        delay(SEARCH_LOADING_INDICATOR_DELAY_MS)
        value = isLoading
    }.value
}

@Composable
internal fun SearchDialogFocusEffects(
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    lifecycleOwner: LifecycleOwner
) {
    LaunchedEffect(Unit) {
        requestDialogFocus(
            focusRequester = focusRequester,
            keyboardController = keyboardController
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                requestDialogFocus(
                    focusRequester = focusRequester,
                    keyboardController = keyboardController
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun requestDialogFocus(
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?
) {
    runCatching {
        focusRequester.requestFocus()
        keyboardController?.show()
    }.onFailure { error ->
        Log.w(APP_SEARCH_DIALOG_LOG_TAG, "Search dialog focus request skipped", error)
    }
}
