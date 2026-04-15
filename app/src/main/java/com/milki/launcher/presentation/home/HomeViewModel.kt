package com.milki.launcher.presentation.home

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.data.widget.WidgetHostManager
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.GridSpan
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HomeRepository
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
    appRepository: AppRepository,
    private val appContext: Context
) : ViewModel(), HomeMutationHandler {

    private companion object {
        private const val ICON_WARMUP_START_DELAY_MS = 250L
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
        val span: GridSpan
    )

    private val modelWriter = HomeModelWriter()
    private val mutationMutex = Mutex()
    private val openFolderIdFlow = MutableStateFlow<String?>(null)
    private val pendingMutationCount = MutableStateFlow(0)
    private val _lastMoveErrorMessage = MutableStateFlow<String?>(null)
    private val pendingWidgets = linkedMapOf<Int, PendingWidget>()
    private var deferredStartupJob: Job? = null

    private val availabilityPruner = HomeAvailabilityPruner(
        appRepository = appRepository,
        appContext = appContext,
        homeRepository = homeRepository,
        modelWriter = modelWriter,
        mutationMutex = mutationMutex,
        persistUpdatedItems = ::persistUpdatedItems,
        scope = viewModelScope
    )

    private val iconWarmupCoordinator = HomeIconWarmupCoordinator(
        homeRepository = homeRepository,
        appContext = appContext,
        packageManager = appContext.packageManager,
        scope = viewModelScope
    )

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
            delay(ICON_WARMUP_START_DELAY_MS)
            iconWarmupCoordinator.start()

            // App availability pruning can trigger full installed-app scans.
            // Delay it to avoid contending with first-draw startup work.
            delay(AVAILABILITY_PRUNE_START_DELAY_MS)
            availabilityPruner.start()
        }
    }

    val pinnedItems = homeRepository.pinnedItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
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
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val isUpdatingPositions = pendingMutationCount
        .map { pendingUpdates -> pendingUpdates > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val lastMoveErrorMessage: StateFlow<String?> = _lastMoveErrorMessage

    private fun launchMutation(
        fallbackErrorMessage: String,
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ) {
        viewModelScope.launch {
            pendingMutationCount.update { it + 1 }
            _lastMoveErrorMessage.value = null

            val wasApplied = runCatching {
                applyWriterCommand(command, onApplied)
            }.getOrElse { exception ->
                _lastMoveErrorMessage.value = exception.message ?: fallbackErrorMessage
                false
            }

            if (!wasApplied && _lastMoveErrorMessage.value == null) {
                _lastMoveErrorMessage.value = fallbackErrorMessage
            }

            pendingMutationCount.update { current -> (current - 1).coerceAtLeast(0) }
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
            command = HomeModelWriter.Command.MoveTopLevelItem(
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
            command = HomeModelWriter.Command.PinOrMoveToPosition(
                item = item,
                targetPosition = dropPosition
            )
        )
    }

    override fun pinApp(appInfo: AppInfo) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin app",
            command = HomeModelWriter.Command.AddPinnedItem(
                item = HomeItem.PinnedApp.fromAppInfo(appInfo)
            )
        )
    }

    override fun pinFile(file: FileDocument) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin file",
            command = HomeModelWriter.Command.AddPinnedItem(
                item = HomeItem.PinnedFile.fromFileDocument(file)
            )
        )
    }

    override fun pinContact(contact: Contact) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin contact",
            command = HomeModelWriter.Command.AddPinnedItem(
                item = HomeItem.PinnedContact.fromContact(contact)
            )
        )
    }

    fun pinAppShortcut(shortcut: HomeItem.AppShortcut) {
        launchMutation(
            fallbackErrorMessage = "Failed to pin shortcut",
            command = HomeModelWriter.Command.AddPinnedItem(item = shortcut)
        )
    }

    override fun unpinItem(itemId: String) {
        launchMutation(
            fallbackErrorMessage = "Failed to remove item",
            command = HomeModelWriter.Command.RemoveItemById(itemId = itemId)
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
            command = HomeModelWriter.Command.CreateFolder(
                draggedItem = item1,
                targetItemId = item2.id,
                atPosition = atPosition
            )
        )
    }

    fun addItemToFolder(folderId: String, item: HomeItem) {
        launchMutation(
            fallbackErrorMessage = "Could not add item to folder",
            command = HomeModelWriter.Command.AddItemToFolder(
                folderId = folderId,
                item = item
            )
        )
    }

    fun removeItemFromFolder(folderId: String, itemId: String) {
        launchMutation(
            fallbackErrorMessage = "Could not remove item from folder",
            command = HomeModelWriter.Command.RemoveItemFromFolder(
                folderId = folderId,
                itemId = itemId
            )
        )
    }

    fun reorderFolderItems(folderId: String, newChildren: List<HomeItem>) {
        launchMutation(
            fallbackErrorMessage = "Could not reorder folder items",
            command = HomeModelWriter.Command.ReorderFolderItems(
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
            command = HomeModelWriter.Command.MoveItemBetweenFolders(
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
            command = HomeModelWriter.Command.ExtractFolderChildOntoItem(
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
            command = HomeModelWriter.Command.MergeFolders(
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId
            )
        )
    }

    fun renameFolder(folderId: String, newName: String) {
        launchMutation(
            fallbackErrorMessage = "Could not rename folder",
            command = HomeModelWriter.Command.RenameFolder(
                folderId = folderId,
                newName = newName
            )
        )
    }

    fun extractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
        launchMutation(
            fallbackErrorMessage = "Target position is occupied",
            command = HomeModelWriter.Command.ExtractItemFromFolder(
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
        widgetHostManager: WidgetHostManager
    ): WidgetPlacementCommand {
        val appWidgetId = widgetHostManager.allocateWidgetId()
        val bindOptions = widgetHostManager.createBindOptions(span)
        pendingWidgets[appWidgetId] = PendingWidget(
            appWidgetId = appWidgetId,
            providerComponent = providerInfo.provider,
            providerLabel = widgetHostManager.loadProviderLabel(providerInfo),
            targetPosition = targetPosition,
            span = span
        )

        val boundImmediately = widgetHostManager.bindWidget(
            appWidgetId = appWidgetId,
            providerInfo = providerInfo,
            options = bindOptions
        )

        return if (boundImmediately) {
            resolvePostBindCommand(appWidgetId, widgetHostManager)
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
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            resolvePostBindCommand(appWidgetId, widgetHostManager)
        } else {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            WidgetPlacementCommand.NoOp
        }
    }

    fun handleWidgetConfigureResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        return if (resultCode == Activity.RESULT_OK) {
            persistPendingWidget(appWidgetId, pending, widgetHostManager)
            WidgetPlacementCommand.NoOp
        } else {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            WidgetPlacementCommand.NoOp
        }
    }

    fun removeWidget(
        widgetId: String,
        widgetHostManager: WidgetHostManager
    ) {
        launchMutation(
            fallbackErrorMessage = "Could not remove widget",
            command = HomeModelWriter.Command.RemoveItemById(itemId = widgetId),
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
            command = HomeModelWriter.Command.UpdateWidgetFrame(
                widgetId = widgetId,
                newPosition = newPosition,
                newSpan = newSpan
            )
        )
    }

    private fun resolvePostBindCommand(
        appWidgetId: Int,
        widgetHostManager: WidgetHostManager
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        val boundProviderInfo = widgetHostManager.getProviderInfo(appWidgetId)
        if (boundProviderInfo == null) {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            return WidgetPlacementCommand.NoOp
        }

        return if (widgetHostManager.needsConfigure(appWidgetId)) {
            WidgetPlacementCommand.LaunchConfigure(appWidgetId = appWidgetId)
        } else {
            persistPendingWidget(appWidgetId, pending, widgetHostManager)
            WidgetPlacementCommand.NoOp
        }
    }

    private fun persistPendingWidget(
        appWidgetId: Int,
        pending: PendingWidget,
        widgetHostManager: WidgetHostManager
    ) {
        val widgetItem = HomeItem.WidgetItem.create(
            appWidgetId = pending.appWidgetId,
            providerPackage = pending.providerComponent.packageName,
            providerClass = pending.providerComponent.className,
            label = pending.providerLabel,
            position = pending.targetPosition,
            span = pending.span
        )

        pendingWidgets.remove(appWidgetId)

        viewModelScope.launch {
            pendingMutationCount.update { it + 1 }
            _lastMoveErrorMessage.value = null

            val wasApplied = runCatching {
                applyWriterCommand(
                    command = HomeModelWriter.Command.PinOrMoveToPosition(
                        item = widgetItem,
                        targetPosition = pending.targetPosition
                    )
                )
            }.getOrElse { false }

            if (!wasApplied) {
                widgetHostManager.deallocateWidgetId(pending.appWidgetId)
                _lastMoveErrorMessage.value = "Could not place widget"
            }

            pendingMutationCount.update { current -> (current - 1).coerceAtLeast(0) }
        }
    }

    private fun cancelPendingWidget(
        appWidgetId: Int,
        widgetHostManager: WidgetHostManager,
        pending: PendingWidget
    ) {
        widgetHostManager.deallocateWidgetId(pending.appWidgetId)
        pendingWidgets.remove(appWidgetId)
    }
}
