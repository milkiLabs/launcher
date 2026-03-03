# AppIconFetcher Documentation (Historical)

## Status

This document is kept only for historical context.

`AppIconFetcher.kt` and `AppIconRequest.kt` were removed from the codebase.
The launcher no longer uses Coil for app icon rendering.

---

## Why It Was Removed

Launcher app icons are local system resources and are displayed at very high frequency.
A direct cache-first PackageManager pipeline provides faster first paint and lower per-item overhead for this use case.

---

## Current Implementation

Use this document instead:

- [app-icon-memory-cache.md](app-icon-memory-cache.md)

That file explains the current architecture:
- `AppIconMemoryCache` (LRU cache)
- preload in `AppRepositoryImpl`
- cache-first rendering in `AppIcon` composable

---

## Migration Summary

Old flow:
- `AppIcon` -> Coil request -> custom fetcher -> PackageManager

New flow:
- App discovery preloads icons into `AppIconMemoryCache`
- `AppIcon` reads cache synchronously first
- cache miss loads on background thread and stores for reuse
