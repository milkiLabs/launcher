# Settings Configuration Audit - Phase 1 Implementation Notes

Date: 2026-03-06
Scope: First prioritized implementation pass for `review/06-settings-configuration-audit.md`.

## Goal Of This Phase

This phase intentionally targets high-impact, lower-risk items that improve runtime behavior and maintenance clarity without introducing large persistence schema changes.

Implemented in this phase:
1. Narrowed settings observation scope in the search settings adapter (audit item `P1-1`).
2. Added explicit and deterministic prefix collision policy in search provider registry (audit item `P1-2`, partial domain-level enforcement).
3. Switched settings UI collection to lifecycle-aware API (audit item `P2-3`).
4. Removed stale/unused search pipeline parameter wiring (audit item `P2-4`).

Deferred in this phase:
1. Repository-level transactional validation and mutation result contracts (`P1-3`).
2. Persistence fallback semantics and explicit empty-state schema changes (`P1-4`).
3. Broad source CRUD repository API refactor (`P2-1`).
4. Settings screen/action-contract decomposition (`P2-2`).
5. Proto DataStore migration recommendation (explicitly excluded by request).

## Why This Order Was Chosen

The selected items provide immediate value while minimizing risk:
1. `P1-1` and `P2-4` reduce unnecessary search-system churn and remove dead wiring.
2. `P1-2` removes implicit behavior and replaces it with explicit policy in core domain logic.
3. `P2-3` aligns lifecycle collection with existing project usage and improves consistency.

These changes are safe to ship independently and make later repository/persistence work easier.

## Detailed Changes

### 1) Search settings adapter now observes a narrow runtime projection (`P1-1`)

File:
- `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt`

Before:
- Adapter collected `settingsRepository.settings` and used `distinctUntilChanged()` on full `LauncherSettings`.
- Any unrelated settings update could trigger dynamic provider register/unregister and prefix map rebuild logic.

After:
- Adapter maps full settings to a dedicated projection (`SearchRuntimeSettingsProjection`) that includes only:
  - `searchSources`
  - `contactsSearchEnabled`
  - `filesSearchEnabled`
  - `prefixConfigurations`
- `distinctUntilChanged()` now applies to this projection only.

Impact:
- Search runtime reconfiguration is now isolated from unrelated settings edits.
- Reduces registry churn and makes change impact easier to reason about.

### 2) Prefix collision behavior is now explicit and deterministic (`P1-2`)

File:
- `app/src/main/java/com/milki/launcher/domain/search/SearchProviderRegistry.kt`

Before:
- Collision behavior depended on map iteration/write order and used overwrite semantics.
- Effective behavior was implicit (`last-write-wins`) and could be surprising.

After:
- Rebuild logic now sorts providers with explicit priority:
  1. Built-in local providers (`ProviderId.all`, currently contacts/files)
  2. Dynamic `source_` providers
  3. Any remaining provider IDs
- Prefix mapping now uses `putIfAbsent`, making first owner win based on that ordered policy.

Impact:
- Local providers consistently keep ownership of shared prefixes.
- Collision behavior is now deliberate and documented in code.

Note:
- This phase improves deterministic behavior in domain logic.
- UI-facing validation/error surfacing for collisions is deferred to a later phase.

### 3) Settings activity now collects settings with lifecycle awareness (`P2-3`)

File:
- `app/src/main/java/com/milki/launcher/SettingsActivity.kt`

Before:
- Used `collectAsState()`.

After:
- Uses `collectAsStateWithLifecycle()`.

Impact:
- Aligns with lifecycle-aware collection approach used elsewhere (e.g. `MainActivity`).
- Reduces lifecycle mismatch risk.

### 4) Removed stale pipeline parameters and state (`P2-4`)

Files:
- `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelPipelineCoordinator.kt`
- `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt`
- `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelStateHolder.kt`
- `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt`

Before:
- `searchSources` was threaded through the pipeline input and `executeSearch` signature but not used.
- `SearchViewModelStateHolder` kept a dedicated `searchSources` flow only for this stale wiring.

After:
- Removed `searchSources` from pipeline bind signature, input snapshot, and execution signature.
- Removed state-holder `searchSources` flow.
- Removed adapter output write to removed flow.

Impact:
- Simplifies orchestration contracts.
- Makes search pipeline dependencies explicit and current.

## Known Follow-up Work (Not In This Phase)

1. Add repository-level atomic validation for source prefix mutations and return structured mutation results (`Success`, `DuplicatePrefix`, etc.).
2. Differentiate first-run seeding, corruption fallback, and intentional empty-source persistence state.
3. Introduce focused source CRUD repository APIs to reduce broad `updateSettings` usage in hot paths.
4. Add test coverage for:
   - collision policy matrix
   - adapter diffing behavior under unrelated settings updates
   - persistence fallback and empty-list semantics

## Explicit Non-Goal

No Proto DataStore migration work was performed in this phase.
