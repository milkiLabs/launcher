package com.milki.launcher.presentation.home

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.model.WidgetDisplayMode
import com.milki.launcher.domain.repository.HomeRepository
import com.milki.launcher.presentation.home.prune.HomeAvailabilityPruner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Home screen ViewModel.
 *
 * This version keeps the write path straightforward:
 * - one mutation lock
 * - one writer
 * - one place that persists updates
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val availabilityPruner: HomeAvailabilityPruner,
    private val iconWarmupCoordinator: HomeIconWarmupCoordinator,
    private val widgetHostManager: WidgetHostManager
) : ViewModel() {

    private companion object {
        private const val AVAILABILITY_PRUNE_START_DELAY_MS = 1_500L
    }

    sealed interface WidgetPlacementCommand {
        data class LaunchBindPermission(val appWidgetId: Int, val intent: Intent) : WidgetPlacementCommand
        data class LaunchConfigure(val appWidgetId: Int) : WidgetPlacementCommand
        data object NoOp : WidgetPlacementCommand
    }

    private data class PendingWidget(
        val appWidgetId: Int,
        val providerComponent: ComponentName,
        val providerLabel: String,
        val targetPosition: GridPosition,
        val span: GridSpan,
        val displayMode: WidgetDisplayMode
    )

    private val modelWriter = HomeModelWriter()
    private val mutationMutex = Mutex()
    private val openFolderIdFlow = MutableStateFlow<String?>(null)
    private val pendingMutationCount = MutableStateFlow(0)
    private val _lastMoveErrorMessage = MutableStateFlow<String?>(null)
    private val pendingWidgets = linkedMapOf<Int, PendingWidget>()
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

    val isUpdatingPositions = pendingMutationCount
        .map { pendingUpdates -> pendingUpdates > 0 }
        .stateIn(
            scope = viewModelScope,
            started = ViewModelSharingStarted,
            initialValue = false
        )

    val lastMoveErrorMessage: StateFlow<String?> = _lastMoveErrorMessage

    private suspend fun tryApplyCommand(
        command: HomeModelWriter.Command,
        fallbackErrorMessage: String,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ): Boolean {
        return try {
            applyWriterCommand(command, onApplied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastMoveErrorMessage.value = e.message ?: fallbackErrorMessage
            false
        }
    }

    private fun launchMutation(
        fallbackErrorMessage: String,
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ) {
        viewModelScope.launch {
            pendingMutationCount.update { it + 1 }

            try {
                _lastMoveErrorMessage.value = null

                val wasApplied = tryApplyCommand(command, fallbackErrorMessage, onApplied)

                if (!wasApplied && _lastMoveErrorMessage.value == null) {
                    _lastMoveErrorMessage.value = fallbackErrorMessage
                }
            } finally {
                pendingMutationCount.update { current -> (current - 1).coerceAtLeast(0) }
            }
        }
    }

    private suspend fun applyWriterCommand(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ): Boolean {
        return mutationMutex.withLock {
            val currentItems = homeRepository.readPinnedItems()
            when (
                val result = modelWriter.apply(
                    currentItems = currentItems,
                    command = command
                )
            ) {
                is HomeModelWriter.Result.Applied -> {
                    persistUpdatedItems(currentItems, result.items)
                    onApplied(result.items)
                    true
                }

                is HomeModelWriter.Result.Rejected -> false
            }
        }
    }

    private suspend fun persistUpdatedItems(
        currentItems: List<HomeItem>,
        updatedItems: List<HomeItem>
    ) {
        if (updatedItems == currentItems) {
            return
        }

        homeRepository.replacePinnedItems(updatedItems)

        val openFolderId = openFolderIdFlow.value
        if (openFolderId != null && updatedItems.none { it.id == openFolderId }) {
            openFolderIdFlow.value = null
        }
    }

    fun moveItemToPosition(itemId: String, newPosition: GridPosition) {
        launchMutation(
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
        launchMutation(
            fallbackErrorMessage = "Target position is occupied",
            command = HomeModelWriter.PinOrMoveToPosition(
                item = item,
                targetPosition = dropPosition
            )
        )
    }

    internal fun pinFile(file: FileDocument) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin file",
            command = HomeModelWriter.AddPinnedItem(
                item = HomeItem.PinnedFile.fromFileDocument(file)
            )
        )
    }

    internal fun pinContact(contact: Contact) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin contact",
            command = HomeModelWriter.AddPinnedItem(
                item = HomeItem.PinnedContact.fromContact(contact)
            )
        )
    }

    fun pinAppShortcut(shortcut: HomeItem.AppShortcut) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin shortcut",
            command = HomeModelWriter.AddPinnedItem(item = shortcut)
        )
    }

    internal fun unpinItem(itemId: String) {
        launchRemoveItemsById(setOf(itemId), fallbackErrorMessage = "Failed to remove item")
    }

    private fun launchRemoveItemsById(
        itemIds: Set<String>,
        fallbackErrorMessage: String = "Failed to remove unavailable items"
    ) {
        launchMutation(
            fallbackErrorMessage = fallbackErrorMessage,
            command = HomeModelWriter.RemoveItemsById(itemIds = itemIds)
        )
    }

    private suspend fun removeUnavailableItemsById(itemIds: Set<String>) {
        applyWriterCommand(
            command = HomeModelWriter.RemoveItemsById(itemIds = itemIds)
        )
    }

    fun clearMoveError() {
        _lastMoveErrorMessage.value = null
    }

    fun openFolder(folderId: String) {
        openFolderIdFlow.value = folderId
    }

    fun closeFolder() {
        openFolderIdFlow.value = null
    }

    fun createFolder(item1: HomeItem, item2: HomeItem, atPosition: GridPosition) {
        launchMutation(
            fallbackErrorMessage = "Could not create folder",
            command = HomeModelWriter.CreateFolder(
                draggedItem = item1,
                targetItemId = item2.id,
                atPosition = atPosition
            )
        )
    }

    fun addItemToFolder(folderId: String, item: HomeItem) {
        launchMutation(
            fallbackErrorMessage = "Could not add item to folder",
            command = HomeModelWriter.AddItemToFolder(
                folderId = folderId,
                item = item
            )
        )
    }

    fun removeItemFromFolder(folderId: String, itemId: String) {
        launchMutation(
            fallbackErrorMessage = "Could not remove item from folder",
            command = HomeModelWriter.RemoveItemFromFolder(
                folderId = folderId,
                itemId = itemId
            )
        )
    }

    fun reorderFolderItems(folderId: String, newChildren: List<HomeItem>) {
        launchMutation(
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
        launchMutation(
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
        launchMutation(
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
        launchMutation(
            fallbackErrorMessage = "Could not merge folders",
            command = HomeModelWriter.MergeFolders(
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId
            )
        )
    }

    fun renameFolder(folderId: String, newName: String) {
        launchMutation(
            fallbackErrorMessage = "Could not rename folder",
            command = HomeModelWriter.RenameFolder(
                folderId = folderId,
                newName = newName
            )
        )
    }

    fun extractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
        launchMutation(
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
        val existingWidget = pinnedItems.value.filterIsInstance<HomeItem.WidgetItem>().firstOrNull {
            it.providerPackage == providerInfo.provider.packageName &&
            it.providerClass == providerInfo.provider.className
        }

        if (existingWidget != null) {
            val updatedWidget = existingWidget.withDisplayMode(displayMode).withSpan(span)
            pinOrMoveHomeItemToPosition(updatedWidget, targetPosition)
            return WidgetPlacementCommand.NoOp
        }

        val appWidgetId = widgetHostManager.allocateWidgetId()
        val bindOptions = widgetHostManager.createBindOptions(span)
        pendingWidgets[appWidgetId] = PendingWidget(
            appWidgetId = appWidgetId,
            providerComponent = providerInfo.provider,
            providerLabel = widgetHostManager.loadProviderLabel(providerInfo),
            targetPosition = targetPosition,
            span = span,
            displayMode = displayMode
        )

        val boundImmediately = widgetHostManager.bindWidget(
            appWidgetId = appWidgetId,
            providerInfo = providerInfo,
            options = bindOptions
        )

        return if (boundImmediately) {
            resolvePostBindCommand(appWidgetId)
        } else {
            WidgetPlacementCommand.LaunchBindPermission(
                appWidgetId = appWidgetId,
                intent = widgetHostManager.createBindPermissionIntent(
                    appWidgetId = appWidgetId,
                    providerInfo = providerInfo,
                    options = bindOptions
                )
            )
        }
    }

    fun handleWidgetBindResult(
        resultCode: Int,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            resolvePostBindCommand(appWidgetId)
        } else {
            cancelPendingWidget(pending)
            WidgetPlacementCommand.NoOp
        }
    }

    fun handleWidgetConfigureResult(
        resultCode: Int,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            persistPendingWidget(appWidgetId, pending)
            WidgetPlacementCommand.NoOp
        } else {
            cancelPendingWidget(pending)
            WidgetPlacementCommand.NoOp
        }
    }

    fun removeWidget(widgetId: String) {
        launchMutation(
            fallbackErrorMessage = "Could not remove widget",
            command = HomeModelWriter.RemoveItemsById(itemIds = setOf(widgetId)),
            onApplied = {
                widgetId.substringAfter("widget:", "").toIntOrNull()?.let(widgetHostManager::deallocateWidgetId)
            }
        )
    }

    fun updateWidgetFrame(
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) {
        launchMutation(
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
        launchMutation(
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
        launchMutation(
            fallbackErrorMessage = "Cannot show full widget",
            command = HomeModelWriter.ExpandPopupWidget(
                widgetId = widgetId,
                visibleRows = visibleRows
            )
        )
    }

    private fun resolvePostBindCommand(appWidgetId: Int): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        val boundProviderInfo = widgetHostManager.getProviderInfo(appWidgetId)
        if (boundProviderInfo == null) {
            cancelPendingWidget(pending)
            return WidgetPlacementCommand.NoOp
        }

        return if (widgetHostManager.needsConfigure(appWidgetId)) {
            WidgetPlacementCommand.LaunchConfigure(appWidgetId = appWidgetId)
        } else {
            persistPendingWidget(appWidgetId, pending)
            WidgetPlacementCommand.NoOp
        }
    }

    private fun persistPendingWidget(
        appWidgetId: Int,
        pending: PendingWidget
    ) {
        val widgetItem = HomeItem.WidgetItem.create(
            appWidgetId = pending.appWidgetId,
            providerPackage = pending.providerComponent.packageName,
            providerClass = pending.providerComponent.className,
            label = pending.providerLabel,
            position = pending.targetPosition,
            span = pending.span,
            displayMode = pending.displayMode
        )

        pendingWidgets.remove(appWidgetId)

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            pendingMutationCount.update { it + 1 }
            var shouldDeallocate = true

            try {
                _lastMoveErrorMessage.value = null

                val wasApplied = tryApplyCommand(
                    command = HomeModelWriter.PinOrMoveToPosition(
                        item = widgetItem,
                        targetPosition = pending.targetPosition
                    ),
                    fallbackErrorMessage = "Could not place widget"
                )

                shouldDeallocate = !wasApplied
                if (!wasApplied) {
                    _lastMoveErrorMessage.value = "Could not place widget"
                }
            } finally {
                if (shouldDeallocate) {
                    widgetHostManager.deallocateWidgetId(pending.appWidgetId)
                }
                pendingMutationCount.update { current -> (current - 1).coerceAtLeast(0) }
            }
        }
    }

    private fun cancelPendingWidget(pending: PendingWidget) {
        widgetHostManager.deallocateWidgetId(pending.appWidgetId)
        pendingWidgets.remove(pending.appWidgetId)
    }
}
