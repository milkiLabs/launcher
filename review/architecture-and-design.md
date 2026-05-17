# Architecture & Design Review

> Analysis of clean architecture adherence, dependency injection, repository patterns, abstraction quality, separation of concerns, and module organization.

---

## 1. Clean Architecture Adherence

### 1.1 Strengths

**Layer separation is well-maintained.** The package structure follows `app -> presentation -> domain -> data -> core` consistently. Domain models (`AppInfo`, `HomeItem`, `Contact`, `FileDocument`) contain zero Android framework imports (except `@Immutable` from Compose runtime and `Uri`), which is correct.

`HomeModelWriter` at `domain/homegraph/HomeModelWriter.kt` is a standout example of pure domain logic — no Android dependencies, deterministic mutation engine with sealed Command/Result/Error types.

`SearchProvider` interface at `domain/repository/SearchProvider.kt:63` correctly defines the contract in domain while implementations live in data layer.

### 1.2 Violations

| # | Issue | File:Line | Severity |
|---|-------|-----------|----------|
| F1 | `HomeItem` sealed class leaks `android.content.pm.ShortcutInfo` into domain | `HomeItem.kt:42` | HIGH |
| F2 | `LauncherBackupRepository` uses Android `Intent` in domain type alias | `LauncherBackupRepository.kt:8` | HIGH |
| F3 | `SearchViewModelSettingsAdapter` imports `ConfigurableUrlSearchProvider` from data layer | `SearchViewModelSettingsAdapter.kt:3` | HIGH |
| F4 | `HomeViewModel` takes `Context` as constructor parameter | `HomeViewModel.kt:46` | HIGH |

**F1 Detail:** `HomeItem.AppShortcut.fromShortcutInfo()` at line 277 takes a framework type as parameter. The mapping from `ShortcutInfo` should happen in the data or presentation layer.

**F2 Detail:** The `WidgetBindPermissionRequester` type alias should accept a domain-level abstraction (e.g., `WidgetBindRequest`) that the presentation layer translates to an `Intent`.

**F3 Detail:** This is a presentation-layer class directly importing a data-layer implementation. It should depend on a factory interface defined in domain instead.

**F4 Detail:** The `Context` is used for `packageManager` (line 90) and `appContext` passed to `HomeAvailabilityPruner` (line 79). These should be abstracted into domain services or injected as specific interfaces.

---

## 2. Dependency Injection (Koin)

### 2.1 Strengths

**Module organization is excellent.** The split into `coreModule`, `searchModule`, `homeModule`, `widgetModule`, `settingsModule`, `drawerModule` at `AppModule.kt:80` follows feature-oriented DI correctly. Dependency direction rules documented at lines 35-39 are sound.

**Singleton vs factory choices are well-reasoned.** Repositories are correctly `single {}`, ViewModels use `viewModel {}` DSL.

### 2.2 Issues

| # | Issue | File:Line | Severity |
|---|-------|-----------|----------|
| F5 | `coreModule` has cross-feature leakage via `LauncherBackupRepository` | `CoreModule.kt:132-141` | HIGH |
| F6 | `UrlHandlerResolver` registered in `coreModule` but is search-specific | `CoreModule.kt:119` | LOW |
| F7 | No Koin qualifiers or named bindings used anywhere | All DI modules | LOW |

**F5 Detail:** `coreModule` depends on `homeRepository` (defined in `homeModule`) and `widgetHostManager` (defined in `widgetModule`). This **inverts the documented dependency direction** — the docs say "coreModule must NEVER depend on any feature module."

**F6 Detail:** `UrlHandlerResolver` is used by `SearchViewModel` and `SettingsViewModel`, making it cross-feature, but its conceptual home is the search domain. Consider a `domain/url` package.

---

## 3. Repository Pattern

### 3.1 Strengths

**Interface/implementation separation is consistent.** Every repository has a domain interface and a data implementation.

**`AppRepositoryImpl`** correctly composes from focused collaborators (`InstalledAppsCatalog`, `RecentAppsStore`, `PackageChangeMonitor`) rather than doing everything itself.

**`ContactsRepositoryImpl`** uses a clean three-layer split (`ContactsQueryLayer`, `ContactsMappingLayer`, `ContactsRecentStorage`) — excellent decomposition.

### 3.2 Issues

