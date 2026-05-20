# Test Coverage Review

> Analysis of existing test quality, coverage gaps, testing strategy, and recommendations for improving test infrastructure.

---

---

## 6. Testing Recommendations

### 6.1 Dramatically Increase Test Coverage

**Current:** 1 unit test file (5 tests), 1 placeholder instrumented test.

**Recommendation:** Add tests for:

- **Domain layer:** All use cases, policies, engines (pure Kotlin, easiest to test)
- **Data layer:** Repository implementations with fakes/mocks
- **Presentation layer:** ViewModel state transitions
- **UI layer:** Compose UI tests for critical components

### 6.2 Add Test Infrastructure

Create test utilities:

```
app/src/test/
  com/milki/launcher/
    testutil/
      FakeHomeRepository.kt
      FakeAppRepository.kt
      TestDispatcherRule.kt
      MainDispatcherRule.kt
```

### 6.3 Add Snapshot Tests for Compose

Consider adding screenshot/snapshot tests for UI components to catch visual regressions.

---

## 1. Test Inventory

### 1.1 Unit Tests (27 files)

| Test File                                       | What It Tests                     |
| ----------------------------------------------- | --------------------------------- |
| `LauncherActivityIntentTest.kt`                 | Benchmark intent parsing          |
| `PermissionOutcomeResolverTest.kt`              | Permission state resolution       |
| `SettingsMutationStorePrefixConflictTest.kt`    | Prefix conflict detection         |
| `ContactsSearchProviderPermissionPromptTest.kt` | Contact search permission prompts |
| `FilesSearchProviderPermissionPromptTest.kt`    | File search permission prompts    |
| `GridReorderEngineDeterminismTest.kt`           | Grid reorder determinism          |
| `HomeModelWriterTest.kt`                        | Home model mutations              |
| `LauncherInteractionCatalogTest.kt`             | Interaction catalog               |
| `AppQueryRankerTest.kt`                         | App search ranking                |
| `WidgetLayoutPolicyTest.kt`                     | Widget layout policy              |
| `WidgetSpanPolicyTest.kt`                       | Widget span policy                |
| `WidgetTransformFrameTest.kt`                   | Widget transform frame            |
| `DrawerListAssemblerTest.kt`                    | Drawer list assembly              |
| `SurfaceStateCoordinatorTest.kt`                | Surface state coordination        |
| `SearchResultActionTest.kt`                     | Search result actions             |
| `AppItemContextMenuSupportTest.kt`              | Context menu support              |
| `AppDrawerOverlayKeyTest.kt`                    | Drawer overlay key handling       |
| `DraggablePinnedItemsGridLookupTest.kt`         | Grid lookup                       |
| `ExternalHomeDropActionTest.kt`                 | External drop actions             |
| `ExternalHomeDropPreviewTest.kt`                | External drop preview             |
| `HomeSurfaceInteractionControllerTest.kt`       | Surface interaction               |
| `InternalHomeDropActionTest.kt`                 | Internal drop actions             |
| `ItemActionMenuPlacementTest.kt`                | Menu placement                    |
| `FolderPopupLayoutTest.kt`                      | Folder popup layout               |
| `HomeBackgroundGesturePolicyTest.kt`            | Background gesture policy         |
| `LauncherActionsContractTest.kt`                | Actions contract                  |
| `LauncherSheetHostPolicyTest.kt`                | Sheet host policy                 |

### 1.2 Instrumented Tests (1 file)

| Test File                    | What It Tests                   |
| ---------------------------- | ------------------------------- |
| `ExampleInstrumentedTest.kt` | Default template test (useless) |

---

## 2. Coverage Analysis

### 2.1 Coverage by Layer

