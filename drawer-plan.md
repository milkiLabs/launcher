# Drawer Optimization Plan

## Benchmark Diagnosis

### Cold Start: 694ms TTID

| Component                         |      Time | % of TTID |
| --------------------------------- | --------: | --------: |
| `resolveLabel` (28 apps × PM IPC) | **673ms** |   **97%** |
| `queryLauncherActivities`         |      17ms |      2.5% |
| `Activity.onCreate`               |      63ms |  overlaps |
| `setContent`                      |      22ms |  overlaps |

**Root cause:** `resolveInfo.loadLabel(packageManager)` does IPC per app. Cold PM cache = ~24ms/app.

### Scroll: P95 frameOverrun = 240ms, P99 = 333ms

**Root cause:** Per-item `AppGridItem` is extremely heavy. Each cell composes:

1. `rememberItemContextMenuState()` → state + mutableStateOf
2. `rememberAppQuickActions()` → state + LaunchedEffect + coroutine
3. `remember { buildAppItemMenuActions() }` → list allocation
4. `IconLabelLayout(...)` → data class allocation
5. `Surface` → full layout node
6. `detectDragGesture(...)` → PointerInput scope + multiple coroutines
7. `IconLabelCell` → wrapper Column + Box + Text
8. `ItemActionMenu(...)` → DropdownMenu composable (even when hidden!)

That's ~10+ objects and 2-3 coroutines **per cell**. During scroll, new cells compose every frame.

## Solution

### 1. Label Cache → Fix cold start from 694ms to ~80ms

- Cache labels to SharedPreferences after first PM resolution
- On subsequent cold starts, read cached labels (< 5ms) instead of PM IPC (673ms)
- Cache miss falls back to PM IPC (handles new installs)
- Package change triggers fresh PM resolution (PM is warm by then)

### 2. Lightweight Drawer Cell → Fix scroll from P95=240ms to target <16ms

- Replace `AppGridItem` with minimal `DrawerGridCell`: just `combinedClickable` + `AppIcon` + `Text`
- **No** per-item context menu state, quick actions, drag gesture detector, or ItemActionMenu
- Context menu composed **only for the one long-pressed item** (shared state at drawer level)
- Drag-to-homescreen initiated from context menu instead of custom gesture detector

### 3. Icon Preloading → Eliminate scroll tail jank

- When drawer items become available, batch-preload all icons on IO
- `AppIconMemoryCache.preloadMissing()` already exists — just call it
- Eliminates per-item `LaunchedEffect` icon loads during scroll

## Files Changed

1. **New:** `AppLabelCache.kt` — SharedPreferences label cache
2. **Modified:** `InstalledAppsCatalog.kt` — Use label cache
3. **Modified:** `AppDrawerOverlay.kt` — Lightweight cells + shared menu + preloading

---

# Drawer Performance Optimization — Changes Summary

## What the benchmarks showed

| Problem     | Metric           |     Value | Root Cause                                               |
| ----------- | ---------------- | --------: | -------------------------------------------------------- |
| Cold start  | TTID             | **694ms** | `loadLabel()` PM IPC: 673ms for 28 apps                  |
| Scroll jank | frameOverrun P95 | **240ms** | Heavy `AppGridItem`: ~10 objects + 3 coroutines per cell |
| Scroll jank | frameOverrun P99 | **333ms** | Icon cache misses during scroll                          |

## Three changes made

### 1. Label Cache → Cold start fix

**New file:** [AppLabelCache.kt](file:///media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/repository/apps/AppLabelCache.kt)
**Modified:** [InstalledAppsCatalog.kt](file:///media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/data/repository/apps/InstalledAppsCatalog.kt)

- Caches app labels to SharedPreferences after PM resolution
- On cold start, reads cached labels (~5ms) instead of PM IPC (~673ms)
- Cache misses (new installs) fall back to PM
- Subsequent loads (package broadcasts) always resolve fresh, then update cache

**Expected impact:** TTID drops from ~694ms to ~80-100ms (after first run)

### 2. Lightweight DrawerGridCell → Scroll fix

**Modified:** [AppDrawerOverlay.kt](file:///media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/ui/components/launcher/AppDrawerOverlay.kt)

Per-cell overhead removed:

| Component             | Old (`AppGridItem`)                    | New (`DrawerGridCell`)        |
| --------------------- | -------------------------------------- | ----------------------------- |
| Context menu state    | Every cell                             | None                          |
| Quick actions load    | Every cell (LaunchedEffect)            | None                          |
| Drag gesture detector | Every cell (PointerInput + coroutines) | None                          |
| Surface wrapper       | Every cell                             | None                          |
| IconLabelLayout alloc | Every cell                             | None                          |
| ItemActionMenu        | Every cell (even hidden)               | Only long-pressed cell        |
| Gesture handling      | `detectDragGesture` (complex)          | `combinedClickable` (minimal) |

Context menu is now shared at the drawer level — only composed for the **one** long-pressed item. Drag-to-homescreen is initiated from the context menu.

> [!NOTE]
> `RecentlyChangedAppsRow` still uses `AppGridItem` since it's a small fixed-size row (max 4-6 items) where the overhead is negligible and drag support is useful.

**Expected impact:** P95 frame time should drop from 144ms toward 8-16ms range

### 3. Batch Icon Preloading → Scroll tail jank fix

**In:** [AppDrawerOverlay.kt](file:///media/Maind/zahrawi/productivity/launcher/app/src/main/java/com/milki/launcher/ui/components/launcher/AppDrawerOverlay.kt#L106-L116)

- When drawer items arrive, all icons are preloaded on IO thread via `AppIconMemoryCache.preloadMissing()`
- Eliminates per-item icon loading during scroll (which caused the P99=333ms overrun spikes)

**Expected impact:** Eliminates icon-loading-related frame drops during scroll

## Build status

✅ `compileDebugKotlin` passes

## What to benchmark next

Re-run all six scenarios from `drawer-bench` to compare against baseline. Key metrics to watch:

- `coldStartupToDrawer` → `resolveLabelSumMs` and `timeToInitialDisplayMs`
- `scrollDrawerFromHomescreen` → `frameDurationCpuMs` P95/P99 and `frameOverrunMs` P95/P99
- `openDrawerFromHomescreen` → should also improve from lighter cells
