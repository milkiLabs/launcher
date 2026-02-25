# Deprecated APIs, Dead Code, and Technical Debt Audit

**Generated:** 2026-02-25  
**Project:** Milki Launcher  
**Scope:** All Kotlin source files and Gradle build files

---

## Summary

| Category | Count | Severity Distribution |
|----------|-------|----------------------|
| Deprecated Android APIs | 4 | High: 2, Medium: 2 |
| TODO/FIXME Comments | 5 | Low: 5 |
| Deprecated Methods (Self-Documented) | 3 | Medium: 3 |
| Potentially Outdated Dependencies | 1 | Low: 1 |
| Unused Code | 1 | Low: 1 |

---

## 1. Deprecated Android APIs

### 1.1 PackageManager.queryIntentActivities() - API 33+

**Severity: HIGH**

**Files Affected:**
- `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt:50-59`
- `app/src/main/java/com/milki/launcher/domain/search/UrlHandlerResolver.kt:166-174`
- `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt:147-148`

**Issue:**
The `queryIntentActivities(intent, 0)` method with Int flags was deprecated in Android 13 (API 33). The new API requires `ResolveInfoFlags`.

**Current Code (AppRepositoryImpl.kt):**
```kotlin
@Suppress("DEPRECATION")
queryIntentActivities(intent, 0)
```

**Status:** Properly handled with extension functions in `AppRepositoryImpl.kt` (`queryIntentActivitiesCompat`) and `UrlHandlerResolver.kt` with version checks. However, `ActionExecutor.kt:147-148` uses the deprecated API directly without proper version check.

**Suggested Fix for ActionExecutor.kt:**
```kotlin
// Import the compat extension from AppRepositoryImpl or create a local one
val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
} else {
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(intent, 0)
}
```

---

### 1.2 PackageManager.resolveActivity() - API 33+

**Severity: HIGH**

**File Affected:**
- `app/src/main/java/com/milki/launcher/domain/search/UrlHandlerResolver.kt:118-126`

**Issue:**
The `resolveActivity(intent, flags)` method with Int flags was deprecated in API 33.

**Current Code:**
```kotlin
val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    packageManager.resolveActivity(
        intent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
    )
} else {
    @Suppress("DEPRECATION")
    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
}
```

**Status:** Properly handled with version check and `@Suppress` annotation.

---

### 1.3 PackageManager.getApplicationInfo() - API 33+

**Severity: MEDIUM**

**File Affected:**
- `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt:76-86`

**Issue:**
The `getApplicationInfo(packageName, flags)` method with Int flags was deprecated in API 33.

**Current Code:**
```kotlin
@Throws(PackageManager.NameNotFoundException::class)
fun PackageManager.getApplicationInfoCompat(packageName: String): ApplicationInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }
}
```

**Status:** Properly handled with extension function and version check.

---

### 1.4 READ_EXTERNAL_STORAGE Permission - Android 11+

**Severity: MEDIUM**

**Files Affected:**
- `app/src/main/java/com/milki/launcher/util/PermissionUtil.kt:74-83`
- `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:279-330`

**Issue:**
`READ_EXTERNAL_STORAGE` is effectively deprecated on Android 11+ (API 30+) in favor of `MANAGE_EXTERNAL_STORAGE` for broad file access.

**Current Code:**
Properly handled with version checks that use `MANAGE_EXTERNAL_STORAGE` on API 30+ and `READ_EXTERNAL_STORAGE` on older versions.

**Status:** Properly handled.

---

## 2. TODO/FIXME Comments (Incomplete Work)

### 2.1 TODO: LauncherApps.pinShortcut() Implementation

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:202`

```kotlin
* TODO: Implement using LauncherApps.pinShortcut() API
```

**Context:** The `openAppShortcut` function currently falls back to opening the parent app instead of the actual shortcut.

**Suggested Fix:** Implement proper shortcut launching using `LauncherApps` API.

---

### 2.2 TODO: Load Actual Shortcut Icons

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/ui/components/PinnedItem.kt:422`

```kotlin
* TODO: Load actual shortcut icons using LauncherApps.getShortcutIcon()
```

**Context:** The `ShortcutIcon` composable currently shows the parent app's icon instead of the shortcut-specific icon.

**Suggested Fix:** Use `LauncherApps.getShortcutIconDrawable()` to load shortcut-specific icons.

---

### 2.3 TODO: Secondary Browser Action for URLs

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/ui/components/SearchResultItems.kt:257`

```kotlin
* TODO: Add secondary action for "Open in Browser" when there's a handler app.
```

**Context:** When a URL has a specific handler app (e.g., YouTube for youtube.com), there's no option to explicitly open in browser.

**Suggested Fix:** Extend `SearchResultListItem` to support secondary trailing actions or use a different layout for URL results.

---

### 2.4 TODO: Permanently Denied Permission Detection

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt:250`

```kotlin
* TODO: Add logic to detect "permanently denied" and guide user to Settings
```

