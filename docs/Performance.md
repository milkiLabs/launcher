# Performance Benchmarking And Profiling

This project uses `:baselineprofile` for repeatable launcher startup and homescreen smoothness measurements on real devices.

## Design Goals

1. Keep benchmark scenarios deterministic and easy to extend.
2. Make startup measurements representative of real usage (non-empty homescreen).
3. Separate environment-specific troubleshooting from benchmark logic.
4. Keep baseline profile generation and macrobenchmark execution as independent workflows.

## Benchmark Architecture

### App-side benchmark commands

The launcher host responds to benchmark-only intent actions:

1. `com.milki.launcher.action.BENCHMARK_PREPARE_HOME`
2. `com.milki.launcher.action.BENCHMARK_OPEN_HOME`
3. `com.milki.launcher.action.BENCHMARK_OPEN_DRAWER`

Responsibilities:

1. `BENCHMARK_PREPARE_HOME` resets transient UI and seeds a deterministic homescreen (16 pinned apps).
2. `BENCHMARK_OPEN_HOME` enters homescreen state from a clean transient state.
3. `BENCHMARK_OPEN_DRAWER` enters drawer state from a clean transient state.

Key files:

1. `app/src/main/java/com/milki/launcher/core/intent/LauncherActivityIntent.kt`
2. `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherHostRuntime.kt`
3. `app/src/main/java/com/milki/launcher/presentation/launcher/host/LauncherBenchmarkHomeSeeder.kt`

### Benchmark-side scenarios

`LauncherHomeBenchmark` contains 3 core scenarios:

1. `coldStartupToHomescreen`
2. `coldStartupToHomescreenWithoutBaselineProfile`
3. `returnToHomescreenFromDrawer`

Shared setup/navigation helpers live in:

1. `baselineprofile/src/main/java/com/milki/launcher/benchmark/LauncherBenchmarkScenario.kt`

## Device Matrix

1. Macrobenchmark execution: API 28+
2. Baseline profile collection: API 33+ or rooted API 28+ with `adb root`

If you are on API 32 or lower without root, macrobenchmarks still work, but baseline profile collection will fail by design.

## Quick Start

### A. Build artifacts

```bash
./gradlew :app:assembleBenchmark :baselineprofile:assembleBenchmark
```

### B. Install APKs directly (most reliable on restrictive ROMs)

```bash
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb install -r baselineprofile/build/outputs/apk/benchmark/baselineprofile-benchmark.apk
```

### C. Run core scenarios (recommended)

```bash
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToHomescreenWithoutBaselineProfile' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#returnToHomescreenFromDrawer' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
```

### D. Optional: run through Gradle instrumentation task

```bash
./gradlew :baselineprofile:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.milki.launcher.benchmark.LauncherHomeBenchmark
```

## Baseline Profile Workflow

### Generate and import profile

```bash
./gradlew :app:importBaselineProfileFromConnectedTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.milki.launcher.benchmark.LauncherBaselineProfile
```

This runs profile instrumentation and copies newest `baseline-prof.txt` into `app/src/main/baseline-prof.txt`.

Then install release-like app artifact:

```bash
./gradlew :app:installBenchmark
```

## Profiling Workflow

For manual traces:

1. Install benchmark app (`:app:installBenchmark`)
2. Open Android Studio profiler on process `com.milki.launcher`
3. Use `System Trace` first for startup and jank; use CPU/memory only after hotspot isolation

Manual deterministic launches:

```bash
adb shell am force-stop com.milki.launcher
adb shell am start -W -n com.milki.launcher/.app.activity.MainActivity -a com.milki.launcher.action.BENCHMARK_PREPARE_HOME
adb shell am start -W -n com.milki.launcher/.app.activity.MainActivity -a com.milki.launcher.action.BENCHMARK_OPEN_HOME
```

## Troubleshooting

### INSTALL_FAILED_USER_RESTRICTED

Symptom:

1. Gradle/UTP test install fails with user-restricted install cancellation.

Resolution:

1. Use manual `adb install -r` commands.
2. Keep device unlocked and confirm install prompt.
3. Rerun instrumentation command.

### Method selector not recognized

Symptom:

1. Shell expands `#` and instrumentation fails to parse class filter.

Resolution:

1. Quote class selector string: `'com.example.Class#method'`.

### Baseline profile collection throws API/root error

Symptom:

1. `BaselineProfileRule.collect` fails immediately.

Resolution:

1. Use API 33+ device or rooted API 28+ device.
2. Keep running macrobenchmarks on current phone if baseline collection is blocked.

## Maintenance Guide

### Add a new benchmark scenario

1. Add/setup helpers in `LauncherBenchmarkScenario.kt` if behavior is reusable.
2. Add the scenario in `LauncherHomeBenchmark.kt` with explicit setup and metric list.
3. Keep setup deterministic and use benchmark-only intents where possible.
4. Update this document with run commands and expected outputs.

### Add a new benchmark command

1. Add action constant and action mapping in `LauncherActivityIntent.kt`.
2. Add intent factory in `LauncherBenchmarkTarget.kt`.
3. Handle action in `LauncherHostRuntime.handleBenchmarkIntent`.
4. Keep command side effects isolated and idempotent.

### Data quality rules

1. Compare medians, not single best iterations.
2. Always run both startup variants when changing startup code.
3. Track P95/P99 for frame metrics when changing drawer/home transitions.
4. Capture device model, API level, and build variant in every report.
