# Folder System

This document covers the complete folder feature on the home screen: how folders are
created, opened, reordered, renamed, and how items move between folders, the home grid,
and the search dialog.  It also documents every edge-case that was discovered and fixed
during development.

---

## Table of Contents

1. [Overview](#overview)
2. [Data Model](#data-model)
3. [Repository & Persistence](#repository--persistence)
4. [ViewModel](#viewmodel)
5. [Creating a Folder (Drag Two Icons Together)](#creating-a-folder)
6. [Opening a Folder](#opening-a-folder)
7. [FolderPopupDialog](#folderpopupdialog)
8. [Drag-Out: Moving an Icon from a Folder to the Home Grid](#drag-out)
9. [Internal Reorder Inside the Popup](#internal-reorder-inside-the-popup)
10. [Merging Two Folders](#merging-two-folders)
11. [Folder Cleanup Policy (Auto-Delete / Auto-Unwrap)](#folder-cleanup-policy)
12. [Cross-Location Drop Routing](#cross-location-drop-routing)
13. [Deduplication: Items Living in Multiple Places](#deduplication)
14. [Edge Cases Fixed](#edge-cases-fixed)
15. [Component Map](#component-map)

---

## Overview

A **folder** is a home screen item (`HomeItem.FolderItem`) that contains other home
screen items as its children.  Folders behave like regular pinned items on the grid
except:

- Tapping opens a **popup dialog** instead of launching an app.
- Dragging two non-folder icons on top of each other **creates** a folder.
- Dragging a non-folder icon on top of a folder **adds** it to the folder.
- Dragging one folder on top of another **merges** them.
- Dragging a folder onto a non-folder is **ignored** (no operation).
- If a folder ends up with **0 children** it is deleted automatically.
- If a folder ends up with **1 child** it is unwrapped back to a plain home item.

---

## Data Model

**File:** `domain/model/HomeItem.kt`

```kotlin
@Serializable
data class FolderItem(
    override val id: String,               // "folder:{uuid}"
    val name: String = "Folder",           // User-editable display name
    val children: List<HomeItem> = emptyList(),
    override val position: GridPosition = GridPosition.DEFAULT
) : HomeItem() {
    companion object {
        /** Creates a new FolderItem containing two seed items at the given position. */
        fun create(item1: HomeItem, item2: HomeItem, atPosition: GridPosition): FolderItem
    }
}
```

`FolderItem` participates in the same polymorphic kotlinx.serialization setup as the
other `HomeItem` subtypes.  It is stored inside the `"pinned_items_ordered"` DataStore
key alongside apps, files, and contacts.  Nesting (folder inside folder) is **not**
supported — every method that accepts an arbitrary `HomeItem` guards against this with
`if (item is HomeItem.FolderItem) return`.

---

## Repository & Persistence

**File:** `data/repository/HomeRepositoryImpl.kt`
**Interface:** `domain/repository/HomeRepository.kt`

All folder mutations are atomic DataStore `.edit { }` lambdas.  The full list of
operations:

| Method | What it does |
|---|---|
| `createFolder(item1, item2, atPosition)` | Removes both items from the flat list, creates a new `FolderItem` at `atPosition` containing them. |
| `addItemToFolder(folderId, item)` | Removes `item` from the flat list AND from any other folder it may be in (see [Deduplication](#deduplication)), then appends it to the target folder's children. |
| `removeItemFromFolder(folderId, itemId)` | Removes the child and applies the cleanup policy (see below). |
| `reorderFolderItems(folderId, newChildren)` | Replaces the folder's children list with the provided order. |
| `mergeFolders(sourceFolderId, targetFolderId)` | Appends all source children to the target folder, deletes the source folder. |
| `renameFolder(folderId, newName)` | Updates the `name` field in-place. |
| `extractItemFromFolder(folderId, itemId, targetPosition)` | Removes the child from the folder, pins it at `targetPosition` on the home grid, applies cleanup policy to the folder. |

### Folder Cleanup Policy

Applied inside `removeItemFromFolder` and `extractItemFromFolder` after every
child removal:

```
remaining children count
        0  →  delete the folder entirely
        1  →  unwrap: delete the folder, pin the single remaining child at the folder's position
       2+  →  update the folder in place (keep it)
```

### `evictItemFromFolderIfPresent` (private helper)

**File:** `HomeRepositoryImpl.kt`

Many write paths (pinning to grid, adding to a folder) need to first ensure the item
is not already living inside some folder.  Rather than duplicating the cleanup-policy
logic, a private helper `evictItemFromFolderIfPresent(items, itemId)` handles this:

```kotlin
// Scans all FolderItems in the flat list for a child with the given id.
// If found: removes it and applies the cleanup policy to the source folder.
// If not found: no-op.
private fun evictItemFromFolderIfPresent(items: MutableList<HomeItem>, itemId: String)
```

It is called in-place (on the already-deserialized mutable list) so everything stays
within a single DataStore `edit {}` block.  Callers that also do
`currentItems.removeAll { it.id == itemId }` for the flat-list case call
`evictItemFromFolderIfPresent` right after, giving full coverage: item removed from the
flat list and from any folder it lives in.

---

## ViewModel

**File:** `presentation/home/HomeViewModel.kt`

```kotlin
// Which folder is currently open in the popup (null = closed)
private val openFolderIdFlow: MutableStateFlow<String?> = MutableStateFlow(null)

// Derived from openFolderIdFlow + pinnedItems:
// delivers the live FolderItem object so the popup recomposes when children change
val uiState: StateFlow<HomeUiState>
    // HomeUiState.openFolderItem: HomeItem.FolderItem?
```

**Why `openFolderIdFlow` instead of storing the whole `FolderItem`:**
The `children` list inside a `FolderItem` changes whenever another item is added or
reordered.  By storing only the ID and joining it with `pinnedItems` in `combine {}`
the popup automatically recomposes with the latest children without any extra
coordination.

### Folder ViewModel methods

| Method | Calls repository |
|---|---|
| `createFolder(item1, item2, atPosition)` | `homeRepository.createFolder(...)` |
| `openFolder(folderId)` | sets `openFolderIdFlow.value = folderId` |
| `closeFolder()` | sets `openFolderIdFlow.value = null` |
| `addItemToFolder(folderId, item)` | `homeRepository.addItemToFolder(...)` |
| `removeItemFromFolder(folderId, itemId)` | `homeRepository.removeItemFromFolder(...)` — also closes popup if folder was deleted |
| `reorderFolderItems(folderId, newChildren)` | `homeRepository.reorderFolderItems(...)` |
| `mergeFolders(sourceFolderId, targetFolderId)` | `homeRepository.mergeFolders(...)` |
| `extractItemFromFolder(folderId, itemId, targetPosition)` | `homeRepository.extractItemFromFolder(...)` — always closes popup |
| `moveItemBetweenFolders(sourceFolderId, itemId, item, targetFolderId)` | `removeItemFromFolder` + `addItemToFolder` in one serialized mutation |
| `extractFolderChildOntoItem(sourceFolderId, childItem, occupantItem, atPosition)` | `removeItemFromFolder` + `createFolder` in one serialized mutation |

---

## Creating a Folder

**Trigger:** User long-presses an icon on the home grid and drags it on top of
another icon (not a folder).

**Flow:**

```
DraggablePinnedItemsGrid.onDragEnd
  → occupant != null && neither item is a FolderItem
  → onCreateFolder(item, occupant, occupant.position)
  → LauncherScreen passes through to MainActivity
  → homeViewModel.createFolder(item1, item2, atPosition)
  → HomeRepositoryImpl.createFolder(...)
  → DataStore updated; pinnedItems StateFlow emits new list
  → Grid recomposes showing FolderIcon
```

The new `FolderItem` appears at the **target item's position** (the item that was
dragged onto).  Both original icons are removed from the flat list.

**Note:** `createFolder` does NOT automatically open the popup.  The user must tap the
newly created folder icon to open it.  An earlier version called
`openFolderIdFlow.value = folder.id` after creation — this was removed because it
triggered the popup unexpectedly whenever two icons were combined.

---

## Opening a Folder

**Trigger:** User taps a `FolderItem` on the home grid.

```
DraggablePinnedItemsGrid  →  onItemClick(item: FolderItem)
  → MainActivity.onPinnedItemClick
      if (item is HomeItem.FolderItem) homeViewModel.openFolder(item.id)
  → openFolderIdFlow.value = item.id
  → HomeUiState.openFolderItem becomes non-null
  → LauncherScreen renders FolderPopupDialog
```

Closing works the same path in reverse (`homeViewModel.closeFolder()` sets
`openFolderIdFlow.value = null`).

---

## FolderPopupDialog

**File:** `ui/components/FolderPopupDialog.kt`

A centered `Card` composable layered over a full-screen scrim.  Key parts:

### Layout

```
Box (full screen)
  ├── Box (scrim — tap to close)
  └── Card (centered, max-width 360dp)
        ├── FolderNameHeader (editable title)
        └── LazyVerticalGrid (3 columns, max 340dp height)
              └── FolderPopupItem × N
                    ├── PinnedItem (visual)
                    ├── detectDragGesture (long-press + drag)
                    └── DropdownMenu (Remove from folder)
```

### Drag gesture inside the popup

Each icon uses `detectDragGesture` (the same reusable detector used on the home grid).
Drag state is tracked locally:

| State variable | Purpose |
|---|---|
| `draggedItemId` | Which icon is being dragged |
| `dragOffset` | Accumulated delta since drag start (Compose-local coords) |
| `dragStartWindowPos` | The dragged item's window-relative top-left at drag start |
| `isDraggingOut` | Whether the pointer has exited `popupWindowRect` |
| `dragOutItem` | Cached reference to the item being dragged out |
| `isPlatformDragActive` | Guards against double-starting the platform DnD session |
| `itemWindowOffsets` | Map of item-id → window-relative top-left (for drop-target math) |
| `popupWindowRect` | Window rect of the popup Card (for boundary detection) |

### Context menu dismissal on drag start

When the user exceeds the drag threshold while long-pressing an icon that has its
context menu open, `menuShownForItemId` is cleared immediately in `onDragStart`.
This mirrors the same behaviour in `DraggablePinnedItemsGrid` and ensures the
dropdown does not remain visible while the icon is being dragged.

---

## Drag-Out

### Problem with manual coordinate forwarding (old approach)

The original implementation accumulated `dragOffset` and forwarded
`dragStartWindowPos + dragOffset` to `LauncherScreen` as a "screen offset".
`LauncherScreen` then rendered a floating `PinnedItem` at that offset.  This
suffered two issues:

1. `dragStartWindowPos` was the item's **top-left corner**, not the finger contact
   point, so the ghost jumped immediately on drag start.
2. The coordinate system conversion (window px → Compose layout px) required
   knowing the status-bar height via `onGloballyPositioned` — fragile and
   error-prone per-device.

### Current approach: `View.startDragAndDrop()`

When the pointer exits `popupWindowRect` for the first time, the popup calls
`startExternalFolderItemDrag(hostView, folder.id, escapedItem)` and then
`onClose()`.

The **OS shadow icon follows the finger automatically** — no manual coordinate
tracking, no floating previews in `LauncherScreen`.

#### Full flow

```
FolderPopupDialog.onDragDelta
  isNowOutside && !wasOutside
    → isPlatformDragActive = true
    → startExternalFolderItemDrag(hostView, folderId, item)   // starts platform DnD
    → onClose()                                                // dismiss popup immediately

            ↓ (finger still moving — OS moves shadow)

AppExternalDropTargetOverlay (on home grid)
  ACTION_DROP received
    → ExternalDragPayloadCodec.decodeDragItem(event)
        → localState is ExternalDragItem.FolderChild → return it directly
    → onItemDropped(FolderChild(...), localOffset)

DraggablePinnedItemsGrid.onItemDropped
  item is ExternalDragItem.FolderChild
    → see routing table in "Drag-Out Drop Routing" below
```

#### Drag-Out Drop Routing

The `FolderChild` drop handler in `DraggablePinnedItemsGrid` checks what occupies the
target cell and routes accordingly:

| Occupant at drop cell | Action |
|---|---|
| Same folder the child came from | **No-op** — consumed silently (drop on source = cancel) |
| A different `FolderItem` | `onMoveFolderItemToFolder` → `HomeViewModel.moveItemBetweenFolders` |
| A non-folder item (app, file, etc.) | `onFolderChildDroppedOnItem` → `HomeViewModel.extractFolderChildOntoItem` → creates a new folder with both items |
| Empty cell | `onFolderItemExtracted` → `HomeViewModel.extractItemFromFolder` → places item on grid |

#### Payload type: `ExternalDragItem.FolderChild`

```kotlin
// ExternalDragPayloadCodec.kt
data class FolderChild(
    val folderId: String,
    val childItem: HomeItem
) : ExternalDragItem()
```

`FolderChild` travels entirely via `DragEvent.localState`.  The `ClipData` text is set
to `childItem.id` as a minimal placeholder (the platform requires non-null ClipData
but the DROP handler never reads it from JSON — `decodeDragItem` returns `localState`
directly when it is an `ExternalDragItem`).

#### Helper: `startExternalFolderItemDrag`

**File:** `ui/components/dragdrop/AppExternalDragDrop.kt`

```kotlin
fun startExternalFolderItemDrag(
    hostView: View,
    folderId: String,
    item: HomeItem,
    dragShadowSize: Dp = IconSize.appList
): Boolean
```

The drag shadow is built from the item's actual content — never from the full window:

| Item type | Shadow source |
|---|---|
| `PinnedApp` | `AppIconMemoryCache.get(packageName)` → falls back to `android.R.drawable.sym_def_app_icon` |
| `PinnedFile` | `android.R.drawable.ic_menu_agenda` |
| `PinnedContact` | `android.R.drawable.ic_menu_myplaces` |
| `AppShortcut` | `android.R.drawable.sym_def_app_icon` |
| anything else | `android.R.drawable.ic_menu_add` |

Using `View.DragShadowBuilder(hostView)` for the shadow was the root cause of a bug
where dragging a non-app item (e.g. a PDF file) from a folder would drag a
**shadow of the entire screen**.  The per-type drawable approach above fixes this.

---

## Internal Reorder Inside the Popup

When the drag ends **inside** `popupWindowRect`, the popup computes which grid slot is
closest to the drop position using `findClosestItemIndex`:

```kotlin
// Iterates all itemWindowOffsets, picks the slot whose center-point
// is nearest to the drop position (Euclidean distance).
fun findClosestItemIndex(
    dropWindowPos: Offset,
    children: List<HomeItem>,
    itemWindowOffsets: Map<String, Offset>,
    cellSizePx: Float
): Int?
```

The icon is then moved to that index in `localChildren` (an optimistic local copy that
avoids DataStore-blink), and `onReorderFolderItems(reordered)` is called to persist.

---

## Merging Two Folders

**Trigger:** User drags one `FolderItem` on top of another `FolderItem` on the home
grid.

```
DraggablePinnedItemsGrid.onDragEnd
  item is FolderItem && occupant is FolderItem
  → onMergeFolders(item.id, occupant.id)
  → homeViewModel.mergeFolders(sourceFolderId, targetFolderId)
  → HomeRepositoryImpl.mergeFolders(...)
      appends source.children to target.children (skips duplicates)
      removes source folder from flat list
  → DataStore updated
```

The merged folder appears at the **target folder's position**.

---

## Folder Cleanup Policy

Triggered automatically after every child removal (`removeItemFromFolder`,
`extractItemFromFolder`, `evictItemFromFolderIfPresent`):

```
remaining.size == 0  →  remove the FolderItem from the home grid entirely
remaining.size == 1  →  replace the FolderItem with remaining[0] at the folder's position
remaining.size >= 2  →  update the FolderItem (keep it, just with fewer children)
```

This means:

- Dragging the last two icons out of a folder (one at a time) will first convert the
  folder to a single plain icon, then leave that icon on the grid.
- The user never sees an empty folder.

---

## Cross-Location Drop Routing

The `AppExternalDropTargetOverlay` drop handler in `DraggablePinnedItemsGrid` handles
two separate item categories: `FolderChild` payloads (items dragged out of a folder
popup) and all other external payloads (items dragged from the search dialog).

### Search-dialog items dropped on the home grid

When an external item is dropped and the target cell is occupied, the occupant type
determines the action:

| Occupant at drop cell | Action |
|---|---|
| A `FolderItem` | `onAddItemToFolder` → item is added to the folder |
| A non-folder item | `onCreateFolder` → both items are merged into a new folder at that cell |
| Empty cell | `onItemDroppedToHome` → item is pinned at that cell |

Before this routing was added, dropping a search-dialog item on top of an existing
non-folder home icon was a **silent no-op** (the drop was ignored).  Now it creates a
folder just like dragging two home icons together.

### `FolderChild` items dropped on the home grid

See the [Drag-Out Drop Routing](#drag-out-drop-routing) table above.

---

## Deduplication

Items in the data model live in **exactly one place**: either the flat `pinnedItems`
list on the home grid, or inside one `FolderItem`'s `children` list.  Several code
paths previously violated this invariant, causing the same icon to appear in two places
at once.

### Where violations happened

1. **Search-dialog item dragged to home grid, item already in a folder** — `pinOrMoveItemToPosition`
   only scanned the flat list for duplicates.  If the item lived in a folder it was not
   found and a second copy was put on the grid alongside the original inside the folder.

2. **Search-dialog item dragged onto a folder, item already in another folder** — `addItemToFolder`
   removed the item from the flat list but did not scan sibling folders.  The item ended
   up in two folders.

### Fix: `evictItemFromFolderIfPresent`

Both write paths now call `evictItemFromFolderIfPresent(currentItems, item.id)` before
inserting the item at its new location.

- `pinOrMoveItemToPosition` calls it right after deserializing, before any index look-ups.
- `addItemToFolder` calls it right after the flat-list `removeAll`.

In both cases the call happens inside the same `DataStore.edit {}` lambda, so there is
no window between "evict from folder" and "place at new location" where a broken
intermediate state could be observed.

---

## Edge Cases Fixed

This section documents specific bugs that were found and fixed during development,
each with the root cause and the fix applied.

---

### 1. Folder opened automatically after creation

**Symptom:** Every time two icons were dragged together to create a folder, the folder
popup opened immediately without the user tapping it.

**Root cause:** `HomeViewModel.createFolder` contained the line:
```kotlin
openFolderIdFlow.value = folder.id   // ← removed
```
This was left over from an early prototype that wanted to preview the new folder.

**Fix:** Removed that line.  Folder creation now only creates the folder on the grid;
the user decides whether and when to open it by tapping the folder icon.

---

### 2. Full-screen drag shadow for non-app folder items (PDF, contacts, etc.)

**Symptom:** Dragging a PDF file or contact card out of a folder popup produced a drag
shadow the size of the entire screen.

**Root cause:** `startExternalFolderItemDrag` was using
`View.DragShadowBuilder(hostView)` as the shadow builder, where `hostView` is the
window decorView.  When the `Bitmap` is not provided to `AppIconDragShadowBuilder`, the
fallback path delegates to `View.DragShadowBuilder(view)` — and with the entire
decorView as `view`, the shadow dimensions become the full window size.

**Fix:** `startExternalFolderItemDrag` now maps each `HomeItem` subtype to a small
system drawable (see [Helper: `startExternalFolderItemDrag`](#helper-startexternalfolderitemdrag)).
The decorView is never used as the shadow source.

---

### 3. Dropping a folder child back into its own folder caused duplication

**Symptom:** Dragging a folder child out of the popup and dropping it back onto the
same folder icon placed the item inside the folder again — resulting in the same icon
appearing twice as a child.

**Root cause:** The `FolderChild` drop handler called `onMoveFolderItemToFolder` for
any `FolderItem` occupant at the drop cell, without checking whether that folder was
the same folder the child originally came from.  `moveItemBetweenFolders` would then
call `removeItemFromFolder` (which would decrement the count by one and possibly
unwrap the folder) and then `addItemToFolder` — effectively duplicating the item.

**Fix:** Added a guard check before the folder-move branch:
```kotlin
// Same-folder drop → consume the event silently (no operation)
if (occupantAtDrop is HomeItem.FolderItem && occupantAtDrop.id == item.folderId) {
    return@AppExternalDropTargetOverlay true
}
```

---

### 4. Dragging an icon from folder A onto folder B duplicated the icon

**Symptom:** Dragging a child icon from one folder popup and dropping it onto a
different folder icon on the home grid left the icon inside **both** folders.

**Root cause:** The drop handler called `onFolderItemExtracted` (which calls
`extractItemFromFolder`) followed separately by `onAddItemToFolder`.
`extractItemFromFolder` removes the item from the source folder and places it as a
**flat home grid item** at the drop position.  But the drop position was already
occupied by the target folder.  Then `addItemToFolder` added it to the target folder
without removing the just-placed flat item, creating a second copy.

**Fix:** A dedicated ViewModel method `moveItemBetweenFolders` handles this atomically:

```kotlin
fun moveItemBetweenFolders(sourceFolderId, itemId, item, targetFolderId) {
    // Step 1: remove from source (cleanup policy applied)
    homeRepository.removeItemFromFolder(sourceFolderId, itemId)
    // Step 2: add to target (item is not on the flat list — just moves between folders)
    homeRepository.addItemToFolder(folderId = targetFolderId, item = item)
}
```

The item is **never placed on the flat home grid** during this operation, so there is
nothing to collide with.

---

### 5. Dropping a folder child onto a home icon did nothing

**Symptom:** Dragging an icon out of a folder popup and releasing it on top of an
existing non-folder home icon was silently ignored.  The expected behaviour (matching
dragging two home icons together) is to create a new folder.

**Root cause:** The `else` branch of the `FolderChild` drop handler called
`onFolderItemExtracted` regardless of what was at the drop cell.  When the cell was
occupied by a non-folder item, `extractItemFromFolder` would place the dragged child at
that position — but the position was already taken, so the repository rejected the
write and the item stayed in its source folder.

**Fix:** The `else` branch was split:

```kotlin
else -> {
    if (occupantAtDrop != null) {
        // Non-folder occupant → create a new folder with both items
        onFolderChildDroppedOnItem(item.folderId, item.childItem, occupantAtDrop, dropPosition)
    } else {
        // Empty cell → standard extract to grid
        onFolderItemExtracted(item.folderId, item.childItem.id, dropPosition)
    }
}
```

`onFolderChildDroppedOnItem` routes to `HomeViewModel.extractFolderChildOntoItem`,
which atomically removes the child from its source folder and calls
`homeRepository.createFolder(childItem, occupantItem, atPosition)`.

---

### 6. Dropping a search-dialog icon onto a home icon did nothing

**Symptom:** Dragging an icon from the search dialog and releasing it on top of an
existing non-folder home icon was silently ignored.

**Root cause:** The external-drop handler in `DraggablePinnedItemsGrid` used a simple
`if (occupant is FolderItem) addToFolder else pin` logic.  The `else` branch called
`onItemDroppedToHome`, which calls `pinOrMoveItemToPosition` — but that method returns
`false` (no-op) when the target position is occupied.

**Fix:** The two-branch check was replaced with a three-branch `when`:

```kotlin
when {
    occupantAtDrop is HomeItem.FolderItem -> onAddItemToFolder(occupantAtDrop.id, homeItem)
    occupantAtDrop != null                -> onCreateFolder(homeItem, occupantAtDrop, dropPosition)
    else                                  -> onItemDroppedToHome(homeItem, dropPosition)
}
```

---

### 7. Search-dialog icons caused duplication when the icon already existed in a folder

**Symptom:** Dragging an icon from the search dialog to the home grid (or into a
folder) when the same icon already existed as a child inside a folder resulted in the
icon appearing in **two** places simultaneously.

**Root cause:** `pinOrMoveItemToPosition` and `addItemToFolder` only checked
the flat `pinnedItems` list for existing copies of the item.  Items living inside a
folder's `children` list were invisible to both methods.

**Fix:** See [Deduplication](#deduplication) — `evictItemFromFolderIfPresent` is now
called in both write paths before the item is inserted at its new location.

---

## Component Map

| Component | Role |
|---|---|
| `HomeItem.FolderItem` | Data model for a folder |
| `HomeRepository` | Interface: all folder operations |
| `HomeRepositoryImpl` | Implementation + cleanup policy + DataStore writes + `evictItemFromFolderIfPresent` |
| `HomeViewModel` | `openFolderIdFlow`, all folder mutation methods including `moveItemBetweenFolders` and `extractFolderChildOntoItem` |
| `HomeUiState.openFolderItem` | Derived state: the live open folder (null = closed) |
| `DraggablePinnedItemsGrid` | Occupancy routing on drag-end and on external drop; all folder drop callbacks |
| `FolderIcon` | 2×2 mini-preview grid composable shown on the home grid |
| `PinnedItem` | Routes to `FolderIcon` when item is `FolderItem` |
| `FolderPopupDialog` | Full popup: grid, rename, internal drag, drag-out trigger, context menu dismissal on drag start |
| `ExternalDragItem.FolderChild` | Drag payload for folder-child drag-out |
| `startExternalFolderItemDrag` | Starts `View.startDragAndDrop()` with `FolderChild` payload and per-type drag shadow |
| `AppExternalDropTargetOverlay` | Receives platform drop events on the home grid |
| `ExternalDragPayloadCodec` | Encodes/decodes external drag payloads |
| `LauncherScreen` | Wires all folder callbacks; renders `FolderPopupDialog` |
| `MainActivity` | Routes all folder callbacks to `HomeViewModel` |


---

## Table of Contents

1. [Overview](#overview)
2. [Data Model](#data-model)
3. [Repository & Persistence](#repository--persistence)
4. [ViewModel](#viewmodel)
5. [Creating a Folder (Drag Two Icons Together)](#creating-a-folder)
6. [Opening a Folder](#opening-a-folder)
7. [FolderPopupDialog](#folderpopupdialog)
8. [Drag-Out: Moving an Icon from a Folder to the Home Grid](#drag-out)
9. [Internal Reorder Inside the Popup](#internal-reorder-inside-the-popup)
10. [Merging Two Folders](#merging-two-folders)
11. [Folder Cleanup Policy (Auto-Delete / Auto-Unwrap)](#folder-cleanup-policy)
12. [Component Map](#component-map)

---

## Overview

A **folder** is a home screen item (`HomeItem.FolderItem`) that contains other home
screen items as its children.  Folders behave like regular pinned items on the grid
except:

- Tapping opens a **popup dialog** instead of launching an app.
- Dragging two non-folder icons on top of each other **creates** a folder.
- Dragging a non-folder icon on top of a folder **adds** it to the folder.
- Dragging one folder on top of another **merges** them.
- Dragging a folder onto a non-folder is **ignored** (no operation).
- If a folder ends up with **0 children** it is deleted automatically.
- If a folder ends up with **1 child** it is unwrapped back to a plain home item.

---

## Data Model

**File:** `domain/model/HomeItem.kt`

```kotlin
@Serializable
data class FolderItem(
    override val id: String,               // "folder:{uuid}"
    val name: String = "Folder",           // User-editable display name
    val children: List<HomeItem> = emptyList(),
    override val position: GridPosition = GridPosition.DEFAULT
) : HomeItem() {
    companion object {
        /** Creates a new FolderItem containing two seed items at the given position. */
        fun create(item1: HomeItem, item2: HomeItem, atPosition: GridPosition): FolderItem
    }
}
```

`FolderItem` participates in the same polymorphic kotlinx.serialization setup as the
other `HomeItem` subtypes.  It is stored inside the `"pinned_items_ordered"` DataStore
key alongside apps, files, and contacts.  Nesting (folder inside folder) is **not**
supported.

---

## Repository & Persistence

**File:** `data/repository/HomeRepositoryImpl.kt`  
**Interface:** `domain/repository/HomeRepository.kt`

All folder mutations are atomic DataStore `.edit { }` lambdas.  The full list of
operations:

| Method | What it does |
|---|---|
| `createFolder(item1, item2, atPosition)` | Removes both items from the flat list, creates a new `FolderItem` at `atPosition` containing them. |
| `addItemToFolder(folderId, item)` | Removes `item` from the flat list, appends it to the folder's children. |
| `removeItemFromFolder(folderId, itemId)` | Removes the child and applies the cleanup policy (see below). |
| `reorderFolderItems(folderId, newChildren)` | Replaces the folder's children list with the provided order. |
| `mergeFolders(sourceFolderId, targetFolderId)` | Appends all source children to the target folder, deletes the source folder. |
| `renameFolder(folderId, newName)` | Updates the `name` field in-place. |
| `extractItemFromFolder(folderId, itemId, targetPosition)` | Removes the child from the folder, pins it at `targetPosition` on the home grid, applies cleanup policy to the folder. |

### Folder Cleanup Policy

Applied inside `removeItemFromFolder` and `extractItemFromFolder` after every
child removal:

```
remaining children count
        0  →  delete the folder entirely
        1  →  unwrap: delete the folder, pin the single remaining child at the folder's position
       2+  →  update the folder in place (keep it)
```

---

## ViewModel

**File:** `presentation/home/HomeViewModel.kt`

```kotlin
// Which folder is currently open in the popup (null = closed)
private val openFolderIdFlow: MutableStateFlow<String?> = MutableStateFlow(null)

// Derived from openFolderIdFlow + pinnedItems:
// delivers the live FolderItem object so the popup recomposes when children change
val uiState: StateFlow<HomeUiState>
    // HomeUiState.openFolderItem: HomeItem.FolderItem?
```

**Why `openFolderIdFlow` instead of storing the whole `FolderItem`:**  
The `children` list inside a `FolderItem` changes whenever another item is added or
reordered.  By storing only the ID and joining it with `pinnedItems` in `combine {}`
the popup automatically recomposes with the latest children without any extra
coordination.

**`extractItemFromFolder` always closes the popup:**

```kotlin
fun extractItemFromFolder(folderId: String, itemId: String, targetPosition: GridPosition) {
    viewModelScope.launch {
        homeRepository.extractItemFromFolder(folderId, itemId, targetPosition)
        openFolderIdFlow.value = null   // close popup regardless of remaining count
    }
}
```

Before this explicit reset, the popup only closed when the folder disappeared from
`pinnedItems` (which only happens when ≤1 child remains).  Dragging out of a folder
with 2+ remaining children left the popup open — that bug is fixed here.

---

## Creating a Folder

**Trigger:** User long-presses an icon on the home grid and drags it on top of
another icon (not a folder).

**Flow:**

```
DraggablePinnedItemsGrid.onDragEnd
  → occupant != null && neither item is a FolderItem
  → onCreateFolder(item, occupant, occupant.position)
  → LauncherScreen passes through to MainActivity
  → homeViewModel.createFolder(item1, item2, atPosition)
  → HomeRepositoryImpl.createFolder(...)
  → DataStore updated; pinnedItems StateFlow emits new list
  → Grid recomposes showing FolderIcon
```

The new `FolderItem` appears at the **target item's position** (the item that was
dragged onto).  Both original icons are removed from the flat list.

---

## Opening a Folder

**Trigger:** User taps a `FolderItem` on the home grid.

```
DraggablePinnedItemsGrid  →  onItemClick(item: FolderItem)
  → MainActivity.onPinnedItemClick
      if (item is HomeItem.FolderItem) homeViewModel.openFolder(item.id)
  → openFolderIdFlow.value = item.id
  → HomeUiState.openFolderItem becomes non-null
  → LauncherScreen renders FolderPopupDialog
```

Closing works the same path in reverse (`homeViewModel.closeFolder()` sets
`openFolderIdFlow.value = null`).

---

## FolderPopupDialog

**File:** `ui/components/FolderPopupDialog.kt`

A centered `Card` composable layered over a full-screen scrim.  Key parts:

### Layout

```
Box (full screen)
  ├── Box (scrim — tap to close)
  └── Card (centered, max-width 360dp)
        ├── FolderNameHeader (editable title)
        └── LazyVerticalGrid (3 columns, max 340dp height)
              └── FolderPopupItem × N
                    ├── PinnedItem (visual)
                    ├── detectDragGesture (long-press + drag)
                    └── DropdownMenu (Remove from folder)
```

### Drag gesture inside the popup

Each icon uses `detectDragGesture` (the same reusable detector used on the home grid).
Drag state is tracked locally:

| State variable | Purpose |
|---|---|
| `draggedItemId` | Which icon is being dragged |
| `dragOffset` | Accumulated delta since drag start (Compose-local coords) |
| `dragStartWindowPos` | The dragged item's window-relative top-left at drag start |
| `isDraggingOut` | Whether the pointer has exited `popupWindowRect` |
| `dragOutItem` | Cached reference to the item being dragged out |
| `isPlatformDragActive` | Guards against double-starting the platform DnD session |
| `itemWindowOffsets` | Map of item-id → window-relative top-left (for drop-target math) |
| `popupWindowRect` | Window rect of the popup Card (for boundary detection) |

---

## Drag-Out

### Problem with manual coordinate forwarding (old approach)

The original implementation accumulated `dragOffset` and forwarded
`dragStartWindowPos + dragOffset` to `LauncherScreen` as a "screen offset".
`LauncherScreen` then rendered a floating `PinnedItem` at that offset.  This
suffered two issues:

1. `dragStartWindowPos` was the item's **top-left corner**, not the finger contact
   point, so the ghost jumped immediately on drag start.
2. The coordinate system conversion (window px → Compose layout px) required
   knowing the status-bar height via `onGloballyPositioned` — fragile and
   error-prone per-device.

### Current approach: `View.startDragAndDrop()`

When the pointer exits `popupWindowRect` for the first time, the popup calls  
`startExternalFolderItemDrag(hostView, folder.id, escapedItem)` and then
`onClose()`.

The **OS shadow icon follows the finger automatically** — no manual coordinate
tracking, no floating previews in `LauncherScreen`.

#### Full flow

```
FolderPopupDialog.onDragDelta
  isNowOutside && !wasOutside
    → isPlatformDragActive = true
    → startExternalFolderItemDrag(hostView, folderId, item)   // starts platform DnD
    → onClose()                                                // dismiss popup immediately

            ↓ (finger still moving — OS moves shadow)

AppExternalDropTargetOverlay (on home grid)
  ACTION_DROP received
    → ExternalDragPayloadCodec.decodeDragItem(event)
        → localState is ExternalDragItem.FolderChild → return it directly
    → onItemDropped(FolderChild(...), localOffset)

DraggablePinnedItemsGrid.onItemDropped
  item is ExternalDragItem.FolderChild
    → onFolderItemExtracted(item.folderId, item.childItem.id, dropPosition)

LauncherScreen
  onFolderItemExtracted = onExtractItemFromFolder

MainActivity
  onExtractItemFromFolder = { folderId, itemId, targetPosition ->
      homeViewModel.extractItemFromFolder(folderId, itemId, targetPosition)
  }

HomeViewModel.extractItemFromFolder
  → homeRepository.extractItemFromFolder(...)
  → openFolderIdFlow.value = null   (popup already closed; this is a no-op guard)
```

#### Payload type: `ExternalDragItem.FolderChild`

```kotlin
// ExternalDragPayloadCodec.kt
data class FolderChild(
    val folderId: String,
    val childItem: HomeItem
) : ExternalDragItem()
```

`FolderChild` travels entirely via `DragEvent.localState`.  The `ClipData` text is set
to `childItem.id` as a minimal placeholder (the platform requires non-null ClipData
but the DROP handler never reads it from JSON — `decodeDragItem` returns `localState`
directly when it is an `ExternalDragItem`).

#### Helper: `startExternalFolderItemDrag`

**File:** `ui/components/dragdrop/AppExternalDragDrop.kt`

```kotlin
fun startExternalFolderItemDrag(
    hostView: View,
    folderId: String,
    item: HomeItem,
    dragShadowSize: Dp = IconSize.appList
): Boolean
```

- If `item` is a `PinnedApp`, tries to use the cached app icon for the shadow via
  `AppIconMemoryCache`.
- Falls back to `View.DragShadowBuilder(hostView)` for other item types.
- Wraps the item in `ExternalDragItem.FolderChild` and calls
  `startExternalDragWithFallbackHosts` (the same private helper used by
  `startExternalAppDrag` and `startExternalFileDrag`).

---

## Internal Reorder Inside the Popup

When the drag ends **inside** `popupWindowRect`, the popup computes which grid slot is
closest to the drop position using `findClosestItemIndex`:

```kotlin
// Iterates all itemWindowOffsets, picks the slot whose center-point
// is nearest to the drop position (Euclidean distance).
fun findClosestItemIndex(
    dropWindowPos: Offset,
    children: List<HomeItem>,
    itemWindowOffsets: Map<String, Offset>,
    cellSizePx: Float
): Int?
```

The icon is then moved to that index in `localChildren` (an optimistic local copy that
avoids DataStore-blink), and `onReorderFolderItems(reordered)` is called to persist.

---

## Merging Two Folders

**Trigger:** User drags one `FolderItem` on top of another `FolderItem` on the home
grid.

```
DraggablePinnedItemsGrid.onDragEnd
  item is FolderItem && occupant is FolderItem
  → onMergeFolders(item.id, occupant.id)
  → homeViewModel.mergeFolders(sourceFolderId, targetFolderId)
  → HomeRepositoryImpl.mergeFolders(...)
      appends source.children to target.children
      removes source folder from flat list
  → DataStore updated
```

The merged folder appears at the **target folder's position**.

---

## Folder Cleanup Policy

Triggered automatically after every child removal (`removeItemFromFolder`,
`extractItemFromFolder`):

```
remaining.size == 0  →  remove the FolderItem from the home grid entirely
remaining.size == 1  →  replace the FolderItem with remaining[0] at the folder's position
remaining.size >= 2  →  update the FolderItem (keep it, just with fewer children)
```

This means:

- Dragging the last two icons out of a folder (one at a time) will first convert the
  folder to a single plain icon, then leave that icon on the grid.
- The user never sees an empty folder.

---

## Component Map

| Component | Role |
|---|---|
| `HomeItem.FolderItem` | Data model for a folder |
| `HomeRepository` | Interface: all 7 folder operations |
| `HomeRepositoryImpl` | Implementation + cleanup policy + DataStore writes |
| `HomeViewModel` | `openFolderIdFlow`, all folder mutation methods |
| `HomeUiState.openFolderItem` | Derived state: the live open folder (null = closed) |
| `DraggablePinnedItemsGrid` | Occupancy routing on drag-end; `onFolderItemExtracted` for drop |
| `FolderIcon` | 2×2 mini-preview grid composable shown on the home grid |
| `PinnedItem` | Routes to `FolderIcon` when item is `FolderItem` |
| `FolderPopupDialog` | Full popup: grid, rename, internal drag, drag-out trigger |
| `ExternalDragItem.FolderChild` | Drag payload for folder-child drag-out |
| `startExternalFolderItemDrag` | Starts `View.startDragAndDrop()` with `FolderChild` payload |
| `AppExternalDropTargetOverlay` | Receives platform drop events on the home grid |
| `ExternalDragPayloadCodec` | Encodes/decodes external drag payloads |
| `LauncherScreen` | Wires all folder callbacks; renders `FolderPopupDialog` |
| `MainActivity` | Routes all folder callbacks to `HomeViewModel` |
