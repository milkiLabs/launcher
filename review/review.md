# Milki Launcher - Codebase Review & Recommendations

## 2. Structural & Architectural Recommendations

### 2.3 Introduce a Navigation Layer

**Current State:** No dedicated navigation package. Activities (`MainActivity`, `SettingsActivity`) handle routing manually.

**Recommendation:** Create `:core-navigation` or `app.navigation.*` package with:

- A sealed class/interface for all navigation destinations
- A `Navigator` interface abstracting navigation logic
- Consider Compose Navigation if not already using it

---

### 3.5 Separate `ui/components/` from `ui/screens/` More Clearly

**Current:** Good separation exists, but some components in `ui/components/launcher/` are large enough to be screens.

**Recommendation:** Establish a clear rule:

- **Screens:** Full-screen composables that are navigation destinations
- **Components:** Reusable UI elements used within screens
- If a component exceeds ~300 lines, consider breaking it into smaller sub-components

---

## 5. Architecture Pattern Recommendations

### 5.1 Introduce MVI for Complex Features

**Current:** MVVM with callback-based actions (`LauncherActions` data class).

**Recommendation:** For the home screen feature (the most complex), consider adopting **MVI (Model-View-Intent)**:

- Define sealed `HomeIntent` classes for all user actions
- Define sealed `HomeEffect` classes for one-time effects
- Single `HomeState` data class
- Reducer function to produce new state from intent + current state

This makes state transitions explicit and testable.

### 5.2 Consolidate Wide Repository Implementations

**Current:** `SettingsRepositoryImpl` implements 4 interfaces:

- `SettingsReader`
- `SearchSourceRepository`
- `PrefixConfigurationRepository`
- `HomeTriggerRepository`

**Issue:** This is a "God class" implementing multiple contracts.

**Recommendation:** Split into focused implementations:

```kotlin
class SettingsDataStore : SettingsReader
class SearchSourceDataStore : SearchSourceRepository
class PrefixDataStore : PrefixConfigurationRepository
class HomeTriggerDataStore : HomeTriggerRepository
```

Or use composition with a single class delegating to focused collaborators.

### 5.3 Add Error Handling Strategy

**Current:** No visible centralized error handling pattern.

**Recommendation:** Introduce:

- `Result<T>` wrapper (or use Kotlin's built-in `Result`)
- Sealed error classes per feature domain
- Error state in UI state classes
- Global error handler for uncaught exceptions

### 5.4 Consider Use Case Layer Expansion

**Current:** Only one use case exists (`FilterAppsUseCase`).

**Recommendation:** Extract more business logic into use cases:

- `LoadHomeGridUseCase`
- `ReorderHomeItemUseCase`
- `SearchAppsUseCase`
- `SaveSettingsUseCase`

This makes business logic testable in isolation and reusable across ViewModels.

---

## 7. Code Quality & Conventions

### 7.2 Address Inconsistent Indentation

**Issue:** Some files (e.g., `HomeItem.kt`) have mixed indentation.

**Recommendation:** Run formatter across the codebase and add CI check.

---

## 8. Dependency & Build Recommendations

### 8.1 Add Build Config Fields for Feature Flags

**Recommendation:** Use `buildConfigField` for:

- Debug-only features
- A/B testing flags
- Performance tracing toggles

### 8.2 Consider Hilt Instead of Koin

**Current:** Koin 4.2.0

**Recommendation:** Evaluate migrating to Hilt for:

- Compile-time dependency validation
- Better Android lifecycle integration
- Standard Jetpack ecosystem alignment

This is optional—Koin works well, but Hilt provides compile-time safety.

### 8.3 Add Compose Compiler Metrics

**Recommendation:** Enable Compose compiler metrics reports to identify:

- Unstable types causing recomposition
- Skippable vs non-skippable composables
- Optimization opportunities
