package com.milki.launcher.ui.components.launcher.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.milki.launcher.data.widget.WidgetAppGroup
import com.milki.launcher.data.widget.WidgetPickerCatalogStore

internal data class WidgetPickerCatalogUiState(
    val isLoading: Boolean,
    val appGroups: List<WidgetAppGroup>
)

@Composable
internal fun rememberWidgetPickerCatalogUiState(
    catalogStore: WidgetPickerCatalogStore
): WidgetPickerCatalogUiState {
    val initialCatalog = catalogStore.peek()
    return produceState(
        initialValue = WidgetPickerCatalogUiState(
            isLoading = initialCatalog == null,
            appGroups = initialCatalog.orEmpty()
        ),
        catalogStore
    ) {
        val cachedCatalog = catalogStore.peek()
        if (cachedCatalog != null) {
            value = WidgetPickerCatalogUiState(
                isLoading = false,
                appGroups = cachedCatalog
            )
        } else {
            value = WidgetPickerCatalogUiState(
                isLoading = true,
                appGroups = emptyList()
            )
            value = WidgetPickerCatalogUiState(
                isLoading = false,
                appGroups = catalogStore.await()
            )
        }
    }.value
}
