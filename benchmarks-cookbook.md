## Benchmark Runbook (Steps + Commands)

Use this procedure to run benchmarks and collect results consistently.

### 1. Connect and verify device

```bash
adb devices
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

### 2. Build benchmark APKs

From project root:

```bash
./gradlew :app:assembleBenchmark :baselineprofile:assembleBenchmark
```

### 3. Install APKs

```bash
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb install -r baselineprofile/build/outputs/apk/benchmark/baselineprofile-benchmark.apk
```

If install/reset gets blocked by device policy, retry app install directly:

```bash
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
```

### 4. Create output folder for this run

```bash
mkdir -p review/benchmark-runs/$(date +%F)
```

### 5. Run cold-start benchmark (with baseline profile)

```bash
adb shell am instrument -w \
	-e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToHomescreen' \
	com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner \
	| tee review/benchmark-runs/$(date +%F)/coldStartupToHomescreen.log
```

### 6. Run cold-start benchmark (without baseline profile)

```bash
adb shell am instrument -w \
	-e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToHomescreenWithoutBaselineProfile' \
	com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner \
	| tee review/benchmark-runs/$(date +%F)/coldStartupToHomescreenWithoutBaselineProfile.log
```

### 7. Optional: run warm/hot startup and drawer scenarios

```bash
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#warmStartupToHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#hotStartupToHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToDrawer' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#coldStartupToDrawerWithoutBaselineProfile' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#openDrawerFromHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#returnToHomescreenFromDrawer' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#filterDrawerFromHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class 'com.milki.launcher.benchmark.LauncherHomeBenchmark#scrollDrawerFromHomescreen' com.milki.launcher.baselineprofile/androidx.test.runner.AndroidJUnitRunner
```

### 8. Extract key result metrics from logs

Median startup metric:

```bash
rg 'timeToInitialDisplayMs\s+\[min|timeToInitialDisplayMs.*median|time_to_initial_display_ms_median' review/benchmark-runs/$(date +%F)/*.log
```

Trace-section medians (startup breakdown):

```bash
rg 'launcher\.apps_catalog\.|launcher\.startup\.|queryLauncherActivities|resolveLabel|preloadIcon|onCreate|setContent|initialize' review/benchmark-runs/$(date +%F)/*.log
```

Quick pass/fail check:

```bash
rg 'OK \(1 test\)|FAILURES!!!|AssertionError|INSTALL_FAILED' review/benchmark-runs/$(date +%F)/*.log
```

### 9. Compare with previous run

```bash
PREV=review/benchmark-runs/2026-04-15/coldStartupToHomescreen.log
CURR=review/benchmark-runs/$(date +%F)/coldStartupToHomescreen.log

echo 'Previous:'
rg 'timeToInitialDisplayMs.*median|time_to_initial_display_ms_median' "$PREV"

echo 'Current:'
rg 'timeToInitialDisplayMs.*median|time_to_initial_display_ms_median' "$CURR"
```

### 10. Where traces are stored

Perfetto traces are reported in instrumentation output as `additionalTestOutputFile_*` and typically stored under:

```text
/storage/emulated/0/Android/media/com.milki.launcher.baselineprofile/
```

Pull traces if needed:

```bash
adb pull /storage/emulated/0/Android/media/com.milki.launcher.baselineprofile review/benchmark-runs/$(date +%F)/traces
```

### Notes for reliable runs

- Keep device connected over USB and screen unlocked.
- Avoid interacting with device during each 10-iteration benchmark.
- Run each scenario at least once after code changes that affect startup path.
- If device policy blocks uninstall/reinstall, install app APK manually and rerun instrumentation.