| # | Issue | File | Severity |
|---|-------|------|----------|
| F8 | `SettingsRepository` is a god interface with 27 methods | `SettingsRepository.kt:29-236` | HIGH |
| F9 | `HomeRepository.findAvailablePosition` leaks placement policy | `HomeRepository.kt:44` | LOW |
| F10 | `FilesRepositoryImpl` is 524 lines of cursor manipulation | `FilesRepositoryImpl.kt:62` | MEDIUM |

**F8 Detail:** Violates Interface Segregation Principle. Should be split into `SettingsRepository`, `SearchSourceRepository`, `PrefixConfigurationRepository`, `HiddenAppsRepository`.

**F10 Detail:** The `CursorRowOutcome` sealed interface is nice, but this class should be decomposed further. Cursor parsing (`readCursorRow`, `resolveMediaStoreColumns`) should be in a separate `FileCursorMapper` class.

---

## 4. Over-Engineering

| # | Issue | Files | Severity |
|---|-------|-------|----------|
| F11 | SearchViewModel split across 5 files (~750 lines total) | `presentation/search/*` | MEDIUM |
| F12 | `HomeModelWriter.Command` has 17 command types; ViewModel is thin pass-through | `HomeModelWriter.kt`, `HomeViewModel.kt` | LOW |
| F13 | `FileFilterConfig` is 561 lines of static configuration with excessive docs | `FileFilterConfig.kt` | LOW |
| F14 | `SearchProviderRegistry` is 352 lines with extensive ASCII diagrams | `SearchProviderRegistry.kt` | LOW |

**F11 Detail:** `SearchViewModel.kt` (313 lines), `SearchViewModelStateHolder.kt` (87), `SearchViewModelPipelineCoordinator.kt` (179), `SearchViewModelSettingsAdapter.kt` (133), `SearchViewModelModels.kt` (36). The `PipelineCoordinator` has only 2 public methods (`bind` and `executeSearch`) and could reasonably be inlined.

**F12 Detail:** The ViewModel adds almost no value beyond command construction for simple operations like `unpinItem`. Consider whether the Command pattern is adding indirection without benefit.

---

## 5. Under-Engineered Areas

| # | Issue | Files | Severity |
|---|-------|-------|----------|
| F15 | No unified error handling abstraction | Multiple | HIGH |
| F16 | No loading/error state types for repository flows | `HomeRepository.kt` | MEDIUM |
| F17 | `MainActivity` directly injects 10 dependencies | `MainActivity.kt:43` | LOW |

**F15 Detail:** Errors are handled inconsistently:
- `HomeViewModel` uses string fallback error messages
- `HomeModelWriter` has a proper `Error` sealed interface
- `FilesRepositoryImpl` catches exceptions and logs them
- `SearchViewModelPipelineCoordinator` swallows all exceptions

There should be a unified `sealed interface AppError` in the domain layer.

**F16 Detail:** `HomeRepository.pinnedItems` is a `Flow<List<HomeItem>>` that can only emit data. There's no way for the UI to distinguish "loading" from "empty list" or to surface errors.

---

## 6. Circular Dependencies and Tight Coupling

| # | Issue | File:Line | Severity |
|---|-------|-----------|----------|
| F18 | `coreModule` -> `homeRepository` -> `homeModule` circular dependency | `CoreModule.kt:136` | HIGH |
| F19 | `SearchViewModelSettingsAdapter` creates `ConfigurableUrlSearchProvider` directly | `SearchViewModelSettingsAdapter.kt:59` | HIGH |
| F20 | `HomeViewModel` receives `widgetHostManager` per-call, not as constructor dep | `HomeViewModel.kt:390-396` | MEDIUM |

**F19 Detail:** This tightly couples the settings adapter to a specific data-layer implementation. If `ConfigurableUrlSearchProvider` needs new constructor parameters, this file must change.

**F20 Detail:** Inconsistent — the ViewModel has `homeRepository` and `appRepository` as constructor dependencies but receives `widgetHostManager` per-call. Either inject it in the constructor or pass all dependencies per-call.

---

## 7. API Design Quality

### 7.1 Good Patterns

- `HomeModelWriter.Command` sealed interface — each command is self-contained
- `PrefixMutationResult` sealed interface — exhaustive, type-safe mutation outcomes
- `SearchProvider` interface — clean and focused

### 7.2 Issues

