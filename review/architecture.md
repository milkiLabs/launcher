# Architecture & Design Review Report

**Project:** Milki Launcher  
**Date:** 2026-02-25  
**Reviewer:** Architecture Audit

---

## Executive Summary

The Milki Launcher codebase demonstrates a generally solid architecture with good separation of concerns, proper use of MVVM pattern, and clean dependency injection with Koin. However, there are several architecture issues, anti-patterns, and areas for improvement identified below.

---

## 1. CRITICAL ISSUES

### 1.1 ActionExecutor Creates Independent CoroutineScope (Memory Leak Risk)

**File:** `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt:50`

**Severity:** CRITICAL

**Problem:**

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

ActionExecutor creates its own `CoroutineScope` with a `SupervisorJob()` that is never cancelled. This creates a potential memory leak because:

1. The scope lives independently of any lifecycle
2. If the Activity is destroyed while a coroutine is running, the coroutine continues
3. The scope holds a reference to the containing class

**Suggested Fix:**
Pass a `CoroutineScope` or `LifecycleScope` from the Activity/ViewModel:

```kotlin
class ActionExecutor(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val homeRepository: HomeRepository,
    private val scope: CoroutineScope // Inject from Activity/ViewModel
) {
    // Use injected scope instead of creating own
}
```

Or use `lifecycleScope` from the Activity when creating ActionExecutor.

---

### 1.2 MainActivity Holds Direct Repository References (Layer Violation)

**File:** `app/src/main/java/com/milki/launcher/MainActivity.kt:71-77`

**Severity:** HIGH

**Problem:**

```kotlin
private val contactsRepository: ContactsRepository by inject()
private val homeRepository: HomeRepository by inject()
```

The Activity directly injects repositories and passes them to `ActionExecutor`. This violates the MVVM pattern where Activities should only communicate with ViewModels, not directly with the data layer.

**Suggested Fix:**
Move action execution logic into a ViewModel or UseCase, or have ActionExecutor receive only what it needs (Context) and use the action handler pattern more fully:

```kotlin
// ActionExecutor should receive callbacks, not repositories
class ActionExecutor(
    private val context: Context,
    private val onPinItem: suspend (HomeItem) -> Unit,
    private val onSaveRecentContact: suspend (String) -> Unit
)
```

---

### 1.3 ActionExecutor Has Mutable Callback Properties (Unidiomatic)

**File:** `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt:55-57`

**Severity:** HIGH

**Problem:**

```kotlin
var onRequestPermission: ((String) -> Unit)? = null
var onCloseSearch: (() -> Unit)? = null
var onSaveRecentApp: ((String) -> Unit)? = null
```

Using mutable `var` properties for callbacks is fragile and error-prone. Callbacks can be null unexpectedly, and setting them requires external coordination.

**Suggested Fix:**
Pass callbacks through constructor:

```kotlin
class ActionExecutor(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val homeRepository: HomeRepository,
    private val onRequestPermission: (String) -> Unit,
    private val onCloseSearch: () -> Unit,
    private val onSaveRecentApp: (String) -> Unit
)
```

---

## 2. HIGH SEVERITY ISSUES

### 2.1 God Class: SearchViewModel (Too Many Responsibilities)

**File:** `app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt`

**Severity:** HIGH

**Problem:**
SearchViewModel handles multiple concerns:

1. Search state management (lines 68-73)
2. Installed apps loading (lines 115-120)
3. Recent apps observation (lines 126-133)
4. Permission state management (lines 216-241)
5. Search logic execution (lines 315-356)
6. URL detection logic (lines 389-466)
7. Recent app/contact saving (lines 253-269)

At 479 lines, this ViewModel is doing too much.

**Suggested Fix:**
Extract responsibilities into separate classes:

- `UrlDetector` class for URL detection logic (lines 389-466)
- `SearchOrchestrator` for search execution
- Consider splitting permission management into a separate coordinator

---

---

### 2.4 PermissionHandler Tightly Coupled to SearchViewModel

**File:** `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:44`

**Severity:** HIGH

**Problem:**

```kotlin
class PermissionHandler(
    private val activity: ComponentActivity,
    private val searchViewModel: SearchViewModel
)
```

PermissionHandler is tightly coupled to SearchViewModel, making it:

1. Not reusable for other permission scenarios
2. Hard to test in isolation
3. Violates single responsibility (handles both permission logic AND ViewModel updates)

