# Performance and Reliability Audit

## P0-P1 Findings

### 1) Duplicate heavy app-list loading paths at startup
- Evidence: `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:123`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:144`, `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:167`
- Problem: Search loads installed apps both by one-shot `getInstalledApps()` and by collecting `observeInstalledApps()`.
- Impact: Extra full `PackageManager` scan and icon preload work during cold start.
- Recommendation:
1. Keep only one startup path.
2. Prefer `observeInstalledApps()` with `stateIn` and a replayed shared stream.
3. If one-shot warm start is kept, gate it to avoid duplicate first emission.

### 2) Multiple collectors trigger repeated full app scans
- Evidence: `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt:167`, `app/src/main/java/com/milki/launcher/presentation/drawer/AppDrawerViewModel.kt:140`, `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt:300`, `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt:302`
- Problem: `observeInstalledApps()` currently recalculates via `mapLatest { getInstalledApps() }` per collector.
- Impact: Search and drawer can each trigger expensive app enumeration.
- Recommendation:
1. Convert repository stream to shared hot flow with `shareIn`/`stateIn` at repository scope.
2. Cache app-list snapshot + timestamp; refresh only on package signals.
3. Return immutable cached list to all consumers.

### 3) Oversized files increase regression and review cost
- Evidence:
- `app/src/main/java/com/milki/launcher/ui/components/DraggablePinnedItemsGrid.kt` (~1496 lines)
- `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt` (~1186 lines)
- `app/src/main/java/com/milki/launcher/ui/components/FolderPopupDialog.kt` (~988 lines)
- `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt` (~834 lines)
- `app/src/main/java/com/milki/launcher/data/repository/SettingsRepositoryImpl.kt` (~727 lines)
- Impact: Slower changes, higher defect probability, hard testability.
- Recommendation:
1. Split by responsibility, not by arbitrary line count.
2. Enforce per-file thresholds with exceptions for generated/model files.
3. Add folder-level readme for ownership and invariants.

### 4) Broad exception catches reduce diagnosability
- Evidence: `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt:174`, `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt:199`, `app/src/main/java/com/milki/launcher/data/widget/WidgetHostManager.kt:126`, `app/src/main/java/com/milki/launcher/data/repository/FilesRepositoryImpl.kt:143`
- Problem: Many `catch (Exception)` blocks collapse distinct error causes.
- Impact: Hard to triage production issues and misleading fallbacks.
- Recommendation:
1. Catch narrow exceptions (`ActivityNotFoundException`, `SecurityException`, `NameNotFoundException`, etc.).
2. Emit structured logs with action context and identifiers.
3. Keep user-friendly fallback but preserve real failure reason in logs/telemetry.

### 5) HomeRepository repeatedly deserializes full dataset for each operation
- Evidence: repeated `deserializeItems(preferences)` calls across operations, e.g. `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:169`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:325`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:764`
- Problem: Full parse/write for every mutation scales poorly with more home items/widgets/folders.
- Impact: Potential jank during burst operations and higher battery/CPU.
- Recommendation:
1. Move to typed Proto DataStore schema for structured updates.
2. Introduce in-memory snapshot + single-writer mutation queue.
3. Diff-based writes instead of full newline-JSON rewrite.

## Reliability Gaps

### 6) Permission flows still have dead ends
- Evidence: `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:264`
- Issue: No permanent-denial recovery path.
- Recommendation: Rationale + settings deep-link and explicit retry UX.

### 7) Test coverage is effectively placeholder-level
- Evidence: `app/src/test/java/com/milki/launcher/ExampleUnitTest.kt`, `app/src/androidTest/java/com/milki/launcher/ExampleInstrumentedTest.kt`
- Issue: Core policy/state logic lacks unit and integration tests.
- Recommendation:
1. Add tests for HomeButtonPolicy, widget state machine, folder mutation invariants, and search pipeline race behavior.
2. Add repository contract tests for folder cleanup and item uniqueness.
