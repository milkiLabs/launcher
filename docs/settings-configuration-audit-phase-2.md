# Settings Configuration Audit - Phase 2 Implementation Notes

Date: 2026-03-06
Scope: Repository-level transactional validation for custom source prefix mutations (`P1-3` continuation).

## Goal Of This Phase

Move correctness-critical source prefix validation into repository atomic write paths so `SettingsViewModel` no longer relies on potentially stale `settings` snapshots for uniqueness guarantees.

This phase specifically targets:
1. Source prefix add operation.
2. Source prefix remove operation.
3. Structured mutation outcomes for deterministic UI behavior.

## Implemented Changes

### 1) Added structured mutation result model

File:
- `app/src/main/java/com/milki/launcher/domain/model/SourcePrefixMutationResult.kt`

What was added:
- A sealed result contract for prefix mutation operations:
  - `Success`
  - `InvalidPrefixEmpty`
  - `InvalidPrefixContainsSpaces`
  - `SourceNotFound`
  - `DuplicatePrefixOnAnotherSource(ownerSourceId)`
  - `PrefixAlreadyExistsOnTargetSource`
  - `PrefixNotFoundOnTargetSource`

Why this matters:
- Replaces ambiguous/silent outcomes with explicit result states.
- Gives ViewModel/UI deterministic feedback mapping.

### 2) Extended SettingsRepository API with transactional source-prefix methods

File:
- `app/src/main/java/com/milki/launcher/domain/repository/SettingsRepository.kt`

What was added:
- `addPrefixToSource(sourceId, prefix): SourcePrefixMutationResult`
- `removePrefixFromSource(sourceId, prefix): SourcePrefixMutationResult`

Why this matters:
- Validations now belong to the persistence source of truth.
- API shape makes race-safe behavior explicit in contract.

### 3) Implemented repository-level atomic validation and mutation

File:
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt`

What changed:
- Implemented `addPrefixToSource(...)` inside one `DataStore.edit` transaction.
- Implemented `removePrefixFromSource(...)` inside one `DataStore.edit` transaction.

Validation behavior now enforced in repository transaction:
1. Normalize prefix (`SearchSource.normalizePrefix`).
2. Reject empty prefix.
3. Reject spacing for add operation.
4. Verify target source exists.
5. For add:
   - Return success no-op when prefix already exists on target source.
   - Reject duplicates owned by any other source.
6. Persist updated source list only on successful mutation.

Why this matters:
- Eliminates snapshot race window where UI validation could pass but persistence normalization could silently drop input.
- Ensures uniqueness decision is made against exact persisted snapshot.

### 4) Refactored SettingsViewModel to consume repository mutation results

File:
- `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt`

What changed:
- Removed snapshot-based duplicate-prefix check from `addPrefixToSource`.
- Kept immediate local checks for empty/spaces to preserve responsive dialog UX.
- Delegated uniqueness/source existence decisions to repository result.
- Added message mapping from `SourcePrefixMutationResult` to existing UI callback contract.
- Updated `removePrefixFromSource` to call repository transactional method.

Why this matters:
- ViewModel no longer acts as authority for cross-source uniqueness.
- User feedback is still immediate and deterministic.

## Current Behavior Summary

For `addPrefixToSource`:
1. UI path quickly rejects empty/space input.
2. Repository transaction validates source existence and global uniqueness.
3. Result maps to user-facing message:
   - success -> empty message (existing UI convention)
   - duplicate -> duplicate message
   - missing source -> source not found message

For `removePrefixFromSource`:
1. Repository transaction validates source existence and prefix presence.
2. Remove is applied atomically when valid.

## Deferred Items After This Phase

Still pending from audit roadmap:
1. Full targeted source CRUD repository APIs for add/update/delete/toggle source (`P2-1` broader scope).
2. Persistence fallback semantics overhaul for empty-source intent vs corruption (`P1-4`).
3. Collision conflict surfacing back into settings UI for local-vs-source provider prefix matrix (`P1-2` UI-level follow-through).
4. Additional tests around mutation result matrix and concurrent edit scenarios.

## Explicit Non-Goal

No Proto DataStore migration work was performed in this phase.