**Suggested Fix:**
Use callbacks or a more generic interface:

```kotlin
class PermissionHandler(
    private val activity: ComponentActivity,
    private val onPermissionsResult: (permission: String, granted: Boolean) -> Unit
)
```

---

## 3. MEDIUM SEVERITY ISSUES

### 3.1 ContactsRepositoryImpl is Too Large (350+ lines)

**File:** `app/src/main/java/com/milki/launcher/data/repository/ContactsRepositoryImpl.kt`

**Severity:** MEDIUM

**Problem:**
At 651 lines, this repository handles:

- Permission checking
- Contact searching with complex SQL
- Recent contacts storage
- Batch contact lookups
- Contact building helpers

**Suggested Fix:**
Split into:

- `ContactsQueryService` - handles contact searching
- `RecentContactsStorage` - handles recent contacts persistence
- Keep `ContactsRepositoryImpl` as a coordinator

---

### 3.2 Multiple DataStore Instances Scattered Across Files

**Files:**

- `AppRepositoryImpl.kt:116` - `launcher_prefs`
- `ContactsRepositoryImpl.kt:39` - `recent_contacts`
- `HomeRepositoryImpl.kt:39` - `home_items`
- `SettingsRepositoryImpl.kt:36` - `launcher_settings`

**Severity:** MEDIUM

**Problem:**
Having 4 separate DataStore files increases complexity and potential for inconsistent state. Each repository manages its own DataStore independently.

**Suggested Fix:**
Consider consolidating related data or creating a centralized `DataStoreManager` to coordinate all preferences.

---

### 3.3 Hardcoded Magic Numbers

**File:** `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt:170`

**Severity:** MEDIUM

**Problem:**

```kotlin
private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
```

The number 8 is a magic number without clear documentation of why 8 was chosen.

**Suggested Fix:**
Extract to a named constant with documentation:

```kotlin
companion object {
    // Limit parallel app loading to typical mobile CPU core count
    // to prevent memory spikes when loading 150+ apps
    private const val MAX_PARALLEL_APP_LOADS = 8
}
```

---

### 3.4 Hardcoded Grid Configuration

**File:** `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:222`

**Severity:** MEDIUM

**Problem:**

```kotlin
val newRow = index / 4 // Assuming 4 columns
```

Grid column count is hardcoded in multiple places (4 columns assumption).

**Suggested Fix:**
Define grid configuration in a central place:

```kotlin
object GridConfig {
    const val COLUMNS = 4
}
```

---

### 3.5 LauncherScreen Contains Business Logic

**File:** `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:155-213`

**Severity:** MEDIUM

**Problem:**
The `openPinnedItem`, `openPinnedApp`, `openPinnedFile`, and `openAppShortcut` functions contain business logic (Intent creation, error handling) that belongs in a ViewModel or UseCase.

**Suggested Fix:**
Move these functions to a ViewModel or create a `PinnedItemOpener` UseCase.

---

### 3.6 SettingsScreen Has Excessive Parameters

**File:** `app/src/main/java/com/milki/launcher/ui/screens/SettingsScreen.kt:58-77`

**Severity:** MEDIUM

**Problem:**
SettingsScreen has 20 parameters, making it:

1. Hard to read and maintain
2. Error-prone when reordering parameters
3. Difficult to test

**Suggested Fix:**
Use a state hoisting pattern or pass a single `SettingsCallbacks` interface:

```kotlin
data class SettingsCallbacks(
    val onSetMaxSearchResults: (Int) -> Unit,
    val onSetAutoFocusKeyboard: (Boolean) -> Unit,
    // ... other callbacks
)

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    callbacks: SettingsCallbacks,
    onNavigateBack: () -> Unit
)
```

---

### 3.7 SearchProvider Interface Returns Generic SearchResult

**File:** `app/src/main/java/com/milki/launcher/domain/repository/SearchProvider.kt:72`

**Severity:** MEDIUM

**Problem:**
The `search()` function returns `List<SearchResult>` which is a sealed class with multiple subtypes. This forces providers to know about all result types, potentially violating ISP (Interface Segregation Principle).

**Suggested Fix:**
Consider a more type-safe approach where each provider returns its specific result type, or use a visitor pattern for result handling.

---

## 4. LOW SEVERITY ISSUES

### 4.1 TODO Comments in Production Code

