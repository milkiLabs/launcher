# DataStore Hot-Path Optimizations

This document explains optimizations added to reduce repeated
serialization/deserialization work in frequently triggered repository paths.

## Why This Was Needed

Two patterns caused avoidable overhead:

1. **Settings edits** often used a generic full-model transform path
   (`LauncherSettings -> copy(...) -> write all keys`) even when only one
   setting key changed.
2. **Home drag/pin actions** in ViewModel performed pre-read checks before
   calling repository mutation methods that already performed the same checks.

Result: extra CPU work from duplicate model mapping and serialization cycles.

---

## Settings Repository Optimizations

File: `data/repository/SettingsRepositoryImpl.kt`

### 1) Diff-based write path for generic updates

`updateSettings(transform)` now:

1. Reads current settings model once
2. Applies transform
3. Writes only keys whose values actually changed

This keeps existing API behavior but avoids full key rewrites for small edits.

### 2) Targeted update helpers for prefix configuration

New `SettingsRepository` APIs allow focused writes that touch only
`PREFIX_CONFIGURATIONS`:

- `setProviderPrefixes(...)`
- `addProviderPrefix(...)`
- `removeProviderPrefix(...)`
- `resetProviderPrefixes(...)`
- `resetAllPrefixConfigurations()`
- `setAllPrefixConfigurations(...)`

These methods avoid full `LauncherSettings` remap/write cycles during prefix edits.

### 3) Targeted update helper for hidden apps set

New API:

- `toggleHiddenApp(packageName)`

This updates only `HIDDEN_APPS` instead of rewriting unrelated keys.

### 4) Targeted single-key updates for common settings fields

The repository now exposes targeted write methods for all common primitive/enum
settings (search behavior, appearance, home actions, and provider toggles).

Examples:

- `setMaxSearchResults(...)`
- `setSearchResultLayout(...)`
- `setHomeTapAction(...)`
- `setDefaultSearchEngine(...)`
- `setWebSearchEnabled(...)`

Each method writes exactly one key (or one logically grouped key) in DataStore.
This removes unnecessary full-settings remap/write cycles during routine toggle
and selector interactions in the Settings UI.

---

## Settings ViewModel Routing Changes

File: `presentation/settings/SettingsViewModel.kt`

Hot-path actions now call targeted repository helpers directly:

- Prefix add/remove/reset/set operations
- Hidden app toggle
- Common setting toggles, selectors, and numeric fields

Generic `updateSettings(transform)` remains available for reset-to-default and
future complex multi-field updates, while routine interactions use targeted paths.

---

## Home ViewModel Optimizations

File: `presentation/home/HomeViewModel.kt`

Removed redundant pre-read checks before repository mutations:

- `moveItemToPosition(...)`
- `pinApp(...)`
- `pinFile(...)`
- `pinContact(...)`

Before optimization, these methods collected `pinnedItems.first()` and then called
repository methods that read and validated state again.

After optimization, ViewModel delegates directly to repository methods that already
enforce:

- duplicate prevention
- occupancy checks
- no-op short-circuit for unchanged position

This removes one full read/deserialize pass per user action in these paths.

---

## Behavioral Safety

The optimizations were designed to keep behavior unchanged:

- Prefix JSON storage shape remains compatible
- Repository validation rules stay authoritative
- UI-visible success/failure semantics remain the same

Only internal update/write efficiency is improved.
