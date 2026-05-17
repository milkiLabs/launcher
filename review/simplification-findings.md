# Simplification Findings

> Opportunities to cut code, reduce complexity, shrink APK size, and improve maintainability.
> Audited 2026-05-18 across 187 source files.

---

## Executive Summary

| Category | Files Affected | Lines Cuttable | Risk |
|----------|---------------|----------------|------|
| **Dead code (delete entirely)** | 9 files + 3 tests | **~783** | None |
| **Documentation bloat (trim)** | 7 DI module files | **~270** | None |
| **Over-engineered abstractions (flatten)** | 8 files | **~200** | Low-Medium |
| **Merge opportunities (consolidate)** | 6 files | **~130** | Low-Medium |
| **Unused sealed variants (prune)** | 3 files | **~10** | None |
| **Total potential reduction** | | **~1,393 lines** | |

That's **~7.5% of the entire codebase** (187 files, ~18,500 lines) that can be cut with zero to low risk.

---

## 1. Dead Code — Delete Entirely (783 lines)

These files have **zero production references**. They were likely built for a feature that was never completed or was replaced by a different approach.

### 1.1 Dead Domain/Widget Files (171 lines)

| File | Lines | What It Was | Why Dead |
|------|-------|-------------|----------|
| `domain/widget/WidgetTransformFrame.kt` | 78 | Widget frame transformation math | Widget sizing uses `WidgetHostSizingSupport` instead |
| `domain/widget/WidgetSpanPolicy.kt` | 50 | Widget span placement recommendations | Not integrated into widget placement flow |
| `domain/widget/WidgetLayoutPolicy.kt` | 43 | Inline widget span fitting | Not integrated into widget placement flow |

**Action:** Delete all 3 files. Widget placement is handled by `HomeModelWriter` + `WidgetHostSizingSupport`.

### 1.2 Dead Domain/Drag/Drop Files (21 lines)

| File | Lines | What It Was | Why Dead |
|------|-------|-------------|----------|
| `domain/drag/drop/DropTargetNode.kt` | 6 | Generic drop target interface | Replaced by direct drop action handlers |
| `domain/drag/drop/DropTargetRegistry.kt` | 15 | Registry that dispatches to nodes | No production code uses it |

**Action:** Delete both files. Drop handling is done directly in `DraggablePinnedItemsGrid` and `ExternalDropRoutingLayer`.

### 1.3 Dead UI Gesture Detector (398 lines)

| File | Lines | What It Was | Why Dead |
|------|-------|-------------|----------|
| `ui/interaction/grid/DragGestureDetector.kt` | 398 | Reusable drag gesture detector | The app uses Compose's `detectDragGestures` directly in `DraggablePinnedItemsGrid` |

This is the single largest dead file. It was built as a reusable gesture detector with tap/long-press/drag detection, multi-touch safety, and haptic coordination — but the actual drag-and-drop implementation bypasses it entirely and uses Compose Foundation's built-in gesture detection.

**Action:** Delete the file. All drag detection is handled by `DraggablePinnedItemsGrid` using `detectDragGestures`.

### 1.4 Dead Test Files (193 lines)

The dead production files above have corresponding test files that should also be deleted:

| File | Lines | Tests |
|------|-------|-------|
| `test/.../domain/widget/WidgetLayoutPolicyTest.kt` | 64 | Widget layout policy |
| `test/.../domain/widget/WidgetSpanPolicyTest.kt` | 48 | Widget span policy |
| `test/.../domain/widget/WidgetTransformFrameTest.kt` | 81 | Widget transform frame |

**Action:** Delete all 3 test files.

### 1.5 Unused Sealed Class Variants (~10 lines)

These enum/sealed variants are **never instantiated** in production code:

| File | Unused Variant | Lines |
|------|---------------|-------|
| `domain/drag/reorder/ReorderPlan.kt:6-7` | `ReorderRejectReason.OCCUPIED_TARGET`, `OUT_OF_BOUNDS` | ~4 |
| `domain/drag/drop/DropDecision.kt:14-15` | `RejectReason.OUT_OF_BOUNDS`, `INTERNAL_ERROR` | ~4 |
| `domain/drag/drop/DropDecision.kt:5` | `DropDecision.Accepted` | ~2 |

**Action:** Remove unused variants. Keep only the ones actually produced:
- `ReorderRejectReason` → only `NO_SPACE`
- `RejectReason` → `OCCUPIED_TARGET`, `INVALID_FOLDER_ROUTE`, `INVALID_WIDGET_ROUTE`, `PAYLOAD_UNSUPPORTED`
- `DropDecision` → `Pass`, `Rejected`

---

## 2. Documentation Bloat — Trim (270 lines)

The DI modules have an extreme documentation-to-code ratio. The KDoc explains Koin basics and Android framework behavior that any Android developer already knows.

