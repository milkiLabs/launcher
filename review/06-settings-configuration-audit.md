# Settings and Configuration Audit

Date: 2026-03-06
Scope: Settings domain model, DataStore persistence strategy, settings UI/editor flow, and runtime config application into search/provider registry.

## Executive Summary

The settings system is generally well-structured for a single-module app: immutable `LauncherSettings`, DataStore-backed persistence, and explicit repository interfaces are good foundations. However, there are several maintainability/scalability risks that will become expensive as configuration grows:

1. Settings-change fanout is broader than needed and can trigger unnecessary registry churn.
2. Search-source and prefix validation is split across UI/ViewModel/repository with inconsistent behavior under races.
3. Persistence fallbacks silently restore defaults in cases where users might expect strict or recoverable behavior.
4. Some APIs and contracts are now partially redundant or too broad for targeted hot paths.

## Findings (Ordered by Severity)

### P1-1) Settings adapter reacts to all settings changes, not only config-relevant changes
- Evidence: `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:38`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:39`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:55`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:56`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:77`
- Problem: Adapter collects full `settings` (`distinctUntilChanged` on entire data class). Any unrelated setting update can still trigger provider register/unregister and prefix map rebuild flow.
- Impact: Unnecessary work and state churn, harder reasoning about change impact.
- Recommendation:
1. Map upstream to a narrow projection (only `searchSources`, local provider enable flags, prefix configurations).
2. Apply `distinctUntilChanged` to that projection only.
3. Batch registry operations (compute diff once, apply once).

### P1-2) Prefix collision policy is implicit and potentially surprising
- Evidence: `app/src/main/java/com/milki/launcher/domain/search/SearchProviderRegistry.kt:197`, `app/src/main/java/com/milki/launcher/domain/search/SearchProviderRegistry.kt:203`, `app/src/main/java/com/milki/launcher/domain/search/SearchProviderRegistry.kt:240`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:64`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelSettingsAdapter.kt:75`
- Problem: Registry resolves collisions with last-write-wins in `prefixToProviderId[prefix] = providerId`, based on provider iteration/build order, not explicit policy.
- Impact: Local provider prefixes and dynamic source prefixes can shadow each other non-obviously.
- Recommendation:
1. Add explicit collision policy in domain contract: reject, prioritize local providers, or prioritize custom sources.
2. Validate collisions before applying registry update and surface deterministic errors to settings UI.
3. Add tests for conflict matrix (`contacts/files` vs custom source prefixes).

### P1-3) Source-prefix uniqueness validation is not transactionally enforced at update point
- Evidence: `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:239`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:253`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:259`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:265`, `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt:668`
- Problem: ViewModel validates against a snapshot; repository later normalizes globally and keeps first prefix owner. Under rapid edits, user input may be dropped silently by normalization.
- Impact: Inconsistent UX and hard-to-debug "prefix not saved" complaints.
- Recommendation:
1. Move uniqueness validation fully into repository-level atomic write path.
2. Return structured result (`Success`, `DuplicatePrefix`, `InvalidTemplate`, etc.) instead of silent normalization only.
3. Keep UI/ViewModel validation for immediate feedback, but treat repository as source of truth.

### P1-4) Settings persistence fallback can silently replace user-configured sources with defaults
- Evidence: `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt:651`, `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt:655`, `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt:662`, `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt:568`, `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt:569`
- Problem: Missing/invalid/empty persisted source list falls back to `defaultSources()`, and empty writes remove the key.
- Impact: Impossible to represent intentional "zero custom sources" state; corruption and user intent look the same at runtime.
- Recommendation:
1. Separate first-run seeding from runtime parsing fallback.
2. Persist explicit version/shape marker and explicit empty-list state.
3. Add corruption telemetry and one-time recovery notice.

### P2-1) Dynamic source CRUD still uses broad `updateSettings` transforms for hot edits
- Evidence: `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:153`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:182`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:211`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:222`, `app/src/main/java/com/milki/launcher/presentation/settings/SettingsViewModel.kt:265`
- Problem: Source and source-prefix edits use generic whole-settings transform path even though they are a focused high-frequency area.
- Impact: More mapping/comparison overhead and larger blast radius for future refactors.
- Recommendation:
1. Add targeted repository APIs for source CRUD (`addSource`, `updateSource`, `deleteSource`, `setSourceEnabled`, `addSourcePrefix`, `removeSourcePrefix`).
2. Keep `updateSettings` as escape hatch, not default path.

