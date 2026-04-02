/**
 * HomeViewModel.kt - ViewModel for the home screen pinned items
 *
 * Manages the state of pinned items on the home screen, including their
 * positions on the grid.
 *
 * ACTION HANDLING:
 * All pinning/unpinning actions are handled by ActionExecutor via SearchResultAction:
 * - SearchResultAction.PinApp: Pins an app to the home screen
 * - SearchResultAction.PinFile: Pins a file to the home screen
 * - SearchResultAction.PinContact: Pins a contact to the home screen
 * - SearchResultAction.UnpinItem: Removes an item from the home screen
 *
 * This ViewModel now acts as the single home mutation coordinator.
 * UI actions still originate from LocalSearchActionHandler + ActionExecutor,
 * but ActionExecutor routes home write actions back into this ViewModel through
 * HomeMutationHandler, so ordering stays serialized in one place.
 *
 * POSITION MANAGEMENT:
 * Each item has a grid position (row, column). The ViewModel provides
 * functions to move items to new positions when the user drags them.
 */

package com.milki.launcher.presentation.home

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UI State for the home screen.
 *
 * @property pinnedItems List of items currently pinned to the home screen.
 *                       This includes both standalone icons AND folder icons.
 * @property isLoading Whether the initial load is in progress
 * @property isUpdatingPositions Whether any position-update operation is in flight
 * @property lastMoveErrorMessage Optional error message from the last failed move operation
 * @property openFolderItem The FolderItem whose popup is currently displayed, or null if
 *                          no folder is open. This is derived from [pinnedItems]: whenever
 *                          the folder data changes (e.g. an item is dragged in/out), the
 *                          popup automatically reflects the latest children.
 */
data class HomeUiState(
    val pinnedItems: List<HomeItem> = emptyList(),
    val isLoading: Boolean = true,
    val isUpdatingPositions: Boolean = false,
    val lastMoveErrorMessage: String? = null,
    val openFolderItem: HomeItem.FolderItem? = null
)

