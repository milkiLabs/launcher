package com.milki.launcher.presentation.drawer

import androidx.compose.runtime.Immutable

@Immutable
data class DrawerSection(
    val key: String,
    val title: String,
    val startIndex: Int,
    val count: Int
)
