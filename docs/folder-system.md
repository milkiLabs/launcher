# Folder System

This document describes the current folder implementation on the home screen after the
folder-mutation-engine extraction and atomicity simplification work.

The focus of this version is correctness and maintainability:
- cross-folder and cross-location folder operations are now single repository transactions,
- stale folder-child removals are strict no-ops,
- global uniqueness by item ID is enforced during write paths,
- folder-domain mutation rules are centralized in `FolderMutationEngine`.

---

## Table of Contents

1. [Overview](#overview)
2. [Data Model](#data-model)
3. [Repository Contract](#repository-contract)
4. [Repository Implementation Guarantees](#repository-implementation-guarantees)
5. [ViewModel Routing](#viewmodel-routing)
6. [UI Drop Routing](#ui-drop-routing)
7. [Cleanup + Dedup Policy](#cleanup--dedup-policy)
8. [Main Transaction Flows](#main-transaction-flows)
9. [Files and Responsibilities](#files-and-responsibilities)
10. [Manual Validation Matrix](#manual-validation-matrix)

---

## Overview

A folder is a `HomeItem.FolderItem` pinned on the home grid and containing a list of
child `HomeItem`s.

Key behavior:
- Drag non-folder onto non-folder → create folder.
- Drag non-folder onto folder → add item to folder.
- Drag folder onto folder → merge folders.
- Drag folder child onto empty cell → extract to grid.
- Drag folder child onto another folder → move child between folders.
- Drag folder child onto occupied non-folder cell → create new folder from both items.

Nested folders are not supported.

---

## Data Model

**File:** `app/src/main/java/com/milki/launcher/domain/model/HomeItem.kt`

Folder model:
- `id: String` (`folder:{uuid}`)
- `name: String`
- `children: List<HomeItem>`
- `position: GridPosition` (home-grid position of the folder icon)

Child items are stored with `GridPosition.DEFAULT` while inside folders.

---

## Repository Contract

**Interface:** `app/src/main/java/com/milki/launcher/domain/repository/HomeRepository.kt`

Folder APIs:
- `createFolder(item1, item2, atPosition)`
- `addItemToFolder(folderId, item, targetIndex)`
- `removeItemFromFolder(folderId, itemId)`
- `reorderFolderItems(folderId, newChildren)`
- `mergeFolders(sourceFolderId, targetFolderId)`
- `renameFolder(folderId, newName)`
- `extractItemFromFolder(folderId, itemId, targetPosition)`
- `moveItemBetweenFolders(sourceFolderId, targetFolderId, itemId)` **(atomic)**
- `extractFolderChildOntoItem(sourceFolderId, childItemId, occupantItem, atPosition)` **(atomic)**

The two new atomic APIs remove previous two-step orchestration from ViewModel.

---

## Repository Implementation Guarantees

**Implementations:**
- `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt`
- `app/src/main/java/com/milki/launcher/data/repository/FolderMutationEngine.kt`

Repository role now:
- deserialize DataStore payload,
- execute a mutation using the folder engine,
- serialize back only when mutation is applied.

Folder engine role now:
- folder invariants (no nesting, no widget children),
- folder cleanup policy (delete/unwrap/update),
- folder-domain dedup and move/merge/extract behavior.

### 1) Strict removal guard

`removeItemFromFolder(folderId, itemId)` now mutates only when `itemId` actually exists
inside that folder.

If child is missing:
- no cleanup is applied,
- no folder is deleted/unwrapped,
- no persistence write happens.

This protects against stale drag/menu events.

### 2) Shared cleanup helper

Folder-child removal + cleanup is centralized in:
- `FolderMutationEngine.removeChildFromFolderWithCleanup(items, folderId, childItemId)`.

Cleanup policy:
- remaining 0 → delete folder
- remaining 1 → unwrap folder and promote last child to folder position
- remaining 2+ → update folder children in place

This helper is reused by:
- `removeItemFromFolder`
- `extractItemFromFolder`
- folder-child eviction paths

### 3) Global uniqueness enforcement

Write paths now use:
- `FolderMutationEngine.evictItemEverywhere(items, itemId)`

This removes a given item ID from:
- top-level list
- all folder children (repeatedly, until exhausted)

Resulting invariant after each relevant write:
> one logical item ID lives in exactly one place.

### 4) New atomic transactions

- `moveItemBetweenFolders(...)`
  - single edit: validate source+target, remove source child (via global eviction), insert into target.

- `extractFolderChildOntoItem(...)`
  - single edit: validate source child + live non-folder occupant at drop cell,
    evict both IDs globally, create new folder at target cell.

No intermediate observable state between remove/add/create steps.

---

## ViewModel Routing

**File:** `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt`

All home writes are still serialized through `launchSerializedHomeMutation`.

Important simplifications:
- `moveItemBetweenFolders(sourceFolderId, itemId, targetFolderId)` now calls a single repository atomic method.
- `extractFolderChildOntoItem(...)` now calls one repository atomic method instead of `remove` + `create` sequence.

Popup lifecycle remains ID-based (`openFolderIdFlow`) and derives live folder content from
`pinnedItems` in `uiState` combine.

---

## UI Drop Routing

### Draggable grid

**File:** `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt`

Callback shape was simplified:
- `onMoveFolderItemToFolder(sourceFolderId, itemId, targetFolderId)`

`ExternalDragItem.FolderChild` routing:
- drop on same source folder icon → consumed no-op
- drop on different folder icon → `onMoveFolderItemToFolder`
- drop on occupied non-folder icon → `onFolderChildDroppedOnItem`
- drop on empty cell → `onFolderItemExtracted`

### Screen and activity plumbing

- `LauncherScreen` and `MainActivity` were updated to pass the simplified callback contract.

---

## Cleanup + Dedup Policy

### Cleanup policy (folder size transitions)

After child removal:
- 0 children → delete folder icon
- 1 child → replace folder icon with remaining child at folder position
- 2+ children → keep folder with updated children

### Dedup policy

Global uniqueness by item ID is enforced in mutation paths that move/create/insert item IDs.

No backward-compat migration layer is intentionally added; current writes enforce the
new invariant directly.

---

## Main Transaction Flows

### A) Folder child -> different folder

1. UI emits `onMoveFolderItemToFolder(sourceFolderId, itemId, targetFolderId)`.
2. `HomeViewModel.moveItemBetweenFolders(...)` delegates to repository.
3. Repository runs one DataStore edit transaction:
   - validate source child and target folder,
   - evict child ID globally,
   - insert child into target folder children.

### B) Folder child -> occupied non-folder icon

1. UI emits `onFolderChildDroppedOnItem(sourceFolderId, childItem, occupantItem, atPosition)`.
2. `HomeViewModel.extractFolderChildOntoItem(...)` delegates to repository atomic API.
3. Repository runs one DataStore edit transaction:
   - validate source child and current live occupant at `atPosition`,
   - evict both IDs globally,
   - create and insert new folder at `atPosition`.

### C) Folder child -> empty home cell

1. UI emits `onFolderItemExtracted(folderId, itemId, targetPosition)`.
2. `HomeViewModel.extractItemFromFolder(...)` calls repository.
3. Repository transaction:
   - validate occupancy,
   - remove child with cleanup helper,
   - evict child ID globally,
   - add child at `targetPosition`.

---

## Files and Responsibilities

- `HomeItem.FolderItem`
  - folder model.
- `HomeRepository`
  - folder API contract, including new atomic operations.
- `HomeRepositoryImpl`
  - DataStore orchestration for home items, occupancy checks, and widget mutations.
- `FolderMutationEngine`
  - Pure in-memory folder mutation rules and folder-domain invariants.
- `HomeViewModel`
  - serialized command routing and popup state.
- `DraggablePinnedItemsGrid`
  - drag/drop occupancy routing.
- `LauncherScreen`
  - callback wiring and popup rendering.
- `MainActivity`
  - callback bridge from UI to ViewModel.

---

## Manual Validation Matrix

Recommended manual checks after future changes:

1. Drag non-folder onto non-folder → new folder at target cell.
2. Drag non-folder onto folder → item appears once inside target folder.
3. Drag folder onto folder → source removed, children appended to target.
4. Drag folder child onto same folder icon → no-op.
5. Drag folder child onto different folder → child moved once, no top-level duplicate icon.
6. Drag folder child onto occupied non-folder icon → new folder contains both icons.
7. Drag folder child onto empty cell → extracted icon appears at drop cell.
8. Trigger remove of non-existent child ID → no folder mutation.
9. Verify cleanup transitions: 2→1 unwrap and 1→0 delete.

---

## Contract Tests

Automated contract tests for folder mutation paths now live in:
- `app/src/test/java/com/milki/launcher/data/repository/FolderMutationEngineContractTest.kt`

Covered mutation paths:
1. `createFolder`
2. `addItemToFolder`
3. `mergeFolders`
4. `extractItemFromFolder`
5. `moveItemBetweenFolders`

---

## Notes

This document intentionally describes the current implementation only. Historical bug
narratives and duplicated sections were removed so the file stays a single source of truth
for ongoing development and onboarding.