/**
 * ViewModel for the home screen.
 *
 * RESPONSIBILITIES:
 * - Observe pinned items from the repository
 * - Provide pinned items state to the UI
 * - Handle all home layout writes (move/pin/unpin/drop) through one serialized path
 *
 * Note: Pinning/unpinning actions are handled by ActionExecutor via SearchResultAction.
 * This separation keeps the action handling logic centralized and consistent.
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository
) : ViewModel(), HomeMutationHandler {

    private data class InstalledAppAvailability(
        val validPackages: Set<String>,
        val validPinnedAppComponents: Set<String>
    )

    private val modelWriter = HomeModelWriter()

    /**
     * Number of currently running position-update operations.
     *
     * WHY COUNTER INSTEAD OF BOOLEAN:
     * Multiple drag-drop updates can be triggered close together.
     * A counter allows us to represent true in-flight state even when
     * operations overlap briefly.
     */
    private val pendingPositionUpdateCount = MutableStateFlow(0)

    /**
     * Latest user-visible move error message.
     *
     * This stays nullable to avoid forcing UI to show any message by default.
     */
    private val lastMoveErrorMessage = MutableStateFlow<String?>(null)

    /**
     * Mutex used to serialize DataStore position writes.
     *
     * PAIN POINT REMOVED:
     * Rapid drag-drop interactions could trigger overlapping writes.
     * DataStore itself is safe, but serialization here gives deterministic
     * ordering and avoids race-like visual churn.
     */
    private val positionUpdateMutex = Mutex()

    /**
     * The ID of the folder whose popup dialog is currently open, or null when
     * no folder is open.
     *
     * WHY STORE THE ID INSTEAD OF THE FULL ITEM:
     * The folder's children can change while the popup is open (e.g. the user
     * drags another item into the folder). Storing just the ID and deriving
     * [HomeUiState.openFolderItem] from [pinnedItems] ensures the popup always
     * displays the latest state without a separate emit.
     */
    private val openFolderIdFlow = MutableStateFlow<String?>(null)

    /**
     * Execute a home-layout mutation through one serialized coordinator path.
     *
     * WHY THIS HELPER EXISTS:
     * - Guarantees ordering across all home layout writes handled by this ViewModel.
     * - Keeps loading/error bookkeeping consistent.
     * - Prevents multiple call sites from duplicating mutex/try-catch logic.
     */
    private fun launchSerializedHomeMutation(
        fallbackErrorMessage: String,
        mutation: suspend () -> Boolean
    ) {
        viewModelScope.launch {
            positionUpdateMutex.withLock {
                pendingPositionUpdateCount.update { current -> current + 1 }
                lastMoveErrorMessage.value = null

                try {
                    val wasApplied = mutation()
                    if (!wasApplied) {
                        lastMoveErrorMessage.value = fallbackErrorMessage
                    }
                } catch (exception: Exception) {
                    lastMoveErrorMessage.value = exception.message ?: fallbackErrorMessage
                } finally {
                    pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    private suspend fun applyWriterCommand(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ): Boolean {
        val currentItems = homeRepository.pinnedItems.first()
        return when (
            val result = modelWriter.apply(
                currentItems = currentItems,
                command = command
            )
        ) {
            is HomeModelWriter.Result.Applied -> {
                if (result.items != currentItems) {
                    homeRepository.replacePinnedItems(result.items)
                }
                onApplied(result.items)
                true
            }
            is HomeModelWriter.Result.Rejected -> false
        }
    }

    /**
     * UI state derived from source streams.
     *
     * Sources:
     * 1) Repository pinned items
     * 2) Position update in-flight count
     * 3) Last position update error message
     * 4) Currently open folder ID (null = no folder open)
     *
     * This keeps UI rendering deterministic and avoids imperative state mutation.
     *
     * WHY openFolderItem IS DERIVED HERE:
     * When the user drags items into or out of a folder while the folder popup is
     * open, the repository emits a new [pinnedItems] list. By finding the open
     * folder by ID inside this combine, the popup automatically reflects the change
     * without needing a separate state update call.
     */
    val uiState = combine(
        homeRepository.pinnedItems,
        pendingPositionUpdateCount,
        lastMoveErrorMessage,
        openFolderIdFlow
    ) { items, pendingUpdates, moveErrorMessage, openFolderId ->
        HomeUiState(
            pinnedItems = items,
            isLoading = false,
            isUpdatingPositions = pendingUpdates > 0,
            lastMoveErrorMessage = moveErrorMessage,
            // Derive the open folder from the latest pinnedItems so the popup
            // always reflects current folder content (especially after drag operations).
            openFolderItem = if (openFolderId != null) {
                items.firstOrNull { it.id == openFolderId } as? HomeItem.FolderItem
            } else null
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )

    init {
        observeAppAvailabilityAndPruneUnavailableItems()
    }

    /**
     * Keeps home layout aligned with currently installed apps.
     *
     * This removes stale app-backed items after uninstall/update events:
     * - PinnedApp entries whose launcher component no longer exists
     * - AppShortcut entries whose parent package no longer exists
     * - WidgetItem entries whose provider package no longer exists
     */
    private fun observeAppAvailabilityAndPruneUnavailableItems() {
        val installedAvailability = appRepository.observeInstalledApps()
            .filter { installedApps -> installedApps.isNotEmpty() }
            .map(::buildInstalledAppAvailability)

        viewModelScope.launch {
            combine(
                homeRepository.pinnedItems,
                installedAvailability
            ) { pinnedItems, availability -> pinnedItems to availability }
                .collectLatest { (_, availability) ->
                    pruneUnavailableItems(availability = availability)
                }
        }
    }

    private fun buildInstalledAppAvailability(installedApps: List<AppInfo>): InstalledAppAvailability {
        return InstalledAppAvailability(
            validPackages = installedApps.mapTo(mutableSetOf()) { it.packageName },
            validPinnedAppComponents = installedApps.mapTo(mutableSetOf()) {
                ComponentName(it.packageName, it.activityName).flattenToString()
            }
        )
    }

    private suspend fun pruneUnavailableItems(availability: InstalledAppAvailability) {
        positionUpdateMutex.withLock {
            val currentItems = homeRepository.pinnedItems.first()
            if (currentItems.isEmpty()) {
                return@withLock
            }

            val unavailableItemIds = collectUnavailableItemIds(
                items = currentItems,
                validPackages = availability.validPackages,
                validPinnedAppComponents = availability.validPinnedAppComponents
            )
            if (unavailableItemIds.isEmpty()) {
                return@withLock
            }

            when (
                val result = modelWriter.apply(
                    currentItems = currentItems,
                    command = HomeModelWriter.Command.RemoveItemsById(itemIds = unavailableItemIds.toSet())
                )
            ) {
                is HomeModelWriter.Result.Applied -> {
                    val updatedItems = result.items
                    if (updatedItems != currentItems) {
                        homeRepository.replacePinnedItems(updatedItems)
                        val openFolderId = openFolderIdFlow.value
                        if (openFolderId != null && updatedItems.none { it.id == openFolderId }) {
                            openFolderIdFlow.value = null
                        }
                    }
                }
                is HomeModelWriter.Result.Rejected -> Unit
            }
        }
    }

    private fun collectUnavailableItemIds(
        items: List<HomeItem>,
        validPackages: Set<String>,
        validPinnedAppComponents: Set<String>
    ): List<String> {
        val unavailableIds = linkedSetOf<String>()

        fun visit(item: HomeItem) {
            when (item) {
                is HomeItem.PinnedApp -> {
                    val componentName = ComponentName(item.packageName, item.activityName).flattenToString()
                    if (componentName !in validPinnedAppComponents) {
                        unavailableIds += item.id
                    }
                }
                is HomeItem.AppShortcut -> {
                    if (item.packageName !in validPackages) {
                        unavailableIds += item.id
                    }
                }
                is HomeItem.WidgetItem -> {
                    if (item.providerPackage !in validPackages) {
                        unavailableIds += item.id
                    }
                }
                is HomeItem.FolderItem -> item.children.forEach(::visit)
                is HomeItem.PinnedFile,
                is HomeItem.PinnedContact -> Unit
            }
        }

        items.forEach(::visit)
        return unavailableIds.toList()
    }

    /**
     * Moves an item to a new grid position.
     *
        * This is called when the user finishes dragging an item.
        * If the target position is occupied by another item, the move is rejected.
     *
     * @param itemId The ID of the item to move
     * @param newPosition The new grid position (row, column)
     */
    fun moveItemToPosition(itemId: String, newPosition: GridPosition) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Target position is occupied or item no longer exists"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.MoveTopLevelItem(
                    itemId = itemId,
                    newPosition = newPosition
                )
            )
        }
    }

    /**
     * Pins a dropped app if needed, or moves the already-pinned app to the drop position.
     *
        * DROP HANDLING RULES:
        * 1) If the app is not currently pinned: pin directly into the requested cell.
        * 2) If the app is already pinned: move existing item to that cell.
        * 3) If another item already occupies the target cell: reject the drop.
        *
        * WHY THIS IS ATOMIC:
        * Repository performs pin-or-move in one edit transaction to avoid
        * transient "added in wrong slot then moved" intermediate states.
     *
     * @param appInfo The dropped app payload from drag-and-drop
     * @param dropPosition The target grid position where user released the drag
     */
    fun pinOrMoveAppToPosition(appInfo: AppInfo, dropPosition: GridPosition) {
        pinOrMoveHomeItemToPosition(
            item = HomeItem.PinnedApp.fromAppInfo(appInfo),
            dropPosition = dropPosition
        )
    }

    /**
     * Pins or moves any supported home item to the exact target position.
     *
     * This method is used by external drag/drop for app, file, and contact payloads.
     */
    fun pinOrMoveHomeItemToPosition(item: HomeItem, dropPosition: GridPosition) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Target position is occupied"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.PinOrMoveToPosition(
                    item = item,
                    targetPosition = dropPosition
                )
            )
        }
    }

    /**
     * Pin an app from search/home actions through the same serialized mutation path.
     */
    override fun pinApp(appInfo: AppInfo) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to pin app"
        ) {
            val pinnedApp = HomeItem.PinnedApp.fromAppInfo(appInfo)
            applyWriterCommand(command = HomeModelWriter.Command.AddPinnedItem(item = pinnedApp))
        }
    }

    /**
     * Pin a file shortcut through the same serialized mutation path.
     */
    override fun pinFile(file: FileDocument) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to pin file"
        ) {
            val pinnedFile = HomeItem.PinnedFile.fromFileDocument(file)
            applyWriterCommand(command = HomeModelWriter.Command.AddPinnedItem(item = pinnedFile))
        }
    }

    /**
     * Pin a contact shortcut through the same serialized mutation path.
     */
    override fun pinContact(contact: Contact) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to pin contact"
        ) {
            val pinnedContact = HomeItem.PinnedContact.fromContact(contact)
            applyWriterCommand(command = HomeModelWriter.Command.AddPinnedItem(item = pinnedContact))
        }
    }

    /**
     * Unpin an item through the same serialized mutation path.
     */
    override fun unpinItem(itemId: String) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to remove item"
        ) {
            applyWriterCommand(command = HomeModelWriter.Command.RemoveItemById(itemId = itemId))
        }
    }

    /**
     * Clear the last move error after the UI has consumed it.
     *
     * This keeps error signaling explicit and avoids sticky stale messages.
     */
    fun clearMoveError() {
        lastMoveErrorMessage.value = null
    }

    // ========================================================================
    // FOLDER UI STATE — open / close
    // ========================================================================

    /**
     * Opens the folder popup for the folder with the given [folderId].
     *
     * This sets [openFolderIdFlow] so that the [uiState] combine derives
     * the [HomeUiState.openFolderItem] and the UI shows the popup.
     *
     * The actual content shown in the popup always comes from the latest
     * [pinnedItems] emission, so it is always up-to-date.
     *
     * @param folderId The [HomeItem.FolderItem.id] whose popup should open.
     */
    fun openFolder(folderId: String) {
        openFolderIdFlow.value = folderId
    }

    /**
     * Closes the folder popup by clearing the open folder ID.
     *
     * After this call, [HomeUiState.openFolderItem] becomes null and
     * the FolderPopupDialog will be hidden.
     */
    fun closeFolder() {
        openFolderIdFlow.value = null
    }

    // ========================================================================
    // FOLDER MUTATIONS — write operations through serialized path
    // ========================================================================

    /**
     * Creates a new folder from two existing home screen items.
     *
     * Called when the user drags one home screen icon onto another.
     * Both items are removed from the home grid and placed inside the new folder.
     * The folder icon appears at [atPosition] but does NOT open automatically —
     * the user can tap it to open it.
     *
     * @param item1 The dragged item
     * @param item2 The item that was dropped onto
     * @param atPosition The home screen cell where the new folder will appear
     */
    fun createFolder(item1: HomeItem, item2: HomeItem, atPosition: GridPosition) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not create folder"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.CreateFolder(
                    draggedItem = item1,
                    targetItemId = item2.id,
                    atPosition = atPosition
                )
            )
        }
    }

    /**
     * Adds a home screen item into a folder.
     *
     * Called when:
     * - The user drags a non-folder home item onto a folder icon on the home screen.
     * - The user drops an item from the search dialog onto a folder cell.
     *
     * @param folderId The [HomeItem.FolderItem.id] to add the item into
     * @param item The item to place inside the folder
     */
    fun addItemToFolder(folderId: String, item: HomeItem) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not add item to folder"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.AddItemToFolder(
                    folderId = folderId,
                    item = item
                )
            )
        }
    }

    /**
     * Removes an item from inside a folder.
     *
     * Called when the user long-presses an item inside the folder popup and
     * selects "Remove from folder".
     *
     * Applies the cleanup policy:
     * - If the folder ends up empty → folder is deleted, popup closes.
     * - If one item remains → folder unwrapped, popup closes.
     * - Otherwise → popup stays open with updated children.
     *
     * @param folderId The folder containing the item
     * @param itemId The id of the item to remove
     */
    fun removeItemFromFolder(folderId: String, itemId: String) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not remove item from folder"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.RemoveItemFromFolder(
                    folderId = folderId,
                    itemId = itemId
                ),
                onApplied = { items ->
                    if (items.none { it.id == folderId }) {
                        openFolderIdFlow.value = null
                    }
                }
            )
        }
    }

    /**
     * Reorders the children inside a folder (internal drag-and-drop).
     *
     * Called after the user drags an icon to a new position inside the folder popup.
     *
     * @param folderId The folder whose children should be reordered.
     * @param newChildren The complete children list in the new display order.
     */
    fun reorderFolderItems(folderId: String, newChildren: List<HomeItem>) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not reorder folder items"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.ReorderFolderItems(
                    folderId = folderId,
                    newChildren = newChildren
                )
            )
        }
    }

    /**
     * Moves an item from one folder directly into another folder.
     *
     * Called when the user drags an icon out of a folder popup and drops it
     * onto a DIFFERENT folder icon on the home grid.
     *
     * WHY A DEDICATED METHOD:
     * Using [extractItemFromFolder] + [addItemToFolder] in sequence is wrong because
     * [extractItemFromFolder] places the item on the HOME GRID at the drop position
     * (which is already occupied by the target folder), causing a position collision.
     * This method keeps the item off the grid entirely — it just moves it between
     * the two folders' children lists in a single serialized mutation.
     *
     * CLEANUP POLICY (applied to the source folder):
     * - 0 children remaining → source folder deleted
     * - 1 child remaining   → source folder unwrapped to plain icon
     * - 2+ children         → source folder updated in place
     *
     * @param sourceFolderId The folder the item is coming FROM
     * @param itemId         The ID of the item being moved
     * @param item           The actual [HomeItem] to add to the target folder
     * @param targetFolderId The folder the item is going INTO
     */
    fun moveItemBetweenFolders(
        sourceFolderId: String,
        itemId: String,
        targetFolderId: String
    ) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not move item between folders"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.MoveItemBetweenFolders(
                    sourceFolderId = sourceFolderId,
                    targetFolderId = targetFolderId,
                    itemId = itemId
                )
            )
        }
    }

    /**
     * Extracts an item from a folder and creates a NEW folder with it and
     * an existing non-folder home grid icon at the same cell position.
     *
     * This is called when the user drags an icon OUT of a folder popup and
     * drops it DIRECTLY ONTO another (non-folder) icon on the home grid.
     * The expected result is the same as dragging two normal icons together:
     * a brand new folder appears at [atPosition] containing both items.
     *
     * HOW IT WORKS — STEP BY STEP:
     *
     * Step 1 — Remove [childItem] from its source folder.
     *   [childItem] lives inside its folder's children list.  It is NOT present
     *   in the flat [pinnedItems] grid list, because folder children are stored
     *   separately inside [HomeItem.FolderItem.children].
     *   After removal, the source folder's cleanup policy is applied:
     *     - 0 children remaining → the source folder is auto-deleted from the grid.
     *     - 1 child remaining    → the folder is "unwrapped" to a plain icon.
     *     - 2+ children          → folder stays, just minus this one item.
     *
     * Step 2 — Create a new folder from [childItem] + [occupantItem] at [atPosition].
     *   [HomeRepository.createFolder] does several things:
     *     a) Looks for both items in the flat pinnedItems list and removes them.
     *        For [childItem], this search is a no-op (it was never in the flat list).
     *        For [occupantItem], it IS in the flat list and gets removed from there.
     *     b) Creates a new [HomeItem.FolderItem] containing both items.
     *     c) Places the new folder at [atPosition] in the flat pinnedItems list.
     *
     * WHY NOT reuse [extractItemFromFolder] + [createFolder] as separate mutations?
     *   They are separate DataStore writes, which could cause a race condition and
     *   a brief UI flash.  [launchSerializedHomeMutation] ensures both writes
     *   happen back-to-back in the same coroutine, eliminating that risk.
     *
     * @param sourceFolderId The folder the [childItem] is being dragged OUT of.
     * @param childItem      The item being dragged — the folder child.
     * @param occupantItem   The home grid item being dropped ONTO (not a folder).
     * @param atPosition     The grid cell where the NEW folder will appear.
     *                       This is the same position [occupantItem] occupies.
     */
    fun extractFolderChildOntoItem(
        sourceFolderId: String,
        childItem: HomeItem,
        occupantItem: HomeItem,
        atPosition: GridPosition
    ) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not create folder from drag"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.ExtractFolderChildOntoItem(
                    sourceFolderId = sourceFolderId,
                    childItemId = childItem.id,
                    targetItemId = occupantItem.id,
                    atPosition = atPosition
                )
            )
        }
    }

    /**
     * Merges two folders together.
     *
     * Called when the user drags one folder icon onto another.
     * The source folder is deleted; its children are appended to the target.
     *
     * @param sourceFolderId The folder being dragged (will be deleted)
     * @param targetFolderId The folder being dropped onto (will receive merged children)
     */
    fun mergeFolders(sourceFolderId: String, targetFolderId: String) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not merge folders"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.MergeFolders(
                    sourceFolderId = sourceFolderId,
                    targetFolderId = targetFolderId
                )
            )
        }
    }

    /**
     * Renames a folder.
     *
     * Called when the user finishes editing the folder title text field in the popup.
     *
     * @param folderId The folder to rename
     * @param newName The new name (blank names are defaulted to "Folder" by the repository)
     */
    fun renameFolder(folderId: String, newName: String) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not rename folder"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.RenameFolder(
                    folderId = folderId,
                    newName = newName
                )
            )
        }
    }

    /**
     * Extracts an item from inside a folder and places it on the home screen.
     *
     * This is the "drag-out" operation: the user dragged an icon out of the folder
     * popup and released it on the home screen grid.
     *
     * - Applies cleanup policy: folder is deleted/unwrapped if ≤1 child remains.
     * - If the target cell is occupied by another item, the operation is rejected.
     * - If the folder is deleted by cleanup, the popup is closed automatically.
     *
     * @param folderId The source folder
     * @param itemId The item being dragged out
     * @param targetPosition The home screen cell where the item should land
     */
    fun extractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Target position is occupied"
        ) {
            applyWriterCommand(
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
    }

    // ========================================================================
    // WIDGET OPERATIONS
    // ========================================================================

    /**
     * Holds the state for a widget that is currently going through the
     * multi-step bind → configure → place flow.
     *
     * WHY THIS EXISTS:
     * Adding a widget is NOT a single step — it requires:
     * 1) Allocating an appWidgetId from the host
     * 2) Binding the widget (may require a system permission dialog)
     * 3) Optionally launching the widget's configure Activity
     * 4) Finally placing it on the home grid
     *
     * Between steps 2→3→4, the Activity is backgrounded while the system
     * dialog / configure Activity is shown.  We need to remember which
     * widget the user was setting up so the ActivityResult callbacks can
     * complete the flow.
     */
    private data class PendingWidget(
        val appWidgetId: Int,
        val providerComponent: ComponentName,
        val providerLabel: String,
        val targetPosition: GridPosition,
        val span: GridSpan
    )

    private val pendingWidgets = linkedMapOf<Int, PendingWidget>()

    /**
     * Result command returned by widget placement state-machine functions.
     *
     * MainActivity executes these commands by launching the corresponding
     * ActivityResult contract. `NoOp` means no further UI-side action is needed.
     */
    sealed interface WidgetPlacementCommand {
        data class LaunchBindPermission(val appWidgetId: Int, val intent: Intent) : WidgetPlacementCommand
        data class LaunchConfigure(val appWidgetId: Int) : WidgetPlacementCommand
        data object NoOp : WidgetPlacementCommand
    }

    /**
     * Starts bind → configure → place for a newly dropped widget.
     *
     * This method returns a command instead of taking callbacks so the Activity
     * stays a thin dispatcher for bind/configure UI work.
     */
    fun startWidgetPlacement(
        providerInfo: AppWidgetProviderInfo,
        targetPosition: GridPosition,
        span: GridSpan,
        widgetHostManager: WidgetHostManager
    ): WidgetPlacementCommand {
        val appWidgetId = widgetHostManager.allocateWidgetId()
        val bindOptions = widgetHostManager.createBindOptions(span)
        val pending = PendingWidget(
            appWidgetId = appWidgetId,
            providerComponent = providerInfo.provider,
            providerLabel = widgetHostManager.loadProviderLabel(providerInfo),
            targetPosition = targetPosition,
            span = span
        )
        pendingWidgets[appWidgetId] = pending

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
                widgetHostManager.createBindPermissionIntent(
                    appWidgetId = appWidgetId,
                    providerInfo = providerInfo,
                    options = bindOptions
                )
            )
        }
    }

    /**
     * Called when the bind-permission Activity returns a result.
     *
     * If the user granted permission (RESULT_OK), we continue the flow.
     * Otherwise we clean up the allocated widget ID.
     */
    fun handleWidgetBindResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        if (resultCode == Activity.RESULT_OK) {
            return resolvePostBindCommand(appWidgetId, widgetHostManager)
        } else {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            return WidgetPlacementCommand.NoOp
        }
    }

    /**
     * Called when the widget's configure Activity returns a result.
     *
     * If the user completed configuration (RESULT_OK), we place the widget.
     * Otherwise we clean up.
     */
    fun handleWidgetConfigureResult(
        resultCode: Int,
        widgetHostManager: WidgetHostManager,
        appWidgetId: Int
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp

        return if (resultCode == Activity.RESULT_OK) {
            persistWidget(appWidgetId, pending, widgetHostManager)
            WidgetPlacementCommand.NoOp
        } else {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            WidgetPlacementCommand.NoOp
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

    /**
     * After binding succeeds, check whether the widget has a configure Activity
     * and either launch it or place the widget directly.
     */
    private fun resolvePostBindCommand(
        appWidgetId: Int,
        widgetHostManager: WidgetHostManager
    ): WidgetPlacementCommand {
        val pending = pendingWidgets[appWidgetId] ?: return WidgetPlacementCommand.NoOp
        val boundProviderInfo = widgetHostManager.getProviderInfo(pending.appWidgetId)
        if (boundProviderInfo == null) {
            cancelPendingWidget(appWidgetId, widgetHostManager, pending)
            return WidgetPlacementCommand.NoOp
        }

        return if (widgetHostManager.needsConfigure(pending.appWidgetId)) {
            WidgetPlacementCommand.LaunchConfigure(appWidgetId = pending.appWidgetId)
        } else {
            persistWidget(appWidgetId, pending, widgetHostManager)
            WidgetPlacementCommand.NoOp
        }
    }

    /**
     * Final step: persist the widget in the home repository.
     *
     * Creates a [HomeItem.WidgetItem] from the pending state and adds it
     * through the serialized mutation path.
     */
    private fun persistWidget(
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

        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not place widget"
        ) {
            val applied = applyWriterCommand(
                command = HomeModelWriter.Command.PinOrMoveToPosition(
                    item = widgetItem,
                    targetPosition = pending.targetPosition
                )
            )
            if (!applied) {
                widgetHostManager.deallocateWidgetId(pending.appWidgetId)
            }
            applied
        }
    }

    /**
     * Removes a widget from the home screen and releases its appWidgetId.
     *
     * @param widgetId The [HomeItem.WidgetItem.id] (format: "widget:{appWidgetId}")
     * @param widgetHostManager The host manager to release the ID from
     */
    fun removeWidget(
        widgetId: String,
        widgetHostManager: WidgetHostManager
    ) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Could not remove widget"
        ) {
            val appWidgetId = widgetId
                .substringAfter(delimiter = "widget:", missingDelimiterValue = "")
                .toIntOrNull()
                ?: return@launchSerializedHomeMutation false
            applyWriterCommand(
                command = HomeModelWriter.Command.RemoveItemById(itemId = widgetId),
                onApplied = {
                    widgetHostManager.deallocateWidgetId(appWidgetId)
                }
            )
        }
    }

    /**
     * Updates a widget's full frame on the home grid.
     *
     * This is used by widget edit mode, where moving and resizing are part of
     * the same interaction and should commit atomically.
     */
    fun updateWidgetFrame(
        widgetId: String,
        newPosition: GridPosition,
        newSpan: GridSpan
    ) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Cannot update widget — cells are occupied"
        ) {
            applyWriterCommand(
                command = HomeModelWriter.Command.UpdateWidgetFrame(
                    widgetId = widgetId,
                    newPosition = newPosition,
                    newSpan = newSpan
                )
            )
        }
    }
}