### 2.1 SearchModule.kt — 241 lines for ~20 lines of code

| Section | Lines | Content | Can Remove? |
|---------|-------|---------|-------------|
| File-level KDoc | ~120 | Explains what each `single {}` does (Koin basics) | YES |
| Per-binding KDoc | ~80 | "This provides X which is used by Y" | YES |
| Actual code | ~20 | `single {}` bindings | NO |

**Before:**
```kotlin
/**
 * Search module provides all search-related dependencies.
 *
 * SEARCH PROVIDER REGISTRY:
 * The SearchProviderRegistry is the central registry that...
 * [30 more lines of explanation]
 */
val searchModule = module {
    /**
     * Provides the SearchProviderRegistry which...
     * [10 more lines]
     */
    single<SearchProviderRegistry> { SearchProviderRegistry() }
}
```

**After:**
```kotlin
val searchModule = module {
    single<SearchProviderRegistry> { SearchProviderRegistry() }
    // ... other bindings
}
```

**Savings:** ~150 lines

### 2.2 WidgetModule.kt — 80 lines for 2 bindings

| Section | Lines | Content | Can Remove? |
|---------|-------|---------|-------------|
| File-level KDoc | ~40 | Explains why AppWidgetHost should be singleton | YES |
| Per-binding KDoc | ~20 | Framework behavior explanation | YES |
| Actual code | ~20 | 2 bindings | NO |

**Savings:** ~40 lines

### 2.3 DrawerModule.kt — 74 lines for 1 binding

| Section | Lines | Content | Can Remove? |
|---------|-------|---------|-------------|
| File-level KDoc | ~28 | "Why a separate module for one ViewModel" | YES |
| Actual code | ~5 | 1 binding | NO |

**Savings:** ~25 lines

### 2.4 AppModule.kt — 92 lines, ASCII art diagram

| Section | Lines | Content | Can Remove? |
|---------|-------|---------|-------------|
| ASCII art + module list KDoc | ~55 | Visual dependency diagram | YES (move to docs/) |
| Actual code | ~7 | `listOf()` | NO |

**Savings:** ~55 lines

### 2.5 Other DI Modules

| File | Total | Code | Bloat | Savings |
|------|-------|------|-------|---------|
| `CoreModule.kt` | 73 | ~40 | ~33 | ~20 |
| `HomeModule.kt` | 39 | ~20 | ~19 | ~10 |
| `SettingsModule.kt` | 36 | ~20 | ~16 | ~10 |

**Total documentation bloat savings: ~270 lines**

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

| Action | File | Lines Saved | Risk |
|--------|------|-------------|------|
| Inline `PipelineCoordinator` into `SearchViewModel` as private functions | `SearchViewModelPipelineCoordinator.kt` | ~120 | Medium |
| Merge `SearchViewModelModels.kt` data classes into `SearchViewModel.kt` | `SearchViewModelModels.kt` | ~36 | Low |
| Remove `SearchRuntimeSettingsProjection` data class, use direct mapping | `SearchViewModelSettingsAdapter.kt` | ~25 | Low |
| Merge `presentationState` intermediate combine into main combine | `SearchViewModelStateHolder.kt` | ~6 | Low |

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

### 3.5 PermissionHandler — Unify Launcher Registrations (15 lines)

Four nearly identical `registerForActivityResult` blocks (contacts, call, files, storage). Each is 5-8 lines of boilerplate.

**Action:** Create a single `registerPermissionLauncher(permission, contract, updateState)` helper.

**Savings:** ~15 lines

### 3.6 PermissionHandler — Merge accessState Methods (12 lines)

`accessStateForRuntimePermission` and `accessStateForSpecialPermission` have identical structure with only the permission check function differing.

**Action:** Single method with `checkGranted: () -> Boolean` parameter.

**Savings:** ~12 lines

---

## 4. Merge Opportunities — Consolidate (130 lines)

### 4.1 Recent Storage Classes → Generic Base (80 lines)

Three files follow the **identical** "LRU list stored as CSV in DataStore" pattern:

| File | Lines | Data Type |
|------|-------|-----------|
| `data/repository/apps/RecentAppsStore.kt` | ~75 | `String` (app IDs) |
| `data/repository/ContactsRecentStorage.kt` | ~83 | `String` (contact IDs) |
| `data/repository/FilesRecentStorage.kt` | ~79 | `Long` (file IDs) |

All three have identical `saveRecent()` (remove old, add front, `take(max)`) and `observeRecent()` (DataStore flow → parse CSV → map) logic.

**Action:** Create a generic `RecentListStorage<T>` base class (~40 lines) with 3 thin subclasses (~15 lines each).

**Current:** 75 + 83 + 79 = 237 lines
**After:** 40 + 15 + 15 + 15 = 85 lines
**Savings:** ~152 lines

