package com.milki.launcher.presentation.drawer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drawer transition state-machine used by host surfaces.
 */
class DrawerSurfaceController {

    private val _state = MutableStateFlow<DrawerTransitionState>(DrawerTransitionState.Closed)
    val state: StateFlow<DrawerTransitionState> = _state.asStateFlow()

    fun requestOpen() {
        _state.value = DrawerTransitionState.Open
    }

    fun requestClose() {
        _state.value = DrawerTransitionState.Closed
    }

    fun setSearchMode(query: String?) {
        if (query.isNullOrBlank()) {
            if (_state.value is DrawerTransitionState.Searching) {
                _state.value = DrawerTransitionState.Open
            }
            return
        }
        _state.value = DrawerTransitionState.Searching(query = query)
    }

    fun updateTransitionProgress(progress: Float, opening: Boolean) {
        val clamped = progress.coerceIn(0f, 1f)
        _state.value = if (opening) {
            if (clamped >= 1f) DrawerTransitionState.Open else DrawerTransitionState.Opening(clamped)
        } else {
            if (clamped <= 0f) DrawerTransitionState.Closed else DrawerTransitionState.Closing(clamped)
        }
    }

    fun isVisible(): Boolean {
        return when (_state.value) {
            DrawerTransitionState.Closed -> false
            is DrawerTransitionState.Closing -> false
            else -> true
        }
    }
}
