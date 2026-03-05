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

1. Introduce a typed settings schema (Proto DataStore) with versioned migrations once configuration surface grows further.
2. Create dedicated `SettingsMutationResult` sealed class for deterministic UX feedback and conflict handling.
3. Split settings into feature-scoped repositories/adapters:
- `SearchSettingsStore`
- `HomeSettingsStore`
- `AppearanceSettingsStore`
4. Add focused tests:
- prefix collision behavior
- source normalization and migration
- adapter diffing behavior under unrelated settings changes
- reset/default semantics for custom sources

## Suggested Implementation Order

1. Narrow settings adapter observation scope and collision-policy enforcement.
2. Add repository-level transactional validation/results for source prefix operations.
3. Add targeted source CRUD repository methods and migrate ViewModel callsites.
4. Adopt lifecycle-aware collection in `SettingsActivity` and clean stale pipeline signatures.
5. Add regression tests for all configuration conflict/fallback paths.