### P2-2) Settings UI and ViewModel contracts are broad and growing
- Evidence: `app/src/main/java/com/milki/launcher/ui/screens/SettingsScreen.kt:60`, `app/src/main/java/com/milki/launcher/SettingsActivity.kt:47`
- Problem: Many callback params and pass-through wiring make evolution noisy and error-prone.
- Impact: Lower readability and harder feature isolation.
- Recommendation:
1. Introduce `SettingsActions` grouped contracts by section.
2. Split screen into section composables with local action interfaces.

### P2-3) `SettingsActivity` uses `collectAsState` instead of lifecycle-aware collection
- Evidence: `app/src/main/java/com/milki/launcher/SettingsActivity.kt:43`
- Problem: Collection isn’t lifecycle-aware compared to `collectAsStateWithLifecycle` used elsewhere.
- Impact: Minor but avoidable lifecycle mismatch and consistency drift.
- Recommendation:
1. Use `collectAsStateWithLifecycle` for consistency and safer lifecycle behavior.

### P2-4) Configuration pipeline has stale parameter/signature drift
- Evidence: `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelPipelineCoordinator.kt:82`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelPipelineCoordinator.kt:86`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelPipelineCoordinator.kt:106`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModelPipelineCoordinator.kt:110`
- Problem: `searchSources` is threaded through `executeSearch` signature but currently unused in logic.
- Impact: Signal of architecture drift and reduced clarity for future maintainers.
- Recommendation:
1. Remove unused args or make usage explicit.
2. Add lint/static check for unused parameters in internal orchestration code.

## What Is Good Today

1. Strong immutable settings model (`LauncherSettings`) with clear defaults.
2. Repository abstraction is clean and testable in principle.
3. DataStore is a good persistence choice for async and atomic writes.
4. Prefix/source normalization exists and prevents many malformed states.
5. Search-side settings adaptation is already separated (`SearchViewModelSettingsAdapter`), which is a good modular direction.

## Scalability Pattern Recommendations

1. Introduce a dedicated `SettingsMutationResult` family (feature-scoped where needed) for deterministic UX feedback and conflict handling.
2. Split settings into feature-scoped repositories/adapters:
- `SearchSettingsStore`
- `HomeSettingsStore`
- `AppearanceSettingsStore`
3. Add explicit settings schema/version keys and migration functions even while staying on Preferences DataStore.
4. Add focused tests:
- prefix collision behavior
- source normalization and migration
- adapter diffing behavior under unrelated settings changes
- reset/default semantics for custom sources

## Potential Refactoring, Cleanup, and Redesign Backlog (Settings)

This section is a practical backlog for the next settings-focused iterations. It is intentionally broader than the findings list and includes cleanup and redesign opportunities that reduce future maintenance cost.

### A) Domain and Contract Refactoring

1. Introduce feature-specific settings aggregates instead of one broad `LauncherSettings` dependency at every callsite.
- Candidate models:
	- `SearchSettings` (sources, provider toggles, prefixes, result limits)
	- `AppearanceSettings` (layout, icon visibility, hint visibility)
	- `HomeSettings` (tap/swipe/home-button behavior)
- Benefit: reduces accidental coupling and makes use-case ownership clearer.

2. Introduce feature-level repository interfaces as first-class contracts.
- `SearchSettingsRepository`
- `AppearanceSettingsRepository`
- `HomeSettingsRepository`
- Keep current `SettingsRepository` as orchestration facade during migration.

3. Standardize mutation result contracts.
- Continue moving from fire-and-forget writes toward typed outcomes:
	- `Success`
	- `ValidationError`
	- `Conflict`
	- `NotFound`
	- `NoOp`
- Benefit: deterministic UI behavior and simpler telemetry hooks.

4. Formalize prefix collision contract in one place.
- Current policy should be codified as an explicit domain rule document + constants.
- UI should be able to query conflict reason before write (pre-check), but repository/domain remains source of truth.

### B) Persistence and DataStore Cleanup

1. Introduce a lightweight persistence schema module for Preferences keys.
- Centralize key versioning/state marker conventions.
- Add migration helper functions with strict unit tests.

2. Separate first-run seed logic from parse logic for all settings groups, not only search sources.
- Search sources now have state semantics; same pattern can be applied to future complex keys.

3. Add corruption handling policy per key family.
- Define behavior matrix:
	- missing key
	- malformed JSON
	- semantically invalid payload
- Choose deterministic recovery action and optional user-facing notice path.

4. Add write-coalescing helpers for related settings updates.
- Example: source update + prefix mutation in one transaction API when UI submits both.

### C) UI and Presentation Redesign

1. Continue section extraction beyond action contracts.
- Move each settings section into its own file for discoverability:
	- `SearchBehaviorSection.kt`
	- `AppearanceSection.kt`
	- `HomeSettingsSection.kt`
	- `CustomSourcesSection.kt`
	- `LocalPrefixesSection.kt`

2. Introduce section-specific view state models.
- Reduce entire-screen recomposition sensitivity and clarify data dependencies.
- Example: `CustomSourcesUiState`, `LocalPrefixesUiState`.

3. Consolidate dialog orchestration into a typed settings dialog state.
- Replace multiple nullable flags with a single sealed state:
	- `None`
	- `ConfirmReset`
	- `AddSource`
	- `EditSource(sourceId)`
	- `DeleteSource(sourceId)`

4. Introduce a settings-intent layer for complex flows.
- Keep simple toggles direct.
- Use intents for multi-step edits (source create/edit + validation + mutation result display).

### D) Search Runtime Configuration Pipeline Redesign

1. Introduce a dedicated `SearchRuntimeConfig` projection model at repository boundary.
- Should include only runtime-relevant values used by search pipeline and registry.
- Benefit: avoids repeated projection logic and reduces fanout risk.

2. Add config diff model for registry updates.
- Compute `added/removed/updated` providers and prefixes before applying mutation.
- Enables precise logs, tests, and potential rollback semantics.

3. Add observable conflict report API.
- When conflicts are resolved by policy, expose structured report for debugging/tests/UI hints.

### E) Testing and Quality Infrastructure

1. Add repository transaction tests for source CRUD + prefix operations under rapid sequential writes.

2. Add parser state-matrix tests for search sources.
- first-run defaults
- initialized-empty
- initialized-invalid
- legacy-no-state-but-valid-json

3. Add UI contract tests for settings action groups.
- Verify each section triggers expected action contract without cross-section leakage.

4. Add performance guard tests for settings fanout.
- Ensure unrelated settings edits do not trigger search registry churn.

### F) Observability and Operational Cleanup

1. Add structured logging around settings mutation outcomes.
- Mutation type, result category, conflict reason.

2. Add one-time recovery notification mechanism for corrupted persisted search source payloads.
- Keep non-blocking UX.
- Avoid repeated toasts/dialog spam with persisted "notice shown" marker.

3. Add developer diagnostics endpoint/screen (debug build only) for settings state snapshot.
- Raw key state + parsed model + migration state marker visibility.

## Recommended Next Iteration After Current Work

1. Implement typed dialog state and section-file extraction for `SettingsScreen` to complete presentation decomposition.
2. Add parser state-matrix and repository transaction tests for search sources and prefixes.
3. Add conflict-report surface from search registry into settings validation UX.
4. Add corruption telemetry + one-time user recovery notice for initialized-invalid source payloads.

## Suggested Implementation Order

1. Narrow settings adapter observation scope and collision-policy enforcement.
2. Add repository-level transactional validation/results for source prefix operations.
3. Add targeted source CRUD repository methods and migrate ViewModel callsites.
4. Adopt lifecycle-aware collection in `SettingsActivity` and clean stale pipeline signatures.
5. Add regression tests for all configuration conflict/fallback paths.
