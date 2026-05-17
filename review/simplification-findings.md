# Simplification Findings

> Opportunities to cut code, reduce complexity, shrink APK size, and improve maintainability.
> Audited 2026-05-18 across 187 source files.

---

## Executive Summary

| Category                                   | Files Affected    | Lines Cuttable   | Risk       |
| ------------------------------------------ | ----------------- | ---------------- | ---------- |
| **Dead code (delete entirely)**            | 9 files + 3 tests | **~783**         | None       |
| **Documentation bloat (trim)**             | 7 DI module files | **~270**         | None       |
| **Over-engineered abstractions (flatten)** | 8 files           | **~200**         | Low-Medium |
| **Merge opportunities (consolidate)**      | 6 files           | **~130**         | Low-Medium |
| **Unused sealed variants (prune)**         | 3 files           | **~10**          | None       |
| **Total potential reduction**              |                   | **~1,393 lines** |            |

That's **~7.5% of the entire codebase** (187 files, ~18,500 lines) that can be cut with zero to low risk.

---

## 1. Dead Code — Delete Entirely (783 lines)

These files have **zero production references**. They were likely built for a feature that was never completed or was replaced by a different approach.

### 1.1 Dead Domain/Widget Files (171 lines)

| File                                    | Lines | What It Was                           | Why Dead                                             |
| --------------------------------------- | ----- | ------------------------------------- | ---------------------------------------------------- |
| `domain/widget/WidgetTransformFrame.kt` | 78    | Widget frame transformation math      | Widget sizing uses `WidgetHostSizingSupport` instead |
| `domain/widget/WidgetSpanPolicy.kt`     | 50    | Widget span placement recommendations | Not integrated into widget placement flow            |
| `domain/widget/WidgetLayoutPolicy.kt`   | 43    | Inline widget span fitting            | Not integrated into widget placement flow            |

**Action:** Delete all 3 files. Widget placement is handled by `HomeModelWriter` + `WidgetHostSizingSupport`.

### 1.2 Dead Domain/Drag/Drop Files (21 lines)

| File                                     | Lines | What It Was                       | Why Dead                                |
| ---------------------------------------- | ----- | --------------------------------- | --------------------------------------- |
| `domain/drag/drop/DropTargetNode.kt`     | 6     | Generic drop target interface     | Replaced by direct drop action handlers |
| `domain/drag/drop/DropTargetRegistry.kt` | 15    | Registry that dispatches to nodes | No production code uses it              |

**Action:** Delete both files. Drop handling is done directly in `DraggablePinnedItemsGrid` and `ExternalDropRoutingLayer`.

### 1.3 Dead UI Gesture Detector (398 lines)

| File                                         | Lines | What It Was                    | Why Dead                                                                           |
| -------------------------------------------- | ----- | ------------------------------ | ---------------------------------------------------------------------------------- |
| `ui/interaction/grid/DragGestureDetector.kt` | 398   | Reusable drag gesture detector | The app uses Compose's `detectDragGestures` directly in `DraggablePinnedItemsGrid` |

This is the single largest dead file. It was built as a reusable gesture detector with tap/long-press/drag detection, multi-touch safety, and haptic coordination — but the actual drag-and-drop implementation bypasses it entirely and uses Compose Foundation's built-in gesture detection.

**Action:** Delete the file. All drag detection is handled by `DraggablePinnedItemsGrid` using `detectDragGestures`.

### 1.4 Dead Test Files (193 lines)

The dead production files above have corresponding test files that should also be deleted:

| File                                                 | Lines | Tests                  |
| ---------------------------------------------------- | ----- | ---------------------- |
| `test/.../domain/widget/WidgetLayoutPolicyTest.kt`   | 64    | Widget layout policy   |
| `test/.../domain/widget/WidgetSpanPolicyTest.kt`     | 48    | Widget span policy     |
| `test/.../domain/widget/WidgetTransformFrameTest.kt` | 81    | Widget transform frame |

**Action:** Delete all 3 test files.

---

## 3. Over-Engineered Abstractions — Flatten (200 lines)

### 3.1 SearchViewModel Split Across 5 Files → 2 Files

