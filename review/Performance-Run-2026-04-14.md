# Performance Run - 2026-04-14 (Agent Handoff)

Device used for this run: Redmi Note 9S (Android 12 / API 31, non-root)

## Purpose

This document is the handoff for future AI agents to start performance patching immediately.
It contains:

1. The trusted benchmark workflow for this project.
2. The latest representative metrics.
3. Platform constraints that affect what can and cannot be measured on this device.
4. A prioritized patch plan with validation criteria.

## Benchmark Harness State (Important)

Benchmark setup now seeds a deterministic non-empty homescreen before each measured iteration.

Seeding details:

1. 16 pinned apps are placed in a 4x4 grid.
2. This is done via benchmark-only intent action `com.milki.launcher.action.BENCHMARK_PREPARE_HOME`.
3. Seeding logic now lives in `LauncherBenchmarkHomeSeeder.seed()` and is invoked by benchmark intent handling in `LauncherHostRuntime`.
4. Widget auto-seeding is intentionally not included (provider binding/config is device and provider dependent).

Files involved:

1. `app/src/main/java/com/milki/launcher/core/intent/LauncherActivityIntent.kt`
2. `app/src/main/java/com/milki/launcher/app/activity/MainActivity.kt`
3. `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherHostRuntime.kt`
4. `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherBenchmarkHomeSeeder.kt`
5. `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherBenchmarkTarget.kt`
6. `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherBenchmarkScenario.kt`
7. `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherHomeBenchmark.kt`

## Reproduction Workflow (Use This)

Preferred workflow on this phone uses manual APK install + direct instrumentation, because Gradle UTP install frequently hits `INSTALL_FAILED_USER_RESTRICTED`.

```bash
./gradlew :app:assembleBenchmark :baselineprofile:assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb install -r baselineprofile/build/outputs/apk/benchmark/baselineprofile-benchmark.apk

adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToHomescreenWithoutBaselineProfile' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#returnToHomescreenFromDrawer' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
```

Notes:

1. Keep method selectors quoted because `#` can be treated as shell syntax.
2. If install is blocked by user prompt, unlock phone and confirm install, then rerun the same command.

## Latest Representative Results

These numbers are from seeded (non-empty) homescreen runs.

### Cold Startup (baseline profile enabled path)

1. metric: `timeToInitialDisplayMs`
2. min: `505.2 ms`
3. median: `524.3 ms`
4. max: `564.8 ms`

### Cold Startup (baseline profile disabled path)

1. metric: `timeToInitialDisplayMs`
2. min: `409.6 ms`
3. median: `446.8 ms`
4. max: `555.0 ms`

### Drawer -> Home Frame Timing

1. `frameDurationCpuMs`: P50 `19.5`, P90 `49.4`, P95 `60.8`, P99 `89.9`
2. `frameOverrunMs`: P50 `23.5`, P90 `55.0`, P95 `67.1`, P99 `80.7`

Interpretation:

1. Startup is slower in baseline-enabled path on this device/run by about `77.5 ms` median.
2. Drawer->home path still has high tail latency and visible jank risk.

## Environment Constraints

1. Baseline profile collection fails on this device by design.
2. Reason: API 31 non-root cannot run `BaselineProfileRule.collect`.
3. Requirement for collection: API 33+ device or rooted API 28+ with `adb root`.

## Repo Tracking State

`baselineprofile/` source is now tracked by git.

Current policy:

1. Benchmark and profiling source/config files are tracked.
2. Generated benchmark build output remains ignored via `/baselineprofile/build`.

## Patch Queue For Future Agent

### P0 - Startup critical path reduction

Target files:

1. `app/src/main/java/com/milki/launcher/app/activity/MainActivity.kt`
2. `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherHostRuntime.kt`

Patch intent:

1. Split startup work into critical vs deferred.
2. Keep only first-frame-critical setup before first draw.
3. Defer non-critical handler setup and widget/auxiliary initialization until after first frame.

### P0 - HomeViewModel startup IO deferral

Target files:

1. `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt`
2. `app/src/main/java/com/milki/launcher/presentation/home/HomeIconWarmupCoordinator.kt`
3. `app/src/main/java/com/milki/launcher/presentation/home/prune/HomeAvailabilityPruner.kt`

Patch intent:

1. Delay icon warmup and availability pruning start from immediate init.
2. Trigger after first frame or short delay window.
3. Keep cancellation and correctness behavior unchanged.

### P1 - Drawer to home jank reduction

Target files:

1. `app/src/main/java/com/milki/launcher/ui/components/launcher/AppDrawerOverlay.kt`
2. `app/src/main/java/com/milki/launcher/presentation/drawer/DrawerSurfaceController.kt`
3. `app/src/main/java/com/milki/launcher/presentation/launcher/SurfaceStateCoordinator.kt`

Patch intent:

1. Reduce heavy work on transition close/open boundaries.
2. Ensure search/query reset and state transitions avoid extra recomposition churn during close.

## Validation Checklist After Each Patch

Run all 3 benchmarks with the same workflow in this document.

Track at minimum:

1. `timeToInitialDisplayMs` median/min/max for both cold-start methods.
2. `frameDurationCpuMs` P90/P95/P99.
3. `frameOverrunMs` P90/P95/P99.

## Merge Gates (Suggested)

Treat patch as successful only if all are true:

1. Startup median improves relative to current seeded baseline for the target mode.
2. No regression > 5% in the other startup mode.
3. Drawer->home `frameOverrunMs` P95 and P99 do not regress.
4. No functional regressions in launcher behavior.

## Next Device Needed For Full Baseline Profile Validation

To complete profile generation/import validation, rerun on API 33+ or rooted device with:

```bash
./gradlew :app:importBaselineProfileFromConnectedTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.milki.launcher.benchmark.LauncherBaselineProfile
```

Then rerun the same 3 benchmark methods and compare deltas against this document.
