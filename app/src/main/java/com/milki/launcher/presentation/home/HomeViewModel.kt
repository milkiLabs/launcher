package com.milki.launcher.presentation.home

import android.appwidget.AppWidgetProviderInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.domain.widget.WidgetHostPort
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.ItemId
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.presentation.home.prune.HomeAvailabilityPruner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home screen ViewModel.
 *
 * A thin aggregator over focused collaborators:
 * - [HomeModelMutator] owns the write path (lock, writer, persistence, errors)
 * - [WidgetPlacementManager] owns the widget placement state machine
 * - folder-open state and startup/pruning wiring stay here (UI-scoped)
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val availabilityPruner: HomeAvailabilityPruner,
    private val iconWarmupCoordinator: HomeIconWarmupCoordinator,
    private val widgetHost: WidgetHostPort
) : ViewModel() {

    private companion object {
        private const val AVAILABILITY_PRUNE_START_DELAY_MS = 1_500L
    }

    private val openFolderIdFlow = MutableStateFlow<String?>(null)

    private val modelMutator = HomeModelMutator(
        homeRepository = homeRepository,
        scope = viewModelScope,
        onItemsPersisted = ::onItemsPersisted
    )

    private val widgetPlacementManager = WidgetPlacementManager(
        modelMutator = modelMutator,
        widgetHost = widgetHost,
        scope = viewModelScope,
        pinnedItemsProvider = { pinnedItems.value }
    )

    private var deferredStartupJob: Job? = null

    init {
        // Start icon warmup immediately so home screen icons begin loading
        // as soon as DataStore emits pinned items — not after an artificial delay.
        iconWarmupCoordinator.start(viewModelScope)
    }

    override fun onCleared() {
        deferredStartupJob?.cancel()
        availabilityPruner.stop()
        super.onCleared()
    }

    fun startDeferredStartupWork() {
        if (deferredStartupJob != null) {
            return
        }

        deferredStartupJob = viewModelScope.launch {
            // App availability pruning can trigger full installed-app scans.
            // Delay it to avoid contending with first-draw startup work.
            delay(AVAILABILITY_PRUNE_START_DELAY_MS)
            availabilityPruner.start(
                scope = viewModelScope,
                readItems = homeRepository::readPinnedItems,
                removeItemsById = ::removeUnavailableItemsById
            )
        }
    }

    val pinnedItems = homeRepository.pinnedItems.stateIn(
        scope = viewModelScope,
        started = ViewModelSharingStarted,
        initialValue = emptyList()
    )

    val openFolderItem = combine(
        pinnedItems,
        openFolderIdFlow
    ) { items, openFolderId ->
        if (openFolderId != null) {
            items.firstOrNull { it.id == openFolderId } as? HomeItem.FolderItem
        } else {
            null
        }
    }.stateIn(
        scope = viewModelScope,
        started = ViewModelSharingStarted,
        initialValue = null
    )

    val isUpdatingPositions: StateFlow<Boolean> = modelMutator.isUpdatingPositions

    val lastMoveErrorMessage: StateFlow<String?> = modelMutator.lastMoveErrorMessage

    fun clearMoveError() {
        modelMutator.clearMoveError()
    }

    fun moveItemToPosition(itemId: String, newPosition: GridPosition) {
        modelMutator.mutate(
            fallbackErrorMessage = "Target position is occupied or item no longer exists",
            command = HomeModelWriter.MoveTopLevelItem(
                itemId = itemId,
                newPosition = newPosition
            )
        )
    }

    fun pinOrMoveAppToPosition(appInfo: AppInfo, dropPosition: GridPosition) {
        pinOrMoveHomeItemToPosition(
            item = HomeItem.PinnedApp.fromAppInfo(appInfo),
            dropPosition = dropPosition
        )
    }

    fun pinOrMoveHomeItemToPosition(item: HomeItem, dropPosition: GridPosition) {
        modelMutator.mutate(
            fallbackErrorMessage = "Target position is occupied",
            command = HomeModelWriter.PinOrMoveToPosition(
                item = item,
                targetPosition = dropPosition
            )
        )
    }

    internal fun pinFile(file: FileDocument) {
        modelMutator.mutate(
            fallbackErrorMessage = "Failed to pin file",
            command = HomeModelWriter.AddPinnedItem(
                item = HomeItem.PinnedFile.fromFileDocument(file)
            )
        )
    }

    internal fun pinContact(contact: Contact) {
        modelMutator.mutate(
            fallbackErrorMessage = "Failed to pin contact",
            command = HomeModelWriter.AddPinnedItem(
                item = HomeItem.PinnedContact.fromContact(contact)
            )
        )
    }

    fun pinAppShortcut(shortcut: HomeItem.AppShortcut) {
        modelMutator.mutate(
            fallbackErrorMessage = "Failed to pin shortcut",
            command = HomeModelWriter.AddPinnedItem(item = shortcut)
        )
    }

    internal fun unpinItem(itemId: String) {
        mutateRemoveItemsById(setOf(itemId), fallbackErrorMessage = "Failed to remove item")
    }

    private fun mutateRemoveItemsById(
        itemIds: Set<String>,
        fallbackErrorMessage: String
    ) {
        modelMutator.mutate(
            fallbackErrorMessage = fallbackErrorMessage,
            command = HomeModelWriter.RemoveItemsById(itemIds = itemIds)
        )
    }

    private suspend fun removeUnavailableItemsById(itemIds: Set<String>) {
        modelMutator.apply(
            command = HomeModelWriter.RemoveItemsById(itemIds = itemIds)
        )
    }

    fun openFolder(folderId: String) {
        openFolderIdFlow.value = folderId
    }

    fun closeFolder() {
        openFolderIdFlow.value = null
    }

    fun createFolder(item1: HomeItem, item2: HomeItem, atPosition: GridPosition) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not create folder",
            command = HomeModelWriter.CreateFolder(
                draggedItem = item1,
                targetItemId = item2.id,
                atPosition = atPosition
            )
        )
    }

    fun addItemToFolder(folderId: String, item: HomeItem) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not add item to folder",
            command = HomeModelWriter.AddItemToFolder(
                folderId = folderId,
                item = item
            )
        )
    }

    fun removeItemFromFolder(folderId: String, itemId: String) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not remove item from folder",
            command = HomeModelWriter.RemoveItemFromFolder(
                folderId = folderId,
                itemId = itemId
            )
        )
    }

    fun reorderFolderItems(folderId: String, newChildren: List<HomeItem>) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not reorder folder items",
            command = HomeModelWriter.ReorderFolderItems(
                folderId = folderId,
                newChildren = newChildren
            )
        )
    }

    fun moveItemBetweenFolders(
        sourceFolderId: String,
        itemId: String,
        targetFolderId: String
    ) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not move item between folders",
            command = HomeModelWriter.MoveItemBetweenFolders(
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId,
                itemId = itemId
            )
        )
    }

    fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItem: HomeItem,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not create folder from drag",
            command = HomeModelWriter.ExtractFolderChildOntoItem(
                sourceFolderId = sourceFolderId,
                childItemId = childItem.id,
                targetItemId = occupantItem.id,
                atPosition = atPosition
            )
        )
    }

    fun mergeFolders(sourceFolderId: String, targetFolderId: String) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not merge folders",
            command = HomeModelWriter.MergeFolders(
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId
            )
        )
    }

    fun renameFolder(folderId: String, newName: String) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not rename folder",
            command = HomeModelWriter.RenameFolder(
                folderId = folderId,
                newName = newName
            )
        )
    }

    fun extractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
        modelMutator.mutate(
            fallbackErrorMessage = "Target position is occupied",
            command = HomeModelWriter.ExtractItemFromFolder(
                folderId = folderId,
                itemId = itemId,
                targetPosition = targetPosition
            ),
            onApplied = {
                openFolderIdFlow.value = null
            }
        )
    }

    fun startWidgetPlacement(
        providerInfo: AppWidgetProviderInfo,
        targetPosition: GridPosition,
        span: GridSpan,
        displayMode: WidgetDisplayMode = WidgetDisplayMode.Inline
    ): WidgetPlacementCommand {
        return widgetPlacementManager.startWidgetPlacement(
            providerInfo = providerInfo,
            targetPosition = targetPosition,
            span = span,
            displayMode = displayMode
        )
    }

    fun handleWidgetBindResult(
        resultCode: Int,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        return widgetPlacementManager.handleWidgetBindResult(resultCode, appWidgetId)
    }

    fun handleWidgetConfigureResult(
        resultCode: Int,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        return widgetPlacementManager.handleWidgetConfigureResult(resultCode, appWidgetId)
    }

    fun removeWidget(widgetId: String) {
        modelMutator.mutate(
            fallbackErrorMessage = "Could not remove widget",
            command = HomeModelWriter.RemoveItemsById(itemIds = setOf(widgetId)),
            onApplied = {
                ItemId.widgetId(widgetId)?.let(widgetHost::deallocateWidgetId)
            }
        )
    }

    fun updateWidgetFrame(
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) {
        modelMutator.mutate(
            fallbackErrorMessage = "Cannot update widget - cells are occupied",
            command = HomeModelWriter.UpdateWidgetFrame(
                widgetId = widgetId,
                newPosition = newPosition,
                newSpan = newSpan
            )
        )
    }

    fun updateWidgetDisplayMode(
        widgetId: String,
        displayMode: WidgetDisplayMode
    ) {
        modelMutator.mutate(
            fallbackErrorMessage = "Cannot update widget display mode",
            command = HomeModelWriter.UpdateWidgetDisplayMode(
                widgetId = widgetId,
                displayMode = displayMode
            )
        )
    }

    fun expandPopupWidget(
        widgetId: String,
        visibleRows: Int
    ) {
        modelMutator.mutate(
            fallbackErrorMessage = "Cannot show full widget",
            command = HomeModelWriter.ExpandPopupWidget(
                widgetId = widgetId,
                visibleRows = visibleRows
            )
        )
    }

    private suspend fun onItemsPersisted(updatedItems: List<HomeItem>) {
        val openFolderId = openFolderIdFlow.value
        if (openFolderId != null && updatedItems.none { it.id == openFolderId }) {
            openFolderIdFlow.value = null
        }
    }
}
