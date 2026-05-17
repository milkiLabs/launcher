# P0 Critical Findings

> Issues that must be addressed immediately. These cause crashes, memory leaks, security vulnerabilities, or broken release builds.

---

## 1. Plaintext Keystore Passwords in Repository

**Severity:** CRITICAL
**File:** `keystore.properties:2-4`
**Category:** Security

```properties
storePassword=eEHP5%&a8uXJJy
keyPassword=eEHP5%&a8uXJJy
```

The release keystore password is stored in plaintext. Even though `.gitignore` lists `keystore.properties`, if this file was committed before the ignore rule, it remains in git history and is accessible to anyone with repo access.

### Impact

- Anyone with repo access can sign APKs as the app
- Malicious releases could be distributed
- Google Play signing could be compromised

### Required Actions

1. **Rotate the keystore immediately** — generate a new key
2. Run `git filter-repo` or BFG Repo-Cleaner to purge from history
3. Use GitHub Secrets only (the release workflows already do this correctly)
4. Add `keystore.properties` to `.gitignore` with a template `keystore.properties.example`

---

## 2. ProGuard/R8 Rules Completely Missing

**Severity:** CRITICAL
**File:** `app/proguard-rules.pro`
**Category:** Build Configuration

The file contains only default comments — zero actual keep rules. The app uses:

- `kotlinx.serialization` (extensive `@Serializable` annotations across 15+ files)
- Koin (reflection-based DI)
- Compose (custom composables)
- Device Admin receiver

### Impact

- **Release builds will crash** — R8 will strip serialization serializers, Koin modules, and reflected classes
- `isMinifyEnabled = true` in release build type means this affects all production APKs

### Required Rules (minimum)

```proguard
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.milki.launcher.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.milki.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class org.koin.** { *; }
-keepclassmembers class com.milki.launcher.core.di.** { *; }

# Device Admin
-keep class com.milki.launcher.core.deviceadmin.LauncherDeviceAdminReceiver { *; }

# DataStore
-keep class androidx.datastore.** { *; }
```

---

## 4. Thread Safety: `pendingWidgets` Accessed from Multiple Threads

**Severity:** HIGH
**File:** `HomeViewModel.kt:73, 556, 558`
**Category:** Concurrency Bug

```kotlin
private val pendingWidgets = linkedMapOf<Int, PendingWidget>()
```

`LinkedHashMap` is accessed from:

- `startWidgetPlacement()` — UI thread
- `handleWidgetBindResult()` — ActivityResultLauncher callback (UI thread)
- `persistPendingWidget()` — `viewModelScope.launch` (background thread at line 558)
- `cancelPendingWidget()` — both UI thread and background coroutine

### Impact

- `ConcurrentModificationException` possible
- Data corruption in widget placement state

### Fix

Use `Mutex` to guard access or convert to `MutableStateFlow<Map<Int, PendingWidget>>`:

```kotlin
private val pendingWidgetsMutex = Mutex()
// ...
pendingWidgetsMutex.withLock {
    pendingWidgets[appWidgetId] = pending
}
```

---

## 5. Structured Concurrency Broken: `runCatching` Swallows CancellationException

**Severity:** HIGH
**File:** `HomeViewModel.kt:158-163, 562-569`
**Category:** Concurrency Bug

```kotlin
val wasApplied = runCatching {
    applyWriterCommand(command, onApplied)
}.getOrElse { exception ->
    _lastMoveErrorMessage.value = exception.message ?: fallbackErrorMessage
    false
}
```

`runCatching` catches ALL `Throwable` including `CancellationException`. If the coroutine is cancelled, the cancellation is swallowed and treated as a regular error.

### Impact

- Coroutines cannot be properly cancelled
- ViewModel cleanup may hang
- Structured concurrency is violated

### Fix

```kotlin
val wasApplied = try {
    applyWriterCommand(command, onApplied)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    _lastMoveErrorMessage.value = e.message ?: fallbackErrorMessage
    false
}
```

---

## 7. Domain Layer Leaks Android Framework Types

**Severity:** HIGH
**Files:** `HomeItem.kt:42`, `LauncherBackupRepository.kt:8`
**Category:** Architecture Violation

`HomeItem.kt` imports `android.content.pm.ShortcutInfo`:

```kotlin
import android.content.pm.ShortcutInfo
// ...
fun fromShortcutInfo(info: ShortcutInfo): AppShortcut
```

`LauncherBackupRepository.kt` uses Android `Intent` in a domain interface:

```kotlin
typealias WidgetBindPermissionRequester = suspend (Intent) -> Boolean
```

### Impact

- Domain layer cannot be tested without Android framework
- Violates clean architecture layer boundaries
- Makes the codebase harder to port or reuse

### Fix

Move `ShortcutInfo` mapping to the data layer. Replace `Intent` callback with a domain-level abstraction.

---

## 8. coreModule Depends on Feature Modules (Violates Own Architecture Rules)

**Severity:** HIGH
**File:** `CoreModule.kt:132-141`
**Category:** Architecture Violation

The module comment at `CoreModule.kt:26` states: "coreModule must NEVER depend on any feature module." But:

```kotlin
single<LauncherBackupRepository> {
    LauncherBackupRepositoryImpl(
        homeRepository = get(),       // from homeModule
        widgetHostManager = get(),    // from widgetModule
        ...
    )
}
```

### Impact

- Dependency graph is inverted
- Makes modularization impossible
- Violates documented architecture rules

### Fix

Create a dedicated `backupModule` listed after all feature modules, or move `LauncherBackupRepository` to a feature-agnostic location.

---

## 9. SettingsRepository is a God Interface (27 Methods)

**Severity:** HIGH
**File:** `SettingsRepository.kt:29-236`
**Category:** Design / Maintainability

The interface has 27 methods covering: general settings, individual setters, search sources, prefix mutations, hidden apps, and backup operations.

### Impact

- Violates Interface Segregation Principle
- Every implementation must provide all 27 methods
- Hard to test, hard to mock
- Any change affects all consumers

### Fix

Split into focused interfaces:

- `SettingsRepository` (core settings read/write)
- `SearchSourceRepository` (CRUD for search sources)
- `PrefixConfigurationRepository` (prefix mutations)
- `HiddenAppsRepository` (hidden apps management)

---

## 11. Presentation Layer Imports Data-Layer Implementation

**Severity:** HIGH
**File:** `SearchViewModelSettingsAdapter.kt:3`
**Category:** Layer Coupling

```kotlin
import com.milki.launcher.data.search.ConfigurableUrlSearchProvider
```

A presentation-layer class directly imports a data-layer implementation.

### Impact

- Tight coupling between presentation and data layers
- Changing `ConfigurableUrlSearchProvider` constructor breaks presentation code
- Violates dependency inversion principle

### Fix

Define a `SearchProviderFactory` interface in the domain layer.

---

## 12. Duplicate Release Workflows

**Severity:** MEDIUM
**Files:** `.github/workflows/android-release.yml`, `.github/workflows/release-android.yml`
**Category:** CI/CD

Both workflows are nearly identical — same name, same triggers, same steps.

### Impact

- Confusing for contributors
- Wasteful CI resources if both trigger
- Maintenance burden (changes must be applied twice)

### Fix

Delete one of the workflows.
