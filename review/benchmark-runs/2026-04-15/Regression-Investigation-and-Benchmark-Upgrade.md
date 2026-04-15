# Regression Investigation And Benchmark Upgrade (2026-04-15)

Device: Redmi Note 9S (Android 12 / API 31, non-root)

## Executive Summary

The startup regression is primarily explained by cold-start-only installed-app catalog work, especially icon preload work executed while startup is in progress.

Evidence now comes from trace-section metrics collected inside the benchmark run, not just top-line startup timing.

## Why Startup Regressed

### 1. Cold startup triggers heavy app catalog work

In the diagnostic cold run:

- `timeToInitialDisplayMs` median: `613.9 ms`
- `launcher.apps_catalog.preload_icon_sum_millis_median`: `6123.9 ms`
- `launcher.apps_catalog.preload_icon_count_median`: `62.5`
- `launcher.apps_catalog.query_launcher_activities_sum_millis_median`: `18.9 ms`
- `launcher.startup.main_activity.on_create_sum_millis_median`: `43.8 ms`
- `launcher.startup.set_content_sum_millis_median`: `25.0 ms`

Source: `review/benchmark-runs/2026-04-15/coldStartupToHomescreen.trace-diagnostic.v2.log`

Interpretation:

- Startup host setup itself is modest (`onCreate` + `setContent` around tens of milliseconds).
- The dominant additional workload in cold runs is app icon preload fan-out (`preloadIcon` sections), which does not appear in warm/hot runs.

### 2. Warm/hot runs confirm the issue is cold-path specific

Warm startup:

- `timeToInitialDisplayMs` median: `82.9 ms`
- `launcher.apps_catalog.preload_icon_sum_millis_median`: `0.0`

Hot startup:

- `timeToInitialDisplayMs` median: `87.0 ms`
- `launcher.apps_catalog.preload_icon_sum_millis_median`: `0.0`

Sources:

- `review/benchmark-runs/2026-04-15/warmStartupToHomescreen.log`
- `review/benchmark-runs/2026-04-15/hotStartupToHomescreen.log`

Interpretation:

- When app catalog preload does not run, startup latency is much lower.
- This strongly localizes the regression to cold initialization paths.

### 3. Eager repository refresh design contributes to cold-start contention

`AppRepositoryImpl` eagerly emits an initial refresh trigger and immediately reloads installed apps:

- `onStart { emit(Unit) }`
- `refreshTriggers.collectLatest { refreshInstalledAppsSnapshot() }`
- `refreshInstalledAppsSnapshot()` -> `installedAppsCatalog.loadInstalledApps()`

File: `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt`

This behavior is correct functionally but expensive for first frame on low/mid-tier devices.

## Benchmarking/Profiling Upgrades Applied

### New startup profiling signals

Added startup trace sections measured directly by macrobenchmark:

- `launcher.startup.mainActivity.onCreate`
- `launcher.startup.runtime.initialize`
- `launcher.startup.setContent`
- `launcher.appsCatalog.queryLauncherActivities`
- `launcher.appsCatalog.preloadIcon`
- `launcher.appsCatalog.resolveLabel`

Code:

- `app/src/main/java/com/milki/launcher/app/activity/MainActivity.kt`
- `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherHostRuntime.kt`
- `app/src/main/java/com/milki/launcher/data/repository/apps/InstalledAppsCatalog.kt`
- `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherHomeBenchmark.kt`

### Expanded benchmark coverage

Added startup and drawer scenarios:

1. `warmStartupToHomescreen`
2. `hotStartupToHomescreen`
3. `openDrawerFromHomescreen`
4. Existing `returnToHomescreenFromDrawer`

Code:

- `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherHomeBenchmark.kt`
- `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherBenchmarkDriver.kt`

### Improved seed determinism

Benchmark seeding now stabilizes candidate app selection via:

- distinct package filtering
- deterministic sort by package/activity

Code:

- `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherBenchmarkHomeSeeder.kt`

## New Drawer Direction Metrics

Open drawer from homescreen:

- frameDurationCpuMs: P90 `29.5`, P95 `63.2`, P99 `119.1`
- frameOverrunMs: P90 `74.2`, P95 `92.2`, P99 `160.2`

Source: `review/benchmark-runs/2026-04-15/openDrawerFromHomescreen.log`

Return home from drawer (v2):

- frameDurationCpuMs: P90 `64.8`, P95 `78.8`, P99 `92.6`
- frameOverrunMs: P90 `77.6`, P95 `88.8`, P99 `92.9`

Source: `review/benchmark-runs/2026-04-15/returnToHomescreenFromDrawer.v2.log`

## Recommended Next Code Patch

1. Defer full app icon preload off the first-frame-critical cold path.
2. Keep initial installed app metadata load lightweight, then warm icon cache after first frame or lazily by visible need.
3. Consider making the first repository refresh collector-driven instead of eager at repository init.