### 4.2 Icon Memory Caches → Shared LRU Base (30 lines)

`AppIconMemoryCache` and `ShortcutIconMemoryCache` share identical `LruCache<String, Drawable.ConstantState>` patterns:
- `get()` → `synchronized` → `newDrawable().mutate()`
- `preload()` → `synchronized` → null check → `cache.put()`
- `clear()` → `evictAll()`

**Action:** Create a `DrawableConstantStateLruCache` base class. Each subclass adds its specific logic (telemetry, disk triggers, etc.).

**Savings:** ~30 lines

### 4.3 HomeItemSerializer + ActionShortcutSerializer → Generic (15 lines)

Both serialize newline-separated JSON lists with identical parsing:
```kotlin
split("\n").filter { it.isNotBlank() }.mapNotNull { runCatching { json.decodeFromString(it) }.getOrNull() }
```

**Action:** Create a `NewlineJsonListSerializer<T>` generic class.

**Savings:** ~15 lines

### 4.4 RemoveItemById → Use RemoveItemsById (9 lines)

`HomeModelWriter.RemoveItemById` wraps a single ID and delegates to `RemoveItemsById(setOf(itemId))`. This is unnecessary indirection.

**Action:** Delete `RemoveItemById`. Callers use `RemoveItemsById(setOf(id))` directly.

**Savings:** 9 lines

### 4.5 FilesRepositoryImpl — Remove Unused Constant (1 line)

`MILLISECONDS_PER_SECOND = 1_000L` at line 27 is never referenced anywhere in the file.

**Savings:** 1 line

---

## 5. Dependency Removal

### 5.1 Coil — Remove from Version Catalog

Coil is defined in `libs.versions.toml` (coil = "2.7.0") but commented out in `app/build.gradle.kts`. If not planned for use, remove it.

**Savings:** 1 line in catalog, cleaner dependency list

### 5.2 Compose Material Icons Extended — Audit Usage

The extended icon pack adds ~1.5MB to the APK. Audit which icons are actually used. If only a handful, switch to `material-icons-core` and use vector drawables for the rest.

**Potential APK savings:** ~1-1.5MB

### 5.3 Koin android vs androidx-compose Overlap

For a Compose-only app, `koin-android` (which provides Android-specific integrations like `viewModel()`) may partially overlap with `koin-androidx-compose`. Audit whether both are needed.

**Potential APK savings:** ~200KB

---

## 6. Comment/Doc Bloat in Source Files

Beyond the DI modules, these files have excessive inline documentation:

| File | Total Lines | Code Lines | Comment Lines | Action |
|------|-------------|------------|---------------|--------|
| `FileFilterConfig.kt` | 561 | ~300 | ~260 | Move educational notes to docs/ |
| `SearchProviderRegistry.kt` | 352 | ~150 | ~200 | Move ASCII diagrams to docs/ |
| `AppQueryRanker.kt` | 283 | ~200 | ~80 | Trim scoring constant comments |
| `LauncherBackupRepositoryImpl.kt` | 433 | ~350 | ~83 | Trim per-item sanitization docs |
| `HomeItem.kt` | 609 | ~450 | ~159 | Trim factory method docs |

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

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Source files | 187 | ~175 | -12 files |
| Total lines | ~18,500 | ~17,100 | -1,400 lines (-7.5%) |
| Dead code | 783 lines | 0 | 100% removed |
| DI module bloat | ~657 lines | ~387 | -270 lines |
| APK size | Baseline | -1.5 to -2MB | From dependency audit |
| Cognitive load | High | Medium | Fewer files, less indirection |

### Biggest Wins

1. **Delete `DragGestureDetector.kt`** — 398 lines of unused gesture detection code. Single biggest cut.
2. **Delete dead widget domain files** — 171 lines of unused widget policy code.
3. **Trim DI module documentation** — 270 lines of KDoc that explains Koin basics.
4. **Consolidate recent storage** — 3 nearly-identical files → 1 generic base + 3 thin subclasses.
5. **Flatten SearchViewModel** — 5 files → 2 files, reducing cognitive overhead significantly.

---

## 9. What NOT to Simplify

These areas are complex but **correctly so** — simplifying them would introduce bugs:

| Area | Why Keep Complex |
|------|-----------------|
| `HomeModelWriter` Command pattern | Provides atomic, testable mutations. Flattening would couple mutation logic. |
| `AsyncSnapshotCache` | Load deduplication and cancellation are non-trivial and correctly implemented. |
| `GridReorderEngine` | Deterministic reorder is hard; the complexity is justified. |
| `SearchProviderRegistry` prefix routing | Multi-provider prefix matching is inherently complex. |
| `WidgetHostManager` lifecycle | AppWidgetHost lifecycle is fragile; the careful state management is necessary. |
| `HomeAvailabilityPruner` | ContentObserver + DataStore sync is correctly complex. |
