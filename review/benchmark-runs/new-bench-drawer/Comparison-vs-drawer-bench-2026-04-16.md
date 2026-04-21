# Drawer Benchmark Comparison Report (2026-04-16)

Compared new logs in `review/benchmark-runs/new-bench-drawer` against corresponding baseline logs in `review/drawer-bench`.

## Files compared

1. `coldStartupToDrawer-new.log` vs `coldStartupToDrawer.log`
2. `scrollDrawerFromHomescreen-new.log` vs `scrollDrawerFromHomescreen.log`

## Metrics comparison

### 1) coldStartupToDrawer

| Metric | Baseline | New | Delta (new - baseline) |
|---|---:|---:|---:|
| timeToInitialDisplayMs min | 675.0 | 714.2 | +39.2 |
| timeToInitialDisplayMs median | 693.6 | 749.9 | +56.3 |
| launcher.appsCatalog.resolveLabelSumMs min | 642.6 | 0.0 | -642.6 |
| launcher.appsCatalog.resolveLabelSumMs median | 673.2 | 0.0 | -673.2 |
| launcher.appsCatalog.resolveLabelCount min | 26.0 | 0.0 | -26.0 |
| launcher.appsCatalog.resolveLabelCount median | 28.0 | 0.0 | -28.0 |

### 2) scrollDrawerFromHomescreen

| Metric | Baseline | New | Delta (new - baseline) |
|---|---:|---:|---:|
| frameCount min | 7.0 | 12.0 | +5.0 |
| frameCount median | 47.0 | 53.0 | +6.0 |
| frameDurationCpuMs P95 | 144.2 | 118.1 | -26.1 |
| frameDurationCpuMs P99 | 219.3 | 190.4 | -28.9 |
| frameOverrunMs P95 | 240.0 | 207.8 | -32.2 |
| frameOverrunMs P99 | 333.3 | 297.4 | -35.9 |