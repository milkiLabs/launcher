# Folder System

This document covers the complete folder feature on the home screen: how folders are
created, opened, reordered, renamed, and how items are dragged out of them.

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
