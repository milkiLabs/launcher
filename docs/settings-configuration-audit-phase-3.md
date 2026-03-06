# Settings Configuration Audit - Phase 3 Implementation Notes

Date: 2026-03-06
Scope: Targeted repository APIs for dynamic source CRUD hot paths (`P2-1` continuation).

## Goal Of This Phase

Reduce use of broad `updateSettings` transforms for high-frequency dynamic-source edits by introducing focused repository methods that update only the `SEARCH_SOURCES` key.

## Implemented Changes

### 1) Extended SettingsRepository with targeted source CRUD methods

File:
- `app/src/main/java/com/milki/launcher/domain/repository/SettingsRepository.kt`

Added methods:
1. `addSearchSource(source)`
2. `updateSearchSource(sourceId, name, urlTemplate, prefixes, accentColorHex)`
3. `deleteSearchSource(sourceId)`
4. `setSearchSourceEnabled(sourceId, enabled)`

Why:
- Makes source operations explicit in repository contract.
- Keeps `updateSettings` available as fallback, but not default for source-edit hot paths.

### 2) Implemented transactional targeted writes in SettingsRepositoryImpl

File:
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`

Behavior:
1. All four operations run in `DataStore.edit` transactions.
2. Each operation reads current persisted sources from `SEARCH_SOURCES` key.
3. Each operation mutates only source list state and writes back using `writeSearchSources(...)`.
4. `updateSearchSource(...)` preserves existing `isEnabled` state by updating only editable fields.
5. Unknown source IDs are treated as no-op (safe idempotent behavior).

Why:
- Avoids full settings remap and full-key diff path for source edits.
- Keeps mutation semantics local and easier to test.

### 3) Migrated SettingsViewModel to new targeted APIs

File:
- `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt`

Updated methods:
1. `addSearchSource(...)` now builds normalized `SearchSource` once and calls `settingsRepository.addSearchSource(...)`.
2. `updateSearchSource(...)` now calls targeted repository update API instead of full settings transform.
3. `deleteSearchSource(...)` now calls targeted repository delete API.
4. `setSearchSourceEnabled(...)` now calls targeted repository enabled-toggle API.

Why:
- Removes broad source-list mutations from ViewModel generic `updateSettings` path.
- Keeps ViewModel orchestration-focused while repository owns persistence mutation details.

## Relationship To Previous Phases

1. Phase 1 reduced search settings fanout and stale pipeline wiring.
2. Phase 2 moved source-prefix uniqueness checks into repository transactions.
3. Phase 3 now migrates broader source CRUD hot-path writes into targeted repository methods.

Together, phases 2+3 significantly reduce race windows and maintenance overhead in settings source management.

## Deferred Work After This Phase

1. Persistence fallback semantics for intentional empty source list vs corruption fallback (`P1-4`).
2. Settings action-contract/UI decomposition (`P2-2`).
3. Additional mutation/result test matrix coverage for source CRUD + prefix operations.

## Explicit Non-Goal

No Proto DataStore migration work was performed in this phase.