| Layer                  | Files | Tested | Coverage %      |
| ---------------------- | ----- | ------ | --------------- |
| **core/**              | ~15   | 3      | ~20%            |
| **data/cache/**        | 2     | 0      | 0%              |
| **data/contextmenu/**  | 1     | 1      | 100%            |
| **data/icon/**         | 4     | 0      | 0%              |
| **data/repository/**   | ~15   | 2      | ~13%            |
| **data/search/**       | 3     | 2      | ~67%            |
| **data/widget/**       | 4     | 0      | 0%              |
| **domain/drag/**       | ~10   | 2      | ~20%            |
| **domain/homegraph/**  | 3     | 1      | ~33%            |
| **domain/model/**      | ~15   | 1      | ~7%             |
| **domain/repository/** | 8     | 0      | 0% (interfaces) |
| **domain/search/**     | 7     | 2      | ~29%            |
| **domain/widget/**     | 3     | 3      | 100%            |
| **presentation/**      | ~15   | 4      | ~27%            |
| **ui/components/**     | ~30   | 10     | ~33%            |
| **ui/interaction/**    | ~7    | 1      | ~14%            |
| **ui/screens/**        | ~5    | 1      | ~20%            |
| **ui/theme/**          | 4     | 0      | 0%              |

### 2.2 Critical Untested Files

These files handle critical functionality but have **zero test coverage**:

| File                               | Functionality                                | Risk   |
| ---------------------------------- | -------------------------------------------- | ------ |
| `UrlValidator.kt`                  | URL detection and validation                 | HIGH   |
| `MimeTypeUtil.kt`                  | MIME type resolution for file icons          | MEDIUM |
| `PinnedFileAvailability.kt`        | File availability checks (pruning)           | HIGH   |
| `AppIconMemoryCache.kt`            | Three-tier icon cache with telemetry         | HIGH   |
| `AppIconDiskSnapshotStore.kt`      | Disk caching for app icons                   | HIGH   |
| `ShortcutIconLoader.kt`            | Shortcut icon loading                        | MEDIUM |
| `WidgetHostManager.kt`             | Widget lifecycle management                  | HIGH   |
| `WidgetPickerCatalogStore.kt`      | Widget catalog caching                       | MEDIUM |
| `UrlHandlerResolver.kt`            | URL handler resolution and browser detection | HIGH   |
| `SuggestionResolver.kt`            | Clipboard suggestion logic                   | MEDIUM |
| `HomeGridOccupancyPolicy.kt`       | Grid placement algorithm                     | HIGH   |
| `HomeSnapshotStore.kt`             | DataStore transactions                       | HIGH   |
| `HomeItemSerializer.kt`            | Home item serialization                      | HIGH   |
| `ContactsQueryLayer.kt`            | ContentResolver queries                      | HIGH   |
| `FilesRepositoryImpl.kt`           | MediaStore queries                           | HIGH   |
| `LauncherBackupRepositoryImpl.kt`  | Backup/import logic                          | HIGH   |
| `SettingsRepositoryImpl.kt`        | Settings persistence                         | HIGH   |
| `RecentAppsStore.kt`               | Recent app tracking                          | MEDIUM |
| `InstalledAppsCatalog.kt`          | Installed app enumeration                    | HIGH   |
| `PackageChangeMonitor.kt`          | Package change detection                     | HIGH   |
| `PermissionHandler.kt`             | Permission state machine                     | HIGH   |
| `ConfigurableUrlSearchProvider.kt` | URL-based search                             | MEDIUM |
| `FilterAppsUseCase.kt`             | App filtering                                | MEDIUM |

---

## 3. Existing Test Quality

### 3.2 Weaknesses

| Test                                            | Issue                                                                       |
| ----------------------------------------------- | --------------------------------------------------------------------------- |
| `SettingsMutationStorePrefixConflictTest.kt`    | Only tests prefix conflicts; missing add/update/delete/enable/disable flows |
| `ContactsSearchProviderPermissionPromptTest.kt` | Only tests permission prompts; missing actual search logic                  |
| `FilesSearchProviderPermissionPromptTest.kt`    | Same as above                                                               |
| `ExampleInstrumentedTest.kt`                    | Default template — provides zero value                                      |

### 3.3 Missing Test Categories

| Category                      | Status                                  |
| ----------------------------- | --------------------------------------- |
| Unit tests for data layer     | Mostly missing                          |
| Unit tests for core utilities | Mostly missing                          |
| Integration tests             | None                                    |
| UI/instrumented tests         | None (only template)                    |
| Performance tests             | None (separate benchmark module exists) |
| Screenshot tests              | None                                    |

---

## 4. Testing Infrastructure

### 4.1 Current Setup

| Aspect                 | Status                                      |
| ---------------------- | ------------------------------------------- |
| Test framework         | JUnit 4 (`libs.junit`)                      |
| Android test runner    | `androidx.test.runner.AndroidJUnitRunner`   |
| Compose testing        | `androidx.compose.ui.test.junit4` available |
| Mocking                | No mocking library declared                 |
| Coroutines testing     | `kotlinx-coroutines-test` not declared      |
| Turbine (Flow testing) | Not declared                                |

### 4.2 Missing Dependencies

| Dependency                  | Purpose                | Recommendation               |
| --------------------------- | ---------------------- | ---------------------------- |
| `kotlinx-coroutines-test`   | Testing coroutines     | ADD                          |
| `turbine`                   | Testing Flow emissions | ADD                          |
| `mockk` or `mockito-kotlin` | Mocking                | ADD                          |
| `robolectric`               | JVM Android tests      | CONSIDER                     |
| `compose-test-junit4`       | Compose UI testing     | Already available but unused |

### 4.3 CI Integration

**File:** `.github/workflows/ci-android.yml`

```yaml
- name: Run unit tests
  run: ./gradlew :app:testDebugUnitTest --console=plain
```

Unit tests run in CI, but:

- No test coverage reporting (Jacoco not configured)
- No instrumentation tests in CI
- No test failure notifications

---

## 5. Testing Strategy Recommendations

### 5.1 Priority 1: Critical Path Tests

Test these files first — they handle core functionality:

1. `HomeItemSerializer.kt` — serialization correctness
2. `HomeSnapshotStore.kt` — DataStore transactions
3. `HomeGridOccupancyPolicy.kt` — grid placement algorithm
4. `UrlValidator.kt` — URL detection
5. `WidgetHostManager.kt` — widget lifecycle
6. `PermissionHandler.kt` — permission state machine
7. `AppIconMemoryCache.kt` — cache behavior
8. `SettingsMutationStore.kt` — complete CRUD coverage

### 5.2 Priority 2: Data Layer Tests

1. `FilesRepositoryImpl.kt` — MediaStore queries (use Robolectric)
2. `ContactsQueryLayer.kt` — ContentResolver queries
3. `InstalledAppsCatalog.kt` — app enumeration
4. `RecentAppsStore.kt` — recent tracking
5. `LauncherBackupRepositoryImpl.kt` — backup/import

### 5.3 Priority 3: UI Tests

1. `PinnedItem` — rendering different item types
2. `ItemActionMenu` — menu actions
3. `LauncherScreen` — home screen layout
4. `AppSearchDialog` — search UI
5. `DraggablePinnedItemsGrid` — drag-and-drop interactions

### 5.4 Recommended Test Dependencies

```toml
[versions]
coroutinesTest = "1.10.2"
turbine = "1.2.0"
mockk = "1.14.5"

[libraries]
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
```

### 5.5 Jacoco Coverage Configuration

Add to `app/build.gradle.kts`:

```kotlin
plugins {
    id("jacoco")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    classDirectories.setFrom(fileTree("build/tmp/kotlin-classes/debug") {
        exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*")
    })
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}
```

---

## 6. Priority Summary

| Priority | Finding                                    | Impact                         |
| -------- | ------------------------------------------ | ------------------------------ |
| P0       | 40+ critical files with zero test coverage | Reliability                    |
| P0       | No instrumentation tests (only template)   | UI regression risk             |
| P1       | Missing coroutines testing library         | Cannot test async code         |
| P1       | Missing mocking library                    | Hard to test with dependencies |
| P1       | No Jacoco coverage reporting               | No coverage visibility         |
| P2       | Existing tests are narrow in scope         | Incomplete coverage            |
| P2       | No screenshot/visual regression tests      | UI regression risk             |
| P3       | No Robolectric for JVM Android tests       | Slow feedback loop             |
