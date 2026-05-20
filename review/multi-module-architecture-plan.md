# Multi-Module Architecture Plan

---

## Executive Summary

**Current state:** Single `:app` module with 240 main Kotlin files across 5 layers (core 30, data 47, domain 55, presentation 24, ui 81) plus `:baselineprofile`.

**Verdict:** Multi-module is a good future direction, but the proposed 14-module full split should **not** be marked as high priority. The codebase has dependency boundary violations that must be resolved first, and a large split would add Gradle overhead that slows development unless build times are already a real pain.

**Recommended approach:** Incremental architecture hardening in 3 phases — fix boundaries, extract foundational modules, then decide on feature modules based on measured build impact.

---

## Phase 0: Dependency Boundary Audit & Cleanup

**Goal:** Make the existing package-level layers clean enough to extract into modules without circular dependencies.

**Effort:** 2-3 days
**Risk:** Low — no structural changes, only import refactoring

### 0.1 Fix `domain` → `data` Leaks

**Files affected:** 1 file

| File                                  | Imports from `data`                                                     | Fix Strategy                                                                                                                                             |
| ------------------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `domain/search/UrlHandlerResolver.kt` | `data.cache.SnapshotCache`, `data.repository.apps.PackageChangeMonitor` | Extract `SnapshotCache` to `core:common` (it's a pure utility). Change `PackageChangeMonitor` to an interface in `domain` with implementation in `data`. |

**Detail:** `UrlHandlerResolver` (228 lines) lives in `domain/search` but directly uses `SnapshotCache` and `PackageChangeMonitor` from the data layer. Two fixes needed:

1. **`SnapshotCache`** — This is a generic thread-safe cache holder (24 lines, no Android dependencies). Move it to `core/util/` or a new `core/cache/` package. It's used by multiple features and has no layer-specific semantics.

2. **`PackageChangeMonitor`** — This is a concrete data-layer class that observes Android package changes. `UrlHandlerResolver` should depend on a `PackageChangeListener` interface defined in `domain`, with `PackageChangeMonitor` as the implementation in `data`.

### 0.2 Fix `ui` → `data` Leaks

**Files affected:** 13 files across 4 data packages

| UI File                                                    | Imports from `data`                                                                                                                    | Fix Strategy                                                                 |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `ui/components/common/AppIcon.kt`                          | `data.icon.AppIconMemoryCache`                                                                                                         | Receive cache through presentation layer (ViewModel or composable parameter) |
| `ui/components/common/ShortcutIcon.kt`                     | `data.icon.ShortcutIconLoader`                                                                                                         | Same — inject through presentation                                           |
| `ui/components/common/ItemContextMenuState.kt`             | `data.contextmenu.AppContextDataCache`                                                                                                 | Move cache access to presentation; UI receives resolved data                 |
| `ui/components/launcher/AppDrawerOverlay.kt`               | `data.icon.AppIconMemoryCache`                                                                                                         | Same pattern                                                                 |
| `ui/components/launcher/DraggablePinnedItemsGrid.kt`       | `data.widget.WidgetHostManager`                                                                                                        | Receive through ViewModel state or composable parameter                      |
| `ui/components/launcher/DraggablePinnedItemsGridLayers.kt` | `data.widget.WidgetHostManager`                                                                                                        | Same                                                                         |
| `ui/components/launcher/DropHighlightLayer.kt`             | `data.widget.WidgetHostManager`                                                                                                        | Same                                                                         |
| `ui/components/launcher/ExternalDropRoutingLayer.kt`       | `data.widget.WidgetHostManager`                                                                                                        | Same                                                                         |
| `ui/components/launcher/ExternalHomeDropActions.kt`        | `data.widget.WidgetHostManager`                                                                                                        | Same                                                                         |
| `ui/components/launcher/widget/HomeScreenWidgetView.kt`    | `data.widget.WidgetHostManager`                                                                                                        | Same                                                                         |
| `ui/components/launcher/widget/PopupWidgetView.kt`         | `data.widget.WidgetHostManager`                                                                                                        | Same                                                                         |
| `ui/components/launcher/widget/WidgetPickerBottomSheet.kt` | `data.widget.WidgetHostManager`, `data.widget.WidgetPickerCatalogStore`, `data.widget.WidgetPickerEntry`, `data.widget.WidgetAppGroup` | Move data access to ViewModel; UI receives state objects                     |
| `ui/screens/launcher/LauncherScreen.kt`                    | `data.widget.WidgetHostManager`, `data.widget.WidgetPickerCatalogStore`                                                                | Same                                                                         |

**Detail:** 18 cross-layer imports from UI directly into `data.*`. The pattern is consistent: UI components are reaching for concrete data-layer services (`WidgetHostManager`, `AppIconMemoryCache`, `ShortcutIconLoader`, `AppContextDataCache`) instead of receiving them through presentation-layer contracts.

**Fix pattern for all 13 files:**

1. Move the data-layer dependency into the corresponding ViewModel or presentation coordinator
2. Expose the needed data through UI state or composable parameters
3. Remove the `import com.milki.launcher.data.*` from UI files

### 0.3 Fix `coreModule` Cross-Feature Leakage

**File:** `core/di/CoreModule.kt`

**Issues:**

- `coreModule` constructs `SettingsRepositoryImpl` (belongs in settings feature)
- `coreModule` constructs `AppRepositoryImpl` (belongs in app/data feature)
- `coreModule` constructs `UrlHandlerResolver` (belongs in search domain)
- `coreModule` depends on `homeRepository` defined in `homeModule` (inverted dependency)

**Fix:** Reorganize DI modules to align with feature boundaries:

- `coreModule` → only core utilities (Context, Dispatchers, Json, etc.)
- `dataModule` → repository implementations
- Each feature module declares its own DI

### 0.4 Resolve `domain/search` Mixed Responsibilities

**Files in `domain/search/` (10 files):**

| File                          | Type                       | Notes                                                 |
| ----------------------------- | -------------------------- | ----------------------------------------------------- |
| `SearchProviderFactory.kt`    | Interface                  | Clean — belongs in domain                             |
| `SearchProviderRegistry.kt`   | Implementation (352 lines) | Contains concrete provider registrations — borderline |
| `SuggestionResolver.kt`       | Implementation             | Uses domain models only — clean                       |
| `SuggestionPatternMatcher.kt` | Implementation             | Pure logic — clean                                    |
| `QueryTextMatcher.kt`         | Implementation             | Pure logic — clean                                    |
| `ParsedQuery.kt`              | Model                      | Clean                                                 |
| `FilterAppsUseCase.kt`        | Use case                   | Clean                                                 |
| `AppQueryRanker.kt`           | Implementation             | Pure logic — clean                                    |
| `ActionSuggestion.kt`         | Model                      | Clean                                                 |
| `UrlHandlerResolver.kt`       | Implementation             | **Leaky** — imports `data.*` (see 0.1)                |

**Verdict:** After fixing 0.1, `domain/search` is clean enough to stay as-is. `SearchProviderRegistry` is large but contains only domain-level wiring.

---

## Phase 1: Extract Foundational Modules

**Goal:** Create 3 foundational modules that have zero or minimal internal dependencies, proving the multi-module build works.

**Effort:** 2-3 days
**Risk:** Low — these modules are self-contained

### 1.1 `:core:model`

**Source:** `domain/model/*` (15 files + `backup/` subdirectory)

**Build config:**

```kotlin
// core/model/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.milki.launcher.core.model"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.runtime) // for @Immutable
}
```

**Contents:**

- `HomeItem.kt`, `AppInfo.kt`, `Contact.kt`, `GridPosition.kt`, `GridSpan.kt`
- `LauncherSettings.kt`, `SearchResult.kt`, `SearchSource.kt`, `SearchProviderConfig.kt`
- `PrefixConfig.kt`, `PrefixMutationResult.kt`, `PermissionAccessState.kt`
- `FileDocument.kt`, `FileFilterConfig.kt`
- `backup/` — backup-related models

**Dependency direction:** No internal dependencies. Depends only on `kotlinx.serialization` and `androidx.compose.runtime`.

**Consumers:** Every other module will depend on `:core:model`.

### 1.2 `:core:common`

**Source:** Selected files from `core/util/`, `core/perf/`, `core/url/`, `core/file/`

**Build config:**

```kotlin
// core/common/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.milki.launcher.core.common"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
```

**Contents:**

| Package       | Files                                               | Notes                           |
| ------------- | --------------------------------------------------- | ------------------------------- |
| `core/util/`  | `ColorUtil.kt`, `CsvUtil.kt`, `ViewModelSharing.kt` | Pure utilities                  |
| `core/cache/` | `SnapshotCache.kt` (moved from `data/cache/`)       | Generic cache — no Android deps |
| `core/perf/`  | `TraceSection.kt`                                   | Pure utility                    |
| `core/url/`   | `UrlValidator.kt`                                   | Pure utility                    |
| `core/file/`  | `MimeTypeUtil.kt`                                   | Pure utility                    |

**Exclusions (stay in `:app` for now):**

- `core/di/` — DI modules need to see all implementations
- `core/intent/` — Android-specific, depends on Context
- `core/permission/` — Android-specific, depends on Activity/Context
- `core/launcher/` — Android-specific, depends on launcher APIs
- `core/shortcut/` — Android-specific, depends on ShortcutManager
- `core/deviceadmin/` — Android-specific

**Dependency direction:** Depends on `:core:model`. No other internal deps.

### 1.3 `:domain`

**Source:** `domain/repository/*`, `domain/search/*` (after 0.1 fix), `domain/drag/`, `domain/drawer/`, `domain/homegraph/`, `domain/widget/`

**Build config:**

```kotlin
// domain/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.milki.launcher.domain"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
}
```

**Contents:**

| Package              | Files                    | Notes                       |
| -------------------- | ------------------------ | --------------------------- |
| `domain/repository/` | 11 interface files       | Repository contracts        |
| `domain/search/`     | 10 files (after 0.1 fix) | Search logic, use cases     |
| `domain/drag/`       | drag/drop, drag/reorder  | Drag domain logic           |
| `domain/drawer/`     | drawer logic             | Drawer domain logic         |
| `domain/homegraph/`  | homegraph + writer       | Home graph manipulation     |
| `domain/widget/`     | widget policies          | Widget layout/span policies |

**Dependency direction:** Depends on `:core:model` and `:core:common`. Must NOT import from `data.*` or `presentation.*`.

**Verification:** After extraction, run `./gradlew :domain:dependencies` and verify no `:app` or `:data` transitive deps.

---

## Phase 2: Measure & Decide

**Goal:** Validate that the 3-module split works, measure build impact, and decide whether to proceed with feature modules.

**Effort:** 1 day
**Risk:** Low

### 2.1 Updated `settings.gradle.kts`

```kotlin
rootProject.name = "milki launcher"
include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:common")
include(":domain")
```

### 2.2 Updated `:app` Dependencies

```kotlin
// app/build.gradle.kts — updated dependencies block
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))

    // ... existing AndroidX, Compose, Koin, DataStore deps
}
```

### 2.3 Build Metrics to Collect

| Metric                                 | Before             | After     | Target           |
| -------------------------------------- | ------------------ | --------- | ---------------- |
| Clean build time                       | _measure_          | _measure_ | < +15%           |
| Incremental build (single file change) | _measure_          | _measure_ | < same or faster |
| `./gradlew :domain:test` time          | N/A (no isolation) | _measure_ | < 30s            |
| Module dependency graph depth          | 1                  | 3         | < 5              |

### 2.4 Decision Gate

**Proceed to Phase 3 (feature modules) if:**

- Incremental builds are measurably faster
- `:domain` tests run in isolation without compiling UI code
- Developer feedback is positive (no friction from module boundaries)

**Stop at 3 modules if:**

- Build times are the same or worse
- Module boundaries cause frequent import errors during development
- The overhead of managing 3+ `build.gradle.kts` files outweighs benefits

---

## Phase 3: Feature Modules (Conditional)

**Only proceed if Phase 2 metrics justify it.**

**Effort:** 3-5 days
**Risk:** Medium — feature boundaries are not yet clean

### 3.1 Candidate Feature Modules

| Module              | Source Packages                                                                                       | Estimated Files | Dependencies                       |
| ------------------- | ----------------------------------------------------------------------------------------------------- | --------------- | ---------------------------------- |
| `:feature:launcher` | `presentation/home/*`, `presentation/launcher/*`, `ui/screens/launcher/*`, `ui/components/launcher/*` | ~60             | `:domain`, `:core:common`, `:data` |
| `:feature:settings` | `presentation/settings/*`, `ui/screens/settings/*`, `ui/components/settings/*`                        | ~15             | `:domain`, `:core:common`          |
| `:feature:search`   | `presentation/search/*`, `ui/components/search/*`                                                     | ~20             | `:domain`, `:core:common`          |
| `:feature:drawer`   | `presentation/drawer/*`, relevant `ui/` files                                                         | ~10             | `:domain`, `:core:common`          |
| `:feature:widget`   | `presentation/home/widget/*`, `ui/components/launcher/widget/*`                                       | ~15             | `:domain`, `:core:common`          |

### 3.2 Prerequisites Before Phase 3

1. All Phase 0 boundary fixes must be complete
2. `:data` module must be extracted (see 3.3)
3. DI modules must be reorganized so each feature module owns its Koin definitions
4. Feature-to-feature imports must be prohibited (e.g., `:feature:search` cannot import from `:feature:launcher`)

### 3.3 `:data` Module (Required Before Feature Modules)

Feature modules need a shared data layer. Extract after Phase 1:

```kotlin
// data/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.milki.launcher.data"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    api(project(":domain"))
    api(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
}
```

**Contents:**

- `data/repository/*` — all repository implementations
- `data/cache/` — `AsyncSnapshotCache.kt` (after `SnapshotCache` moves to `:core:common`)
- `data/icon/` — icon loading infrastructure
- `data/widget/` — widget host management
- `data/search/` — search data providers
- `data/contextmenu/` — context menu cache

---

## What NOT to Do (Yet)

### Skip `:core:di`

**Reason:** Koin modules reference concrete implementations across all layers. `coreModule` imports `SettingsRepositoryImpl`, `AppRepositoryImpl`, `WidgetHostManager`, etc. Extracting DI into its own module creates a **dependency magnet** — every module would need to export its DI definitions to `:core:di`, and `:core:di` would need to depend on every module.

**When to reconsider:** Only after feature modules are stable and each feature owns its own `val featureModule = module { ... }` definition that is registered in `MainActivity` via `startKoin { modules(listOf(coreModule, homeModule, ...)) }`.

### Skip `:core:navigation`

**Reason:** The app has no formal navigation library (no Compose Navigation, no NavHost). Navigation is handled through `MainActivity` launching `SettingsActivity` and composable state management. There's nothing to extract yet.

### Skip `:data:persistence` Separation

**Reason:** DataStore and caching infrastructure is tightly coupled to repository implementations. Separating them adds a module boundary that doesn't correspond to a real architectural seam.

### Skip the Full 14-Module Split

**Reason:** The proposed split (`:core-common`, `:core-di`, `:core-model`, `:core-navigation`, `:feature-home`, `:feature-drawer`, `:feature-search`, `:feature-settings`, `:feature-widget`, `:data-repository`, `:data-persistence`, `:domain-repository`, `:domain-usecase`, `:baselineprofile`) would:

- Add 12 new `build.gradle.kts` files to manage
- Require fixing all Phase 0 issues plus many more
- Likely slow down development for months with minimal build-time benefit at 240 files
- Create circular dependency risks between DI, data, and feature modules

---

## Dependency Graph (Target State After Phase 1)

```
:app
├── :domain
│   ├── :core:common
│   │   ├── :core:model
│   │   └── (AndroidX core-ktx)
│   └── :core:model
├── :core:common
│   └── :core:model
└── :core:model
```

**Rule:** No module may depend on `:app`. `:domain` may not depend on `:data` (which stays in `:app` during Phase 1).

---

## Verification Checklist

After each phase, verify:

### Phase 0

- [ ] `domain/` has zero `import com.milki.launcher.data.` references
- [ ] `ui/` has zero `import com.milki.launcher.data.` references
- [ ] `./gradlew :app:compileDebugKotlin` succeeds
- [ ] All existing tests pass

### Phase 1

- [ ] `:core:model` compiles independently (`./gradlew :core:model:assemble`)
- [ ] `:core:common` compiles independently
- [ ] `:domain` compiles independently
- [ ] `:app` compiles with module dependencies
- [ ] No circular dependencies (`./gradlew :app:dependencies` — no cycles)
- [ ] All existing tests pass

### Phase 2

- [ ] Build metrics collected and compared
- [ ] Go/no-go decision documented

---

## Risk Assessment

| Risk                                                 | Likelihood | Impact | Mitigation                                                                           |
| ---------------------------------------------------- | ---------- | ------ | ------------------------------------------------------------------------------------ |
| Phase 0 reveals more boundary leaks than expected    | Medium     | Low    | Each leak is a simple import redirect; scope creep is bounded                        |
| Module extraction breaks Koin DI                     | Medium     | Medium | Keep all DI in `:app` during Phase 1; only move pure types                           |
| Build times increase with more modules               | Low        | Medium | Phase 2 decision gate catches this before feature modules                            |
| `HomeItem` in `:core:model` needs data-layer imports | Low        | High   | Audit `HomeItem.kt` — it uses `@Serializable` and `@Immutable` only, should be clean |
| Feature modules have too much shared UI code         | Medium     | Medium | Defer feature modules until shared UI components are identified                      |

---

## Timeline Estimate

| Phase                         | Duration               | Deliverable                                                          |
| ----------------------------- | ---------------------- | -------------------------------------------------------------------- |
| Phase 0: Boundary cleanup     | 2-3 days               | Clean layer imports, no cross-layer violations                       |
| Phase 1: Foundational modules | 2-3 days               | `:core:model`, `:core:common`, `:domain` compile independently       |
| Phase 2: Measure & decide     | 1 day                  | Build metrics report, go/no-go decision                              |
| Phase 3: Feature modules      | 3-5 days (conditional) | `:feature:launcher`, `:feature:settings`, `:feature:search`, `:data` |
| **Total (Phases 0-2)**        | **5-7 days**           | 3-module architecture with verified build improvement                |
| **Total (all phases)**        | **8-12 days**          | Full feature modularization                                          |
