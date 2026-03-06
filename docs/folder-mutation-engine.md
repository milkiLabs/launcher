# FolderMutationEngine

This document explains the dedicated folder-domain mutation engine introduced to reduce
`HomeRepositoryImpl` complexity and make folder behavior easier to test and evolve.

---

## Why This Exists

Before this extraction, most folder invariants and mutation flows lived directly in:
- `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt`

That made one persistence class responsible for two different concerns:
1. **Storage orchestration** (DataStore read/edit/write and serialization), and
2. **Folder business rules** (cleanup policy, dedup policy, nesting guards, merge/extract behavior).

`FolderMutationEngine` separates these concerns so each class has a clear job:
- `HomeRepositoryImpl` orchestrates persistence transactions.
- `FolderMutationEngine` performs pure in-memory folder mutations.

---

## File Locations

- Engine implementation:
  - `app/src/main/java/com/milki/launcher/data/repository/FolderMutationEngine.kt`
- Repository delegating to the engine:
  - `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt`
- Engine contract tests:
  - `app/src/test/java/com/milki/launcher/data/repository/FolderMutationEngineContractTest.kt`

---

## Architectural Boundary

### `HomeRepositoryImpl` responsibilities

- Deserialize persisted item payload from DataStore.
- Start and own DataStore `edit` transaction boundaries.
- Delegate folder mutations to the engine using a mutable list.
- Perform non-folder concerns (for example span-aware occupancy checks for widget logic).
- Serialize and persist only when a mutation is applied.

### `FolderMutationEngine` responsibilities

- Validate folder-domain guards:
  - no nested folders,
  - no widget children inside folders,
  - same-source-and-target move rejection.
- Apply cleanup policy for child removals:
  - 0 children left: delete folder,
  - 1 child left: unwrap folder and promote remaining child,
  - 2+ children left: keep folder and update children.
- Enforce global uniqueness by item ID in folder mutation paths.
- Implement create/add/remove/reorder/merge/rename/extract/move flows as pure list transformations.

---

## Public Engine API

`FolderMutationEngine` exposes methods used by `HomeRepositoryImpl`:

- `createFolder(items, item1, item2, atPosition)`
- `addItemToFolder(items, folderId, item, targetIndex)`
- `removeItemFromFolder(items, folderId, itemId)`
- `reorderFolderItems(items, folderId, newChildren)`
- `mergeFolders(items, sourceFolderId, targetFolderId)`
- `renameFolder(items, folderId, newName)`
- `extractItemFromFolder(items, folderId, itemId, targetPosition, targetPositionOccupiedByOtherItem)`
- `moveItemBetweenFolders(items, sourceFolderId, targetFolderId, itemId)`
- `extractFolderChildOntoItem(items, sourceFolderId, childItemId, occupantItem, atPosition)`
- `evictItemEverywhere(items, itemId)`
- `containsItemIdAnywhere(items, itemId)`

These APIs intentionally mutate the provided `MutableList<HomeItem>` in place.
This avoids copy-heavy intermediate states and keeps the repository transaction simple.

---

## Testing Strategy

The engine is tested with contract-style unit tests that focus on behavior guarantees,
not implementation details.

Current contract coverage includes:

1. `createFolder`
   - source top-level items are replaced by a folder,
   - children are stored with `GridPosition.DEFAULT`.
2. `addItemToFolder`
   - item is moved from top-level to folder,
   - insertion index behavior is preserved.
3. `mergeFolders`
   - source folder is deleted,
   - only unique source children are appended.
4. `extractItemFromFolder`
   - child is extracted to target cell,
   - source folder children are updated via cleanup logic.
5. `moveItemBetweenFolders`
   - item moves into target folder,
   - source folder cleanup policy (including unwrap path) is applied.

An additional rejection guard test verifies extract failure when target is occupied.

---

## Behavior Notes

- The engine is intentionally pure from a platform perspective:
  - no Android framework dependencies,
  - no DataStore usage,
  - no coroutine concerns.
- Any storage side effects remain in repository edit blocks.
- This boundary makes folder behavior easier to reason about for new contributors.
