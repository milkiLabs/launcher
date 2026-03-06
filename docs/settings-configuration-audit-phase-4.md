# Settings Configuration Audit - Phase 4 Implementation Notes

Date: 2026-03-06
Scope: Search-source persistence fallback semantics (`P1-4` continuation).

## Goal Of This Phase

Fix persistence ambiguity where empty/missing/invalid source storage could silently restore defaults and make user intent indistinguishable from fallback behavior.

## Implemented Changes

### 1) Added explicit persisted state marker for search sources

File:
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`

What changed:
1. Added `Keys.SEARCH_SOURCES_STATE` preference key.
2. Added state value constant: `SearchSourcesState.INITIALIZED`.

Why:
- Enables parser to distinguish first-run behavior from initialized runtime behavior.

### 2) Stopped compacting empty source list by removing the key

File:
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`

What changed:
- `writeSearchSources(...)` now always writes:
  1. state marker = initialized
  2. serialized source list JSON (including empty list `[]`)

Before:
- Empty list removed `SEARCH_SOURCES` key entirely.

After:
- Empty list is an explicit persisted state, not key absence.

Why:
- Allows intentional "zero custom sources" to be represented without being auto-converted to defaults.

### 3) Split parsing logic into first-run vs initialized semantics

File:
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`

What changed:
1. Added `parseSearchSources(preferences)` wrapper using both JSON and state marker.
2. Added `parseSearchSources(json, isInitialized)` decision logic.

Behavior now:
1. `state missing` + `json missing/blank` -> first-run defaults (`SearchSource.defaultSources()`).
2. `state missing` + `json present` -> parse legacy persisted data.
3. `state initialized` + `json missing/blank` -> explicit empty list.
4. `state initialized` + `json invalid` -> recover to empty list (avoid silent default restore).

Why:
- Separates first-run seeding behavior from runtime fallback.
- Avoids silently treating corruption/missing runtime data as user-default intent.

### 4) Removed forced default restoration for empty decoded list

File:
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`

What changed:
- `normalizeAndValidateSearchSources(...)` no longer auto-replaces empty list with defaults.

Why:
- Preserves explicit empty-list intent when list is intentionally empty.

## Compatibility Notes

1. Legacy users without state marker but with stored `search_sources` JSON continue to load their persisted list.
2. Legacy users with no key and no marker still see default starter sources on first run.
3. After first write through repository, state marker is persisted and runtime semantics become explicit.

## Impact Summary

1. Intentional empty source list is now representable.
2. First-run defaults are no longer conflated with initialized runtime fallback paths.
3. Source CRUD and prefix operations continue using targeted DataStore edit transactions introduced in phases 2 and 3.

## Deferred Work After This Phase

1. Add explicit corruption telemetry / one-time user recovery notice path.
2. Add focused tests for first-run vs initialized parse matrix and empty-list semantics.
3. Continue settings contract decomposition (`P2-2`).

## Explicit Non-Goal

No Proto DataStore migration work was performed in this phase.
