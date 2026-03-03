# App Icon Memory Cache - Detailed Documentation

## Overview

The launcher now uses a **direct PackageManager + in-memory cache** pipeline for app icons.
This replaces the older Coil-based icon flow for launcher app icons.

Why this change was made:
- Launcher UX requires icons to appear immediately.
- Per-item async image pipeline overhead was visible as a delay.
- App icons are local system resources (not remote network images), so a simpler path is better.

---

## Problem We Observed

After migrating app icons through Coil, icons started appearing with a noticeable delay.

Even though Coil is excellent for many image workloads, launcher app icons have a different profile:
- Local source (`PackageManager`), not network.
- Very high frequency and repeated rendering (search list, grid, pinned items).
- Strict first-frame expectation from users.

For this specific workload, a custom direct path is faster and simpler.

---

## New Architecture

### 1) Preload During App Discovery

When installed apps are loaded in `AppRepositoryImpl`, we already iterate every launcher activity.
At that moment we also preload each icon into `AppIconMemoryCache`.

This means that by the time UI composables render app rows/cells, most icons are already memory hits.

### 2) Fast Synchronous Cache Read in UI

`AppIcon` composable now:
1. Tries `AppIconMemoryCache.get(packageName)` immediately.
2. If hit: renders icon instantly.
3. If miss: launches background load (`Dispatchers.IO`) via `getOrLoad`, caches result, and updates UI.

### 3) Direct Drawable Rendering

`AppIcon` renders with `AndroidView` + `ImageView` so we can display Drawables directly without extra conversion/pipeline stages.

---

## File Map

- `app/src/main/java/com/milki/launcher/data/icon/AppIconMemoryCache.kt`
  - Dedicated thread-safe LRU cache for app icon drawables.
- `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt`
  - Preloads icons while installed/recent app models are built.
- `app/src/main/java/com/milki/launcher/ui/components/AppIcon.kt`
  - Cache-first icon rendering composable with IO fallback loading.

---

## Cache Design Details

### Why `Drawable.ConstantState`?

The cache stores `Drawable.ConstantState` instead of raw `Drawable` instances.

Reason:
- `Drawable` objects are stateful and can be mutated.
- Returning the same `Drawable` object to multiple UI consumers can cause subtle visual/state bugs.
- `ConstantState.newDrawable()` creates a fresh drawable each time while still sharing underlying icon resources.

### Why an entry-count LRU cache?

A launcher usually has a few hundred apps.
A count-based cap is easy to reason about and predictable for this use case.

Current default:
- `MAX_ENTRIES = 300`

This can be tuned later based on profiling and real device behavior.

---

## Threading Model

- Repository preloading runs on background dispatcher (`Dispatchers.IO.limitedParallelism(8)`).
- UI fallback load on cache miss runs on `Dispatchers.IO` from `LaunchedEffect`.
- Cache access is synchronized because `LruCache` is not guaranteed thread-safe for concurrent reads/writes.

---

## Behavioral Guarantees

- If package exists and icon is cached: icon appears in first composition.
- If package exists and icon is not cached: default icon is shown briefly, then replaced once background load completes.
- If package no longer exists: default activity icon is used safely.

---

## Why We Removed Coil for App Icons

This decision is **scope-specific**:
- We removed Coil only from launcher app icon rendering.
- Reason is workload fit and startup responsiveness.

In general apps, Coil is still a strong library for:
- Network images
- Transformations
- Complex image request pipelines

For launcher app icons, direct PackageManager + memory cache is usually the most performant and understandable approach.

---

## Future Improvements (Optional)

If needed later, we can add:
- Memory pressure hooks (trim cache aggressively on low memory callbacks).
- Size-aware cache tuning based on number of installed apps.
- Benchmark traces around first-render and scroll performance.

The current implementation intentionally prioritizes simplicity and immediate UX improvement.
