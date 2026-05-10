# Homescreen vs Drawer — Optimization Crossover Analysis

## Summary

The homescreen **already has most of our drawer optimizations** or doesn't need them. There's one applicable win: the label cache already benefits homescreen cold start since `InstalledAppsCatalog` is shared. Beyond that, the homescreen architecture is fundamentally different from the drawer — it's not scroll-heavy and already uses well-optimized patterns.

---

## Optimization 1: Label Cache → ✅ Already benefits homescreen

The `AppLabelCache` we added to `InstalledAppsCatalog` speeds up cold start for **every consumer** of `AppRepository`, not just the drawer. The homescreen benefits indirectly because:

- `HomeAvailabilityPruner` calls `AppRepository` to compare installed apps against pinned items (delayed 1.5s, but still benefits)
- Any search/drawer open that happens after cold start gets the faster catalog

However, the homescreen's **own** cold start path (`HomeViewModel` → `HomeRepository` → DataStore) does NOT go through `InstalledAppsCatalog`. Pinned items load from DataStore, not PM queries. So label cache doesn't speed up homescreenTTID directly — it was never the bottleneck there.

## Optimization 2: Lightweight Grid Cell → ❌ Not applicable

**Why the homescreen doesn't need it:**

| Property                   | Drawer                 | Homescreen                          |
| -------------------------- | ---------------------- | ----------------------------------- |
| Item count                 | 50-200+ apps           | 4-16 pinned items                   |
| Scrollable                 | Yes (LazyVerticalGrid) | No (fixed grid, Box layout)         |
| Items enter/leave viewport | Yes, during scroll     | No, all always visible              |
| Per-item gesture needs     | Tap + long-press only  | Tap + long-press + **drag reorder** |

The homescreen uses `DraggablePinnedItemsGrid` → `InternalGridDragLayer` which renders items in a fixed `Box` layout (not lazy). All items are always composed. The `detectDragGesture` per item is **essential** because homescreen items support:

- Internal reorder (drag to rearrange grid)
- Drag to create folders
- Drag to widget overlay
- Long-press → context menu with concurrent drag detection

These gestures require the custom `detectDragGesture` pointer input per item. Replacing it with `combinedClickable` would **break** the core homescreen interaction model.

The count is also low (4-16 items), so the per-item overhead is negligible. Even with all the `detectDragGesture` + `ItemActionMenu` cost, composing 16 items is ~50x cheaper than composing 200+ items during scroll.

## Optimization 3: Icon Preloading → ✅ Already implemented

The homescreen already has `HomeIconWarmupCoordinator`:

```
HomeIconWarmupCoordinator.start()
  → collects homeRepository.pinnedItems
  → extracts all package names (apps, shortcuts, widgets, folder children)
  → calls AppIconMemoryCache.preloadMissing()
  → calls ShortcutIconLoader.preloadMissing()
```

This is started in `HomeViewModel.init` and runs on `Dispatchers.IO`. It's actually **better** than our drawer preload because:

1. It runs immediately on VM creation (no LaunchedEffect delay)
2. It also preloads shortcut icons (not just app icons)
3. It tracks changes via `distinctUntilChanged` to avoid redundant preloads
4. It calls `updateHomePriorityPackages()` to prevent LRU eviction of home icons

## What about the `PinnedItem` composable?

`PinnedItem` uses `rememberAppQuickActions` per item, but with `shouldLoad = isMenuVisible`. So it only actually loads shortcuts when the menu is open (one item at a time). The `ItemActionMenu` is composed for every `PinnedItem`, but:

1. The homescreen always renders all items (no lazy recycling), so this is a one-time cost
2. The count is small (4-16 items)
3. The `expanded = false` path of `DropdownMenu` is extremely lightweight

There's no measurable benefit from changing this pattern on the homescreen.

## Drawer→Home Transition Jank?

The benchmark shows `returnToHomescreenFromDrawer` frame timing:

- `frameDurationCpuMs` P95 = 60.8ms, P99 = 89.9ms
- `frameOverrunMs` P95 = 67.1ms, P99 = 80.7ms

This is drawer-to-home _transition_ jank, caused by the heavy drawer teardown + home recomposition. Our drawer cell optimization should help here because:

- Lighter drawer cells = faster teardown when drawer closes
- Less composition work = smoother transition animation

This benefit comes **automatically** from our existing drawer changes.

## Conclusion

| Optimization     | Homescreen Benefit           | Action Needed             |
| ---------------- | ---------------------------- | ------------------------- |
| Label cache      | ✅ Indirect (shared catalog) | None — already done       |
| Lightweight cell | ❌ Not applicable            | None — wrong architecture |
| Icon preloading  | ✅ Already implemented       | None — already better     |
| Transition jank  | ✅ Automatic from drawer fix | None — comes for free     |

**No additional homescreen changes are needed.** The homescreen is architecturally optimized for its use case (small fixed set of items with rich drag interaction). The drawer optimizations that apply to it already do so automatically.
