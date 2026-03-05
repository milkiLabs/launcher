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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.Contact
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    private val homeRepository: HomeRepository
) : ViewModel(), HomeMutationHandler {

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
            // PERFORMANCE OPTIMIZATION:
            // We intentionally avoid pre-reading pinnedItems from Flow here.
            // Repository moveItemToPositionIfEmpty already validates:
            // - item exists
            // - destination occupancy
            // - no-op when destination is unchanged
            // This prevents duplicate deserialize cycles during drag operations.
            homeRepository.moveItemToPositionIfEmpty(itemId, newPosition)
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
            val wasApplied = homeRepository.pinOrMoveItemToPosition(
                item = item,
                targetPosition = dropPosition
            )

            wasApplied
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
            // PERFORMANCE OPTIMIZATION:
            // Skip pre-read duplicate check here. Repository addPinnedItem already
            // performs duplicate detection inside one edit transaction.
            homeRepository.addPinnedItem(pinnedApp)
            true
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
            // PERFORMANCE OPTIMIZATION:
            // Repository-level duplicate protection makes pre-read unnecessary.
            homeRepository.addPinnedItem(pinnedFile)
            true
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
            // PERFORMANCE OPTIMIZATION:
            // Repository-level duplicate protection makes pre-read unnecessary.
            homeRepository.addPinnedItem(pinnedContact)
            true
        }
    }

    /**
     * Unpin an item through the same serialized mutation path.
     */
    override fun unpinItem(itemId: String) {
        launchSerializedHomeMutation(
            fallbackErrorMessage = "Failed to remove item"
        ) {
            homeRepository.removePinnedItem(itemId)
            true
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
            val folder = homeRepository.createFolder(item1, item2, atPosition)
            folder != null
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
            homeRepository.addItemToFolder(folderId = folderId, item = item)
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
            val remaining = homeRepository.removeItemFromFolder(folderId, itemId)
            // Close the popup if the folder was deleted (remaining == null).
            if (remaining == null) {
                openFolderIdFlow.value = null
            }
            true
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
            homeRepository.reorderFolderItems(folderId, newChildren)
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
            // Single atomic repository transaction: remove from source, apply source
            // cleanup policy, then insert into target folder children.
            homeRepository.moveItemBetweenFolders(
                sourceFolderId = sourceFolderId,
                targetFolderId = targetFolderId,
                itemId = itemId
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
            // Single atomic repository transaction:
            // 1) remove child from source folder + apply cleanup policy
            // 2) remove occupant from top-level cell
            // 3) create new folder at the occupied cell
            val folder = homeRepository.extractFolderChildOntoItem(
                sourceFolderId = sourceFolderId,
                childItemId = childItem.id,
                occupantItem = occupantItem,
                atPosition = atPosition
            )
            folder != null
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
            homeRepository.mergeFolders(sourceFolderId, targetFolderId)
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
            homeRepository.renameFolder(folderId, newName)
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
            val applied = homeRepository.extractItemFromFolder(folderId, itemId, targetPosition)
            if (applied) {
                // Always close the popup when a drag-out succeeds.
                //
                // WHY NOT RELY ON THE COMBINE:
                // The combine sets openFolderItem=null only when the folder is no longer
                // present in pinnedItems (i.e. cleanup policy deleted/unwrapped it because
                // ≤1 child remained). If the folder still has 2+ children after the
                // drag-out, it stays in pinnedItems and the combine never closes the popup.
                //
                // The drag-out gesture ends the folder interaction regardless of how many
                // children remain, so we always dismiss here.
                openFolderIdFlow.value = null
            }
            applied
        }
    }
}