**File:** `app/src/main/java/com/milki/launcher/domain/repository/AppRepository.kt:32`

**Severity:** LOW

**Problem:**

```kotlin
// TODO: is this actually needed?
```

A TODO comment exists in production code questioning the necessity of `getRecentApps()`.

**Suggested Fix:**
Resolve the TODO - either remove the method if not needed, or document why it's needed.

---

### 4.2 TODO Comment for Unimplemented Feature

**File:** `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:203`

**Severity:** LOW

**Problem:**

```kotlin
// TODO: Implement using LauncherApps.pinShortcut() API
private fun openAppShortcut(item: HomeItem.AppShortcut, context: Context) {
```

The AppShortcut feature is partially implemented but uses a fallback.

**Suggested Fix:**
Complete the implementation or mark as a known limitation.

---

### 4.3 Inconsistent Error Handling

**Files:**

- `FilesRepositoryImpl.kt` - Returns empty list on error
- `ContactsRepositoryImpl.kt` - Throws SecurityException on permission denied
- `HomeRepositoryImpl.kt` - Silently handles IOException

**Severity:** LOW

**Problem:**
Error handling strategy is inconsistent across repositories. Some throw exceptions, some return empty results.

**Suggested Fix:**
Define a consistent error handling strategy:

- Use `Result<T>` wrapper for operations that can fail
- Or define domain-specific exceptions

---

### 4.4 Inline Comments Are Very Verbose

**Multiple Files**

**Severity:** LOW (Style Issue)

**Problem:**
While documentation is good, some inline comments are excessively verbose (e.g., `LauncherApplication.kt` has 153 lines with extensive comments). This can reduce code readability.

**Note:** The AGENTS.md instructions specify "write down extensive documentation and very detailed incode comments" for educational purposes, so this is intentional.

---

### 4.5 AppIconFetcher.Factory Could Be Singleton

**File:** `app/src/main/java/com/milki/launcher/AppIconFetcher.kt:119`

**Severity:** LOW

**Problem:**
`AppIconFetcher.Factory` is a stateless class that could be a singleton/object instead of a class.

**Suggested Fix:**

```kotlin
object Factory : Fetcher.Factory<AppIconRequest> {
    override fun create(...)
}
```

---

## 5. ARCHITECTURAL POSITIVES

Despite the issues above, the codebase has several architectural strengths:

1. **Clean MVVM Pattern:** ViewModels properly separate UI from business logic
2. **Repository Pattern:** Clean separation between data sources and domain
3. **Dependency Injection:** Koin is used properly with constructor injection
4. **Sealed Classes:** Good use of sealed classes for state and actions
5. **Flow-based Reactive Programming:** Proper use of StateFlow and Flow
6. **CompositionLocal for Action Handling:** Eliminates prop drilling
7. **Coroutines Usage:** Proper suspend functions and structured concurrency (mostly)
8. **Clean Package Structure:** Domain, data, presentation layers are separated

---

## 6. RECOMMENDATIONS SUMMARY

| Priority | Issue                               | Action                              |
| -------- | ----------------------------------- | ----------------------------------- |
| Critical | ActionExecutor coroutine scope leak | Inject scope or use lifecycleScope  |
| Critical | MainActivity repository references  | Move to ViewModel layer             |
| High     | ActionExecutor mutable callbacks    | Constructor injection               |
| High     | SearchViewModel too large           | Extract URL detection, orchestrator |
| High     | Duplicate file/app opening logic    | Create shared utilities             |
| High     | PermissionHandler coupling          | Use callbacks instead of ViewModel  |
| Medium   | Multiple DataStore instances        | Consider consolidation              |
| Medium   | Hardcoded grid columns              | Extract to configuration            |
| Medium   | SettingsScreen parameters           | Use callback interface              |
| Low      | TODO comments                       | Resolve or document                 |

---

## 7. FILES REVIEWED

All 64 Kotlin source files were reviewed including:

- Activities: MainActivity, SettingsActivity
- Application: LauncherApplication
- ViewModels: SearchViewModel, HomeViewModel, SettingsViewModel
- Repositories: AppRepositoryImpl, ContactsRepositoryImpl, FilesRepositoryImpl, HomeRepositoryImpl, SettingsRepositoryImpl
- Domain: Models, UseCases, Interfaces
- UI: Screens, Components, Theme
- Utilities: PermissionUtil, MimeTypeUtil
- DI: AppModule

---

_End of Report_
