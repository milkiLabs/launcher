## 1. Clean Architecture Adherence

### 1.1 Strengths

**Layer separation is well-maintained.** The package structure follows `app -> presentation -> domain -> data -> core` consistently. Domain models (`AppInfo`, `HomeItem`, `Contact`, `FileDocument`) contain zero Android framework imports (except `@Immutable` from Compose runtime and `Uri`), which is correct.

`HomeModelWriter` at `domain/homegraph/HomeModelWriter.kt` is a standout example of pure domain logic — no Android dependencies, deterministic mutation engine with sealed Command/Result/Error types.

`SearchProvider` interface at `domain/repository/SearchProvider.kt:63` correctly defines the contract in domain while implementations live in data layer.

## 2. Dependency Injection (Koin)

### 2.1 Strengths

**Module organization is excellent.** The split into `coreModule`, `searchModule`, `homeModule`, `widgetModule`, `settingsModule`, `drawerModule` at `AppModule.kt:80` follows feature-oriented DI correctly. Dependency direction rules documented at lines 35-39 are sound.

**Singleton vs factory choices are well-reasoned.** Repositories are correctly `single {}`, ViewModels use `viewModel {}` DSL.

## 3. Repository Pattern

### 3.1 Strengths

**Interface/implementation separation is consistent.** Every repository has a domain interface and a data implementation.

**`AppRepositoryImpl`** correctly composes from focused collaborators (`InstalledAppsCatalog`, `RecentAppsStore`, `PackageChangeMonitor`) rather than doing everything itself.

**`ContactsRepositoryImpl`** uses a clean three-layer split (`ContactsQueryLayer`, `ContactsMappingLayer`, `ContactsRecentStorage`) — excellent decomposition.

## 7. API Design Quality

### 7.1 Good Patterns

- `HomeModelWriter.Command` sealed interface — each command is self-contained
- `PrefixMutationResult` sealed interface — exhaustive, type-safe mutation outcomes
- `SearchProvider` interface — clean and focused

## 9. Domain Model Quality

### 9.1 Rich Models (Good)

- `HomeItem` sealed class — 6 subtypes with meaningful properties, factory methods, serialization
- `LauncherSettings` — comprehensive with gesture triggers, search providers, prefix configurations
- `SearchSource` — URL template building, prefix normalization, color normalization, validation

## 10. Module Organization

### 10.1 Strengths

- 6-module Koin split is well-reasoned and documented
- Package structure matches documented architecture
- Domain layer packages correctly organized by concern

# State Management, Concurrency & Lifecycle Review

## 1. ViewModel State Management

### 1.1 Correct Patterns

| File                                  | Pattern                                                       | Assessment |
| ------------------------------------- | ------------------------------------------------------------- | ---------- |
| `SearchViewModelStateHolder.kt:25-39` | MutableStateFlow internally, combined into derived StateFlows | CORRECT    |
| `HomeViewModel.kt:70-72`              | Private mutable, public immutable flows                       | CORRECT    |
| `HomeViewModel.kt:119-122`            | `SharingStarted.WhileSubscribed(5_000)` with 5s timeout       | CORRECT    |
| `AppDrawerViewModel.kt:63-71`         | `mapLatest` with `withContext(Dispatchers.Default)`           | CORRECT    |

## 2. Coroutine Scope Management

### 2.2 Correct Patterns

| File                                  | Pattern                                                     | Assessment |
| ------------------------------------- | ----------------------------------------------------------- | ---------- |
| `HomeViewModel.kt:99-103`             | `onCleared()` cancels `deferredStartupJob` and stops pruner | CORRECT    |
| `PinShortcutRequestCoordinator.kt:22` | Uses `activity.lifecycleScope` — lifecycle-aware            | CORRECT    |
| `HomeIconWarmupCoordinator.kt`        | Passed `viewModelScope` — cancelled on ViewModel clear      | CORRECT    |

## 5. Lifecycle Awareness

### 5.2 Correct Patterns

| File                                  | Pattern                                                             | Assessment |
| ------------------------------------- | ------------------------------------------------------------------- | ---------- |
| `WidgetPlacementCoordinator.kt:27-43` | `initialize()` must be called before operations — correctly ordered | CORRECT    |
| `LauncherHostRuntime.kt:101-107`      | `initialize()` registers callbacks once                             | CORRECT    |

## 10. Snapshot State vs Flow State

### 10.1 Correct Usage