**Context:** When user denies permission with "Don't ask again", the dialog won't show again but there's no guidance to Settings.

**Suggested Fix:** Use `ActivityCompat.shouldShowRequestPermissionRationale()` to detect permanently denied permissions and show a dialog directing users to Settings.

---

### 2.5 TODO: Question About getRecentApps Necessity

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/domain/repository/AppRepository.kt:32`

```kotlin
* TODO: is this actually needed?
```

**Context:** Developer question about whether the Flow-based `getRecentApps()` is necessary.

**Status:** This is used extensively in `SearchViewModel` and `FilterAppsUseCase`, so it is needed. The TODO should be removed.

---

## 3. Deprecated Methods (Self-Documented)

### 3.1 HomeViewModel.reorderItems()

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:90-96`

```kotlin
/**
 * @deprecated Use moveItemToPosition instead for grid-based positioning
 */
fun reorderItems(fromIndex: Int, toIndex: Int) {
    viewModelScope.launch {
        homeRepository.reorderPinnedItems(fromIndex, toIndex)
    }
}
```

**Issue:** This method is deprecated but still exposed. It's not being used anywhere in the codebase (dead code).

**Suggested Fix:** Remove this method entirely since `moveItemToPosition` is the preferred approach.

---

### 3.2 HomeRepository.reorderPinnedItems()

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/domain/repository/HomeRepository.kt:76-78`

```kotlin
/**
 * @deprecated Use updateItemPosition instead for grid-based positioning
 */
suspend fun reorderPinnedItems(fromIndex: Int, toIndex: Int)
```

**Issue:** This interface method is deprecated. It's implemented in `HomeRepositoryImpl` but only called from the deprecated `HomeViewModel.reorderItems()`.

**Suggested Fix:** Remove from both interface and implementation after removing `HomeViewModel.reorderItems()`.

---

### 3.3 HomeRepositoryImpl.reorderPinnedItems()

**Severity: MEDIUM**  
**File:** `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:204-228`

**Issue:** Implementation of deprecated method. Can be removed along with interface method.

---

## 4. Potentially Outdated Dependencies

### 4.1 Coil 2.7.0

**Severity: LOW**  
**File:** `gradle/libs.versions.toml:13`

```toml
coil = "2.7.0"
```

**Issue:** Coil 3.x has been released with significant improvements including:
- Kotlin 2.0 support
- Better Compose integration
- Improved memory management
- KMP support

**Current Version:** 2.7.0  
**Latest Stable:** 3.x

**Note:** This is not critical. Coil 2.7.0 is stable and works well. Migration to Coil 3 would require code changes due to API differences.

**Suggested Fix:** Consider upgrading to Coil 3.x when planning a larger refactoring effort. The main breaking change is the package restructure (`coil.compose` → `coil3.compose`).

---

## 5. Unused/Dead Code

### 5.1 HomeViewModel.reorderItems() - Unused Method

**Severity: LOW**  
**File:** `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:92-96`

**Issue:** This deprecated method is not called anywhere in the codebase.

**Search Results:**
- Not called in MainActivity
- Not called in any composables
- Replaced by `moveItemToPosition()`

**Suggested Fix:** Remove this method and its associated repository methods.

---

## 6. Unused Imports (Potential)

No obvious unused imports were found during the audit. The codebase appears to be well-maintained in this regard.

---

## 7. Commented-Out Code

No significant blocks of commented-out code were found. The codebase maintains clean version control practices.

---

## 8. Build Configuration Issues

### 8.1 Java Version Compatibility

**File:** `app/build.gradle.kts:199-204`

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

**Note:** While not deprecated, consider using `JavaVersion.VERSION_17` for better compatibility with newer Android libraries and Kotlin features. AGP 9.0 recommends Java 17.

---

## 9. Action Items Summary

### Immediate (High Priority)
1. Fix `ActionExecutor.kt:147-148` to use version-checked `queryIntentActivities` API

### Short Term (Medium Priority)
1. Remove deprecated `reorderItems()` from `HomeViewModel`
2. Remove deprecated `reorderPinnedItems()` from `HomeRepository` interface and `HomeRepositoryImpl`
3. Remove the TODO comment in `AppRepository.kt:32` since `getRecentApps()` is being used

### Long Term (Low Priority)
1. Implement proper shortcut icon loading using LauncherApps API
2. Add permanently denied permission detection
3. Add secondary browser action for URL results
4. Consider upgrading to Coil 3.x
5. Consider upgrading Java compatibility to VERSION_17

---

## Appendix: Files Reviewed

All Kotlin source files (61 files) and Gradle build files (4 files) were reviewed:

### App Source Files
- MainActivity.kt, SettingsActivity.kt, LauncherApplication.kt, AppIconFetcher.kt
- All files in `ui/`, `presentation/`, `data/`, `domain/`, `handlers/`, `util/`, `di/` packages

### Build Files
- `app/build.gradle.kts`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle/libs.versions.toml`

### Test Files
- `ExampleUnitTest.kt`
- `ExampleInstrumentedTest.kt`
