# Drawer Benchmark Baseline (2026-04-15)

Device/context:
- Device id: `f581b61d`
- Android SDK: `31`
- Build/install path: benchmark APKs from current workspace state

Scenarios run:
1. `openDrawerFromHomescreen`
2. `returnToHomescreenFromDrawer`
3. `filterDrawerFromHomescreen`
4. `coldStartupToDrawer`
5. `coldStartupToDrawerWithoutBaselineProfile`
6. `scrollDrawerFromHomescreen`

All six scenarios finished with `OK (1 test)` on their successful run logs.

## Key metrics

### Frame timing scenarios

| Scenario | frameCount median | frameDurationCpuMs P95 | frameDurationCpuMs P99 | frameOverrunMs P95 | frameOverrunMs P99 |
|---|---:|---:|---:|---:|---:|
| openDrawerFromHomescreen | 22.0 | 25.54 | 73.33 | 48.00 | 61.43 |
| returnToHomescreenFromDrawer | 24.0 | 8.52 | 18.52 | -5.17 | 4.86 |
| filterDrawerFromHomescreen | 22.0 | 25.95 | 53.60 | 23.34 | 41.94 |
| scrollDrawerFromHomescreen | 47.0 | 144.16 | 219.34 | 239.97 | 333.33 |

### Cold startup scenarios

| Scenario | timeToInitialDisplay median (ms) | timeToInitialDisplay min (ms) | resolveLabelSum median (ms) |
|---|---:|---:|---:|
| coldStartupToDrawer | 693.64 | 674.96 | 673.23 |
| coldStartupToDrawerWithoutBaselineProfile | 584.12 | 570.52 | 610.94 |

## First-pass interpretation

- `openDrawerFromHomescreen` is the primary drawer latency hotspot.
- `filterDrawerFromHomescreen` is also a hotspot, but with materially better tail than drawer-open (`P99 overrun 41.94 ms` vs `61.43 ms`).
- `returnToHomescreenFromDrawer` is comparatively healthy and should be treated as the control path.
- `scrollDrawerFromHomescreen` now runs reliably and reveals the worst tail behavior in this suite (`frameDurationCpuMs P99 219.34 ms`, `frameOverrunMs P99 333.33 ms`).
- Cold startup-to-drawer is dominated by app catalog/label resolution work and remains expensive.
- Startup comparison between baseline-profile and without-baseline runs is likely confounded by runtime-image clearing limits reported by benchmark output; treat this pair as directional, not definitive.
