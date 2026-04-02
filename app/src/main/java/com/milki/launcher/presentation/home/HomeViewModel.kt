package com.milki.launcher.presentation.home

import android.appwidget.AppWidgetProviderInfo
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Home screen ViewModel.
 *
 * This class intentionally stays thin and delegates heavy logic to focused
 * collaborators:
 * - HomeMutationCoordinator: serialized write path and error/loading bookkeeping
 * - HomeAvailabilityPruner: installed-app and file availability cleanup
 * - HomeWidgetPlacementController: bind/configure/place widget state machine
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    appRepository: AppRepository,
    appContext: Context
) : ViewModel(), HomeMutationHandler {

    private val modelWriter = HomeModelWriter()
    private val openFolderIdFlow = MutableStateFlow<String?>(null)

    private val mutationCoordinator = HomeMutationCoordinator(
        homeRepository = homeRepository,
        modelWriter = modelWriter,
        openFolderIdFlow = openFolderIdFlow,
        scope = viewModelScope
    )

    private val availabilityPruner = HomeAvailabilityPruner(
        appRepository = appRepository,
        appContext = appContext,
        homeRepository = homeRepository,
        modelWriter = modelWriter,
        mutationCoordinator = mutationCoordinator,
        scope = viewModelScope
    )

    private val widgetPlacementController = HomeWidgetPlacementController(
        mutationCoordinator = mutationCoordinator
    )

    init {
        availabilityPruner.start()
    }

    override fun onCleared() {
        availabilityPruner.stop()
        super.onCleared()
    }

    /**
     * Derived home UI state composed from repository data and mutation metadata.
     */
    val uiState = combine(
        homeRepository.pinnedItems,
        mutationCoordinator.pendingPositionUpdateCount,
        mutationCoordinator.lastMoveErrorMessage,
        openFolderIdFlow
    ) { items, pendingUpdates, moveErrorMessage, openFolderId ->
        HomeUiState(
            pinnedItems = items,
            isLoading = false,
            isUpdatingPositions = pendingUpdates > 0,
            lastMoveErrorMessage = moveErrorMessage,
            openFolderItem = if (openFolderId != null) {
                items.firstOrNull { it.id == openFolderId } as? HomeItem.FolderItem
            } else {
                null
            }
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )

    private fun launchWriterCommand(
        fallbackErrorMessage: String,
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ) {
        mutationCoordinator.launchSerializedMutation(
            fallbackErrorMessage = fallbackErrorMessage,
            coalescingKey = command.coalescingKey()
        ) {
            mutationCoordinator.applyWriterCommand(
                command = command,
                onApplied = onApplied
            )
        }
    }

    fun moveItemToPosition(itemId: String, newPosition: GridPosition) {
        launchWriterCommand(
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
        launchWriterCommand(
            fallbackErrorMessage = "Target position is occupied",
            command = HomeModelWriter.Command.PinOrMoveToPosition(
                item = item,
                targetPosition = dropPosition
            )
        )
    }

    override fun pinApp(appInfo: AppInfo) {
        launchWriterCommand(
            fallbackErrorMessage = "Failed to pin app",
            command = HomeModelWriter.Command.AddPinnedItem(
                item = HomeItem.PinnedApp.fromAppInfo(appInfo)
            )
        )
    }

    override fun pinFile(file: FileDocument) {
        launchWriterCommand(
            fallbackErrorMessage = "Failed to pin file",
            command = HomeModelWriter.Command.AddPinnedItem(
                item = HomeItem.PinnedFile.fromFileDocument(file)
            )
        )
    }

    override fun pinContact(contact: Contact) {
        launchWriterCommand(
            fallbackErrorMessage = "Failed to pin contact",
            command = HomeModelWriter.Command.AddPinnedItem(
                item = HomeItem.PinnedContact.fromContact(contact)
            )
        )
    }

    override fun unpinItem(itemId: String) {
        launchWriterCommand(
            fallbackErrorMessage = "Failed to remove item",
            command = HomeModelWriter.Command.RemoveItemById(itemId = itemId)
        )
    }

    fun clearMoveError() {
        mutationCoordinator.clearMoveError()
    }

    fun openFolder(folderId: String) {
        openFolderIdFlow.value = folderId
    }

    fun closeFolder() {
        openFolderIdFlow.value = null
    }

    fun createFolder(item1: HomeItem, item2: HomeItem, atPosition: GridPosition) {
        launchWriterCommand(
            fallbackErrorMessage = "Could not create folder",
            command = HomeModelWriter.Command.CreateFolder(
                draggedItem = item1,
                targetItemId = item2.id,
                atPosition = atPosition
            )
        )
    }

    fun addItemToFolder(folderId: String, item: HomeItem) {
        launchWriterCommand(
            fallbackErrorMessage = "Could not add item to folder",
            command = HomeModelWriter.Command.AddItemToFolder(
                folderId = folderId,
                item = item
            )
        )
    }

    fun removeItemFromFolder(folderId: String, itemId: String) {
        launchWriterCommand(
            fallbackErrorMessage = "Could not remove item from folder",
            command = HomeModelWriter.Command.RemoveItemFromFolder(
                folderId = folderId,
                itemId = itemId
            )
        )
    }

    fun reorderFolderItems(folderId: String, newChildren: List<HomeItem>) {
        launchWriterCommand(
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
        launchWriterCommand(
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
        launchWriterCommand(
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
        launchWriterCommand(
            fallbackErrorMessage = "Could not merge folders",
            command = HomeModelWriter.Command.MergeFolders(
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId
            )
        )
    }

    fun renameFolder(folderId: String, newName: String) {
        launchWriterCommand(
            fallbackErrorMessage = "Could not rename folder",
            command = HomeModelWriter.Command.RenameFolder(
                folderId = folderId,
                newName = newName
            )
        )
    }

    fun extractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
        launchWriterCommand(
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

    sealed interface WidgetPlacementCommand {
        data class LaunchBindPermission(val appWidgetId: Int, val intent: Intent) : WidgetPlacementCommand
        data class LaunchConfigure(val appWidgetId: Int) : WidgetPlacementCommand
        data object NoOp : WidgetPlacementCommand
    }

    fun startWidgetPlacement(
        providerInfo: AppWidgetProviderInfo,
        targetPosition: GridPosition,
        span: GridSpan,
        widgetHostManager: WidgetHostManager
    ): WidgetPlacementCommand {
        return widgetPlacementController.startWidgetPlacement(
            providerInfo = providerInfo,
            targetPosition = targetPosition,
            span = span,
            widgetHostManager = widgetHostManager
        )
    }

    fun handleWidgetBindResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        return widgetPlacementController.handleWidgetBindResult(
            resultCode = resultCode,
            widgetHostManager = widgetHostManager,
            appWidgetId = appWidgetId
        )
    }

    fun handleWidgetConfigureResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        return widgetPlacementController.handleWidgetConfigureResult(
            resultCode = resultCode,
            widgetHostManager = widgetHostManager,
            appWidgetId = appWidgetId
        )
    }

    fun removeWidget(
        widgetId: String,
        widgetHostManager: WidgetHostManager
    ) {
        mutationCoordinator.launchSerializedMutation(
            fallbackErrorMessage = "Could not remove widget"
        ) {
            val appWidgetId = widgetId
                .substringAfter(delimiter = "widget:", missingDelimiterValue = "")
                .toIntOrNull()
                ?: return@launchSerializedMutation false

            mutationCoordinator.applyWriterCommand(
                command = HomeModelWriter.Command.RemoveItemById(itemId = widgetId),
                onApplied = {
                    widgetHostManager.deallocateWidgetId(appWidgetId)
                }
            )
        }
    }

    fun updateWidgetFrame(
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) {
        launchWriterCommand(
            fallbackErrorMessage = "Cannot update widget - cells are occupied",
            command = HomeModelWriter.Command.UpdateWidgetFrame(
                widgetId = widgetId,
                newPosition = newPosition,
                newSpan = newSpan
            )
        )
    }

    private fun HomeModelWriter.Command.coalescingKey(): String? {
        return when (this) {
            is HomeModelWriter.Command.MoveTopLevelItem -> "move:$itemId"
            is HomeModelWriter.Command.PinOrMoveToPosition -> "pin-or-move:${item.id}"
            is HomeModelWriter.Command.UpdateWidgetFrame -> "widget-frame:$widgetId"
            else -> null
        }
    }
}