| # | Issue | File:Line | Severity |
|---|-------|-----------|----------|
| F21 | `SettingsRepository` method signatures are inconsistent | `SettingsRepository.kt:52-119` | MEDIUM |
| F22 | `LauncherBackupRepository.importFromUri` takes callback parameter | `LauncherBackupRepository.kt:12` | MEDIUM |
| F23 | `HomeItem` factory method naming inconsistent (`fromX` vs `create`) | `HomeItem.kt:133,179,226,277,313,448` | LOW |

**F21 Detail:** Compare `setMaxSearchResults(value: Int)` (1 param, returns Unit) vs `updateSearchSource(sourceId, name, urlTemplate, prefixes, accentColorHex)` (5 params, returns result). Consider using a `SettingsUpdate` sealed interface.

**F22 Detail:** The repository is reaching back into the presentation layer for UI interaction. It should emit a `WidgetBindRequired` event that the caller handles.

---

## 8. Separation of Concerns

| # | Issue | File:Line | Severity |
|---|-------|-----------|----------|
| F24 | `HomeViewModel` implements `HomeMutationHandler` interface | `HomeViewModel.kt:47` | MEDIUM |
| F25 | `MainActivity` constructs `LauncherHostRuntime` with 8 provider lambdas | `MainActivity.kt:78-91` | MEDIUM |
| F26 | `SettingsActivity` constructs `SettingsActions` with 15+ method references | `SettingsActivity.kt:108-142` | LOW |
| F27 | `FileFilterConfig` is an `object` in domain but contains filtering logic | `FileFilterConfig.kt:83` | LOW |

---

## 9. Domain Model Quality

### 9.1 Rich Models (Good)

- `HomeItem` sealed class — 6 subtypes with meaningful properties, factory methods, serialization
- `LauncherSettings` — comprehensive with gesture triggers, search providers, prefix configurations
- `SearchSource` — URL template building, prefix normalization, color normalization, validation

### 9.2 Anemic Models (Needs Improvement)

| # | Issue | File | Severity |
|---|-------|------|----------|
| F28 | `GridPosition` lacks bounds checking, neighbor computation, distance calculation | `GridPosition.kt:61` | MEDIUM |
| F29 | `Contact` has no behavior beyond free-floating extension functions | `Contact.kt:34` | LOW |
| F30 | `AppInfo.matchesQuery` is a free-floating extension, not a member | `AppInfo.kt:33` | LOW |

**F28 Detail:** Grid math is scattered across `HomeModelWriter`, `HomeGridOccupancyPolicy`, and `GridSpan`. `GridPosition` should own its geometry.

---

## 10. Module Organization

### 10.1 Strengths

- 6-module Koin split is well-reasoned and documented
- Package structure matches documented architecture
- Domain layer packages correctly organized by concern

### 10.2 Issues

| # | Issue | Severity |
|---|-------|----------|
| F31 | Missing `backupModule` — `LauncherBackupRepository` in wrong module | HIGH |
| F32 | `domain/search` contains both interfaces and concrete implementations | MEDIUM |
| F33 | No dedicated `domain/usecase` package | LOW |
| F34 | `presentation/home` mixes ViewModel with coordinators | LOW |

---

## 11. Priority Summary

| Priority | Finding | File | Impact |
|----------|---------|------|--------|
| P0 | `coreModule` depends on feature modules | `CoreModule.kt:132-141` | Architecture integrity |
| P0 | Domain layer imports Android framework types | `HomeItem.kt:42` | Testability, layer purity |
| P1 | `SettingsRepository` is a god interface (27 methods) | `SettingsRepository.kt:29-236` | Maintainability |
| P1 | Presentation imports data-layer implementation | `SearchViewModelSettingsAdapter.kt:3` | Layer coupling |
| P1 | `HomeViewModel` holds `Context` reference | `HomeViewModel.kt:46` | Memory leak risk |
| P2 | Inconsistent error handling across layers | Multiple | User experience |
| P2 | `HomeItem` factory method naming inconsistency | `HomeItem.kt` | API consistency |
| P2 | Anemic `GridPosition` with scattered grid math | `GridPosition.kt:61` | Code duplication |
| P3 | Search ViewModel split across 5 files | `presentation/search/*` | Cognitive overhead |
| P3 | `FileFilterConfig` is 561 lines | `FileFilterConfig.kt:1` | File bloat |
