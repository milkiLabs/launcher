# Performance and Reliability Audit

## P0-P1 Findings

### 1) 

### 2) 
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

### 5)

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