**Current structure:**
| File | Lines | Purpose |
|------|-------|---------|
| `SearchViewModel.kt` | 316 | Main ViewModel |
| `SearchViewModelStateHolder.kt` | 87 | State flow management |
| `SearchViewModelPipelineCoordinator.kt` | 179 | Search pipeline orchestration |
| `SearchViewModelSettingsAdapter.kt` | 134 | Settings-to-registry adapter |
| `SearchViewModelModels.kt` | 36 | Internal data classes |
| **Total** | **752** | |

**Simplification:**

| Action                                                                   | File                                    | Lines Saved | Risk   |
| ------------------------------------------------------------------------ | --------------------------------------- | ----------- | ------ |
| Inline `PipelineCoordinator` into `SearchViewModel` as private functions | `SearchViewModelPipelineCoordinator.kt` | ~120        | Medium |
| Merge `SearchViewModelModels.kt` data classes into `SearchViewModel.kt`  | `SearchViewModelModels.kt`              | ~36         | Low    |
| Remove `SearchRuntimeSettingsProjection` data class, use direct mapping  | `SearchViewModelSettingsAdapter.kt`     | ~25         | Low    |
| Merge `presentationState` intermediate combine into main combine         | `SearchViewModelStateHolder.kt`         | ~6          | Low    |

**Result:** 5 files → 2 files (`SearchViewModel.kt` + `SearchViewModelSettingsAdapter.kt`), ~752 → ~565 lines. **Savings: ~187 lines.**

### 3.2 HomeMutationHandler Interface — Delete (32 lines)

`HomeMutationHandler.kt` is a 32-line interface with 3 methods (`pinFile`, `pinContact`, `unpinItem`). `HomeViewModel` is the **only** implementation. The interface exists so `ActionExecutor` can depend on a "small contract" rather than the full ViewModel.

**Action:** Make the 3 methods `internal` on `HomeViewModel` directly. Delete `HomeMutationHandler.kt`. Update `ActionExecutor` to take `HomeViewModel` (or a lambda).

**Savings:** 32 lines + 1 import in HomeViewModel.

### 3.3 HomeViewModel 3-Layer Mutation Chain — Merge (15 lines)

Current chain: `launchMutation` → `applyWriterCommandOrShowError` → `applyWriterCommand`

The middle function only exists to catch exceptions and set error messages. This can be a single function with try/catch.

**Savings:** ~15 lines

### 3.4 HomeModelWriter Command.execute() Indirection — Simplify (80 lines)

Each of the 17 Command data classes has an `execute()` method that just calls `writer.someMethod(currentItems, this)`. This is pure indirection — commands are never serialized, logged, or replayed.

**Action:** Replace `command.execute(writer, items)` with direct `writer.someMethod(items, command)`. Remove `execute()` overrides from all 17 command classes.

**Risk:** HIGH — this is a core domain abstraction. Do this last, with good test coverage.

**Savings:** ~80 lines

---

## 6. Comment/Doc Bloat in Source Files

Beyond the DI modules, these files have excessive inline documentation:

| File                              | Total Lines | Code Lines | Comment Lines | Action                          |
| --------------------------------- | ----------- | ---------- | ------------- | ------------------------------- |
| `FileFilterConfig.kt`             | 561         | ~300       | ~260          | Move educational notes to docs/ |
| `SearchProviderRegistry.kt`       | 352         | ~150       | ~200          | Move ASCII diagrams to docs/    |
| `AppQueryRanker.kt`               | 283         | ~200       | ~80           | Trim scoring constant comments  |
| `LauncherBackupRepositoryImpl.kt` | 433         | ~350       | ~83           | Trim per-item sanitization docs |
| `HomeItem.kt`                     | 609         | ~450       | ~159          | Trim factory method docs        |

**Total comment bloat: ~782 lines** that could be moved to `docs/` or trimmed.

---

## 7. Recommended Action Plan

### Phase 1: Zero-Risk Deletions (Day 1)

**Savings: 783 lines, 0 risk**

- [ ] Delete `WidgetTransformFrame.kt` (78 lines)
- [ ] Delete `WidgetSpanPolicy.kt` (50 lines)
- [ ] Delete `WidgetLayoutPolicy.kt` (43 lines)
- [ ] Delete `DropTargetNode.kt` (6 lines)
- [ ] Delete `DropTargetRegistry.kt` (15 lines)
- [ ] Delete `DragGestureDetector.kt` (398 lines)
- [ ] Delete 3 corresponding test files (193 lines)
- [ ] Remove unused sealed class variants (~10 lines)
- [ ] Remove `MILLISECONDS_PER_SECOND` constant (1 line)

