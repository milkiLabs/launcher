package com.milki.launcher.presentation.drawer

sealed interface DrawerTransitionState {
    data object Closed : DrawerTransitionState
    data class Opening(val progress: Float) : DrawerTransitionState
    data object Open : DrawerTransitionState
    data class Searching(val query: String) : DrawerTransitionState
    data class Closing(val progress: Float) : DrawerTransitionState
}