| File                               | State Type                                                   | Assessment |
| ---------------------------------- | ------------------------------------------------------------ | ---------- |
| `SurfaceStateCoordinator.kt:30-43` | Compose `mutableStateOf` for non-ViewModel class             | CORRECT    |
| `SearchViewModel.kt:16-42`         | 4-layer state architecture (input, background, pipeline, UI) | CORRECT    |
| `HomeViewModel.kt`                 | StateFlow for repository data, MutableStateFlow for UI state | CORRECT    |

## 9. String Handling

### 9.2 Good Patterns

Most of the codebase correctly uses string templates (`"$packageName/$activityName"`) rather than concatenation.

## 3. Build Configuration Issues

### 3.1 SDK Configuration

| `minSdk` | 24 | Android 7.0; covers ~95%+ of devices | GOOD |

---

## 9. What NOT to Simplify

These areas are complex but **correctly so** — simplifying them would introduce bugs:

| Area                                    | Why Keep Complex                                                               |
| --------------------------------------- | ------------------------------------------------------------------------------ |
| `HomeModelWriter` Command pattern       | Provides atomic, testable mutations. Flattening would couple mutation logic.   |
| `AsyncSnapshotCache`                    | Load deduplication and cancellation are non-trivial and correctly implemented. |
| `GridReorderEngine`                     | Deterministic reorder is hard; the complexity is justified.                    |
| `SearchProviderRegistry` prefix routing | Multi-provider prefix matching is inherently complex.                          |
| `WidgetHostManager` lifecycle           | AppWidgetHost lifecycle is fragile; the careful state management is necessary. |
| `HomeAvailabilityPruner`                | ContentObserver + DataStore sync is correctly complex.                         |

### 1.2 Gesture Handling

**Good:** Consistent drag-and-drop architecture with layered approach (`InternalGridDragLayer`, `ExternalDropRoutingLayer`, `WidgetOverlayLayer`, `DropHighlightLayer`).

## 12. Drag and Drop UX

### 12.1 Strengths

- Layered architecture with separate drag, drop routing, highlight, and widget overlay layers
- Preview position uses same reorder engine as commit
- Auto-paging when dragging to folder edges
- Drag-out-to-extract from folder with platform drag

## 2. Android Launcher Best Practices

### 2.1 HOME Intent Handling

**Good:**

- Proper `ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT` intent filter
- `singleTask` launch mode
- Default launcher detection using `RoleManager.ROLE_HOME` with fallback
- HOME role request with cascading fallbacks

### 2.3 Widget Hosting

**Good:**

- Proper `AppWidgetHost` lifecycle with `startListening`/`stopListening`
- Proper widget ID allocation/deallocation
- `WidgetLongPressFrameLayout` for reliable long-press detection
- API-level branching for `SIZEF` (API 31+) vs deprecated method

## 5. Error UX

### 5.1 Good Patterns

- `PermissionHandler` — context-specific messages for each permission type
- `HomeViewModel` — `MutableStateFlow<String?>` for `lastMoveErrorMessage` with mutation counting

## 7. Configuration Change Handling

### 7.1 ViewModel Survival

**Good:** All ViewModels extend `ViewModel` and survive configuration changes. Koin's `viewModel()` delegate handles this correctly.

## 8. Multi-Window & Foldable Support

### 8.1 Multi-Window

**Good:**

- `android:resizeableActivity="true"` declared on application and MainActivity

## 9. RTL Layout Support

### 9.1 Declaration

**Good:** `android:supportsRtl="true"` declared in manifest.

## 10. Dark Mode Support

### 10.1 Theme Implementation

**Good:**

- Dynamic color support on Android 12+
- Fallback to static light/dark schemes

## 3. Existing Test Quality

### 3.1 Strengths

| Test                                                    | Quality                                                 |
| ------------------------------------------------------- | ------------------------------------------------------- |
| `GridReorderEngineDeterminismTest.kt`                   | Tests determinism — important for drag-drop consistency |
| `HomeModelWriterTest.kt`                                | Tests core domain mutation logic                        |
| `AppQueryRankerTest.kt`                                 | Tests search ranking algorithm                          |
| `WidgetLayoutPolicyTest.kt` / `WidgetSpanPolicyTest.kt` | Tests widget layout policies                            |

## 8. Build Performance

### 8.1 Good Practices

| Setting                         | File                          | Status  |
| ------------------------------- | ----------------------------- | ------- |
| Configuration cache             | `gradle.properties:1`         | ENABLED |
| Parallel execution              | `gradle.properties:2`         | ENABLED |
| Build cache                     | `gradle.properties:3`         | ENABLED |
| Non-transitive R class          | `gradle.properties:7`         | ENABLED |
| Gradle wrapper SHA verification | `gradle-wrapper.properties:4` | ENABLED |