### Phase 2: Documentation Trim (Day 2)

**Savings: 270 lines, 0 risk**

- [ ] Trim `SearchModule.kt` KDoc (~150 lines)
- [ ] Trim `WidgetModule.kt` KDoc (~40 lines)
- [ ] Trim `DrawerModule.kt` KDoc (~25 lines)
- [ ] Trim `AppModule.kt` ASCII art (~55 lines)
- [ ] Trim other DI module KDoc (~40 lines)

### Phase 3: Low-Risk Simplifications (Days 3-4)

**Savings: ~100 lines, low risk**

- [ ] Delete `HomeMutationHandler.kt` interface (32 lines)
- [ ] Merge HomeViewModel 3-layer mutation chain (15 lines)
- [ ] Unify PermissionHandler launcher registrations (15 lines)
- [ ] Merge PermissionHandler accessState methods (12 lines)
- [ ] Remove `RemoveItemById` command (9 lines)
- [ ] Merge `SearchViewModelModels.kt` into `SearchViewModel.kt` (36 lines)
- [ ] Remove `SearchRuntimeSettingsProjection` (25 lines)
- [ ] Remove Coil from version catalog (1 line)

### Phase 4: Medium-Risk Consolidations (Days 5-7)

**Savings: ~200 lines, medium risk**

- [ ] Create `RecentListStorage<T>` base class (~152 lines saved)
- [ ] Create `DrawableConstantStateLruCache` base (~30 lines saved)
- [ ] Create `NewlineJsonListSerializer<T>` (~15 lines saved)
- [ ] Inline `SearchViewModelPipelineCoordinator` (~120 lines saved)

### Phase 5: High-Risk Refactoring (Week 2+)

**Savings: ~80 lines, high risk**

- [ ] Simplify `HomeModelWriter.Command.execute()` indirection (~80 lines)
  - Requires good test coverage first
  - Core domain abstraction — changes ripple through the codebase

---

## 8. Impact Summary

| Metric          | Before     | After        | Change                        |
| --------------- | ---------- | ------------ | ----------------------------- |
| Source files    | 187        | ~175         | -12 files                     |
| Total lines     | ~18,500    | ~17,100      | -1,400 lines (-7.5%)          |
| Dead code       | 783 lines  | 0            | 100% removed                  |
| DI module bloat | ~657 lines | ~387         | -270 lines                    |
| APK size        | Baseline   | -1.5 to -2MB | From dependency audit         |
| Cognitive load  | High       | Medium       | Fewer files, less indirection |

### Biggest Wins

1. **Delete `DragGestureDetector.kt`** — 398 lines of unused gesture detection code. Single biggest cut.
2. **Delete dead widget domain files** — 171 lines of unused widget policy code.
3. **Trim DI module documentation** — 270 lines of KDoc that explains Koin basics.
4. **Consolidate recent storage** — 3 nearly-identical files → 1 generic base + 3 thin subclasses.
5. **Flatten SearchViewModel** — 5 files → 2 files, reducing cognitive overhead significantly.

---

## 9. What NOT to Simplify

These areas are complex but **correctly so** — simplifying them would introduce bugs:

| Area                                    | Why Keep Complex                                                               |
| --------------------------------------- | ------------------------------------------------------------------------------ |
| `HomeModelWriter` Command pattern       | Provides atomic, testable mutations. Flattening would couple mutation logic.   |
| `AsyncSnapshotCache`                    | Load deduplication and cancellation are non-trivial and correctly implemented. |
| `GridReorderEngine`                     | Deterministic reorder is hard; the complexity is justified.                    |
| `SearchProviderRegistry` prefix routing | Multi-provider prefix matching is inherently complex.                          |
| `WidgetHostManager` lifecycle           | AppWidgetHost lifecycle is fragile; the careful state management is necessary. |
| `HomeAvailabilityPruner`                | ContentObserver + DataStore sync is correctly complex.                         |
