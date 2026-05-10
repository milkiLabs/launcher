# Benchmark + Profiling Comparison

Run date: 2026-04-15
Device: Redmi Note 9S (Android 12 / API 31, non-root)
Comparison baseline: Performance Run - 2026-04-14 (Agent Handoff)

## Raw metrics (new run)

### Cold startup to homescreen (baseline-enabled path)
- timeToInitialDisplayMs min: 616.8 ms
- timeToInitialDisplayMs median: 670.0 ms
- timeToInitialDisplayMs max: 727.7 ms

Source: review/benchmark-runs/2026-04-15/coldStartupToHomescreen.log

### Cold startup to homescreen (without baseline profile)
- timeToInitialDisplayMs min: 520.9 ms
- timeToInitialDisplayMs median: 568.2 ms
- timeToInitialDisplayMs max: 652.9 ms

Source: review/benchmark-runs/2026-04-15/coldStartupToHomescreenWithoutBaselineProfile.log

### Drawer -> Home frame timing
- frameDurationCpuMs: P50 22.1, P90 58.8, P95 65.2, P99 73.1
- frameOverrunMs: P50 25.6, P90 49.9, P95 58.0, P99 62.3

Source: review/benchmark-runs/2026-04-15/returnToHomescreenFromDrawer.log

## Delta vs 2026-04-14 baseline

### Cold startup (baseline-enabled path)
- min: 616.8 vs 505.2 = +111.6 ms (+22.1%)
- median: 670.0 vs 524.3 = +145.7 ms (+27.8%)
- max: 727.7 vs 564.8 = +162.9 ms (+28.8%)

### Cold startup (without baseline profile)
- min: 520.9 vs 409.6 = +111.3 ms (+27.2%)
- median: 568.2 vs 446.8 = +121.4 ms (+27.2%)
- max: 652.9 vs 555.0 = +97.9 ms (+17.6%)

### Drawer -> Home frame timing
frameDurationCpuMs:
- P50: 22.1 vs 19.5 = +2.6 ms (+13.3%)
- P90: 58.8 vs 49.4 = +9.4 ms (+19.0%)
- P95: 65.2 vs 60.8 = +4.4 ms (+7.2%)
- P99: 73.1 vs 89.9 = -16.8 ms (-18.7%)

frameOverrunMs:
- P50: 25.6 vs 23.5 = +2.1 ms (+8.9%)
- P90: 49.9 vs 55.0 = -5.1 ms (-9.3%)
- P95: 58.0 vs 67.1 = -9.1 ms (-13.6%)
- P99: 62.3 vs 80.7 = -18.4 ms (-22.8%)

## Profiling status

Baseline profile collection/import remains blocked on this device.

Observed failure:
- java.lang.IllegalArgumentException: Baseline Profile collection requires API 33+, or a rooted device running API 28 or higher and rooted adb session (via adb root).

Source: review/benchmark-runs/2026-04-15/importBaselineProfileFromConnectedTest.log

## Merge gate check against handoff policy

- Startup median improves for target mode: FAIL
- No regression > 5% in the other startup mode: FAIL
- Drawer->home frameOverrunMs P95/P99 do not regress: PASS (both improved)
- Functional regression status: NOT EVALUATED in this benchmark-only run

## Summary

Compared with 2026-04-14 seeded baseline:
- Startup regressed materially in both cold-start methods (~27% median slower).
- Drawer->home jank tail improved for frame overrun metrics (P90/P95/P99 better).
- Baseline profile collection/import remains unavailable on API 31 non-root, consistent with prior constraints.
