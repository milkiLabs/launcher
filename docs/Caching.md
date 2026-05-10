# Caching

Launcher caches should keep expensive Android framework calls off hot UI paths
without hiding invalidation rules in individual screens.

## Shared Primitives

- `SnapshotCache<T>` stores immutable, synchronously readable snapshots.
- `AsyncSnapshotCache<T>` stores expensive async snapshots with one active load,
  cache-first reads, cancellation on invalidation, and stale-load protection.

Use these before adding new manual `@Volatile`, `Deferred`, or version fields.

## Current Caches

- App labels: `AppLabelCache`, persisted for cold starts and refreshed by full
  app catalog reloads.
- App list: `AppRepositoryImpl` owns the installed-app snapshot used by drawer
  and search. One-shot `getInstalledApps()` calls reuse this snapshot once it
  is available.
- Recent apps: `AppRepositoryImpl` resolves recent component names from the
  installed-app snapshot before falling back to `PackageManager`.
- Context-menu data: `AppContextDataCache` stores shortcuts and widget
  availability as one atomic snapshot.
- Widget picker catalog: `WidgetPickerCatalogStore` uses `AsyncSnapshotCache`.
- App and shortcut icons: memory caches serve UI synchronously and invalidate
  by package on package broadcasts.
- URL handlers: `UrlHandlerResolver` caches browser package discovery and
  resolved handler labels, then invalidates by package on package broadcasts.

## Invalidation

`PackageChangeMonitor` is the package-change source of truth. Package-change
events include the changed package when Android provides it.

On package changes:

- app list reloads from `InstalledAppsCatalog`
- recents prune unavailable apps
- context-menu data refreshes as a full snapshot
- widget picker catalog invalidates and prewarms
- app and shortcut memory icons invalidate for the changed package

If a future cache depends on installed packages, wire it to this package-change
path instead of observing package broadcasts independently.
