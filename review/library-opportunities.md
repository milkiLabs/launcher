# Library Opportunities Audit Report

**Project:** Milki Launcher  
**Date:** 2026-02-25  
**Auditor:** Code Review

## Executive Summary

This audit identifies manual implementations in the codebase that could benefit from external libraries or dependencies. The project already uses several modern Android libraries (Coil, Koin, DataStore, Jetpack Compose) but has areas where additional libraries could improve code quality, maintainability, and safety.

---

## Current Dependencies

The project already uses these modern libraries:

| Category | Library | Status |
|----------|---------|--------|
| Image Loading | Coil 3 | ✅ In use |
| Dependency Injection | Koin | ✅ In use |
| Data Persistence | DataStore Preferences | ✅ In use |
| UI | Jetpack Compose + Material 3 | ✅ In use |
| Architecture | ViewModel + Lifecycle | ✅ In use |

---

## Findings

### 1. Manual Serialization - HIGH PRIORITY

#### Location
- `app/src/main/java/com/milki/launcher/domain/model/HomeItem.kt` (lines 56-155, 178-252, 280-347)
- `app/src/main/java/com/milki/launcher/domain/model/GridPosition.kt` (lines 65-93)

#### Current Implementation
The codebase manually serializes `HomeItem` sealed class variants using pipe-delimited strings:

```kotlin
// HomeItem.kt:89-91
override fun toStorageString(): String {
    return "app|$packageName|$activityName|$label|${position.toStorageString()}"
}

// HomeItem.kt:126-155
fun fromStorageString(str: String): PinnedApp? {
    val parts = str.split("|")
    if (parts.isEmpty() || parts[0] != "app") return null
    
    if (parts.size == 4) {
        return PinnedApp(
            id = "app:${parts[1]}/${parts[2]}",
            packageName = parts[1],
            activityName = parts[2],
            label = parts[3],
            position = GridPosition.DEFAULT
        )
    }
    // ... more parsing logic
}
```

Similar manual parsing exists for:
- `PinnedFile` (lines 218-252)
- `AppShortcut` (lines 315-347)
- `GridPosition` (lines 81-93)

#### Problems with Current Approach
1. **Fragile parsing**: Pipe characters in data (e.g., app labels with `|`) will corrupt the serialization
2. **No type safety**: Manual string parsing is error-prone
3. **Maintenance burden**: Adding new fields requires updating serialization in multiple places
4. **No schema evolution**: Cannot easily add/remove fields without breaking existing data
5. **Testing overhead**: Manual serialization requires extensive testing

#### Recommended Library: kotlinx.serialization

```kotlin
// With kotlinx.serialization
@Serializable
sealed class HomeItem {
    abstract val id: String
    abstract val position: GridPosition
    
    @Serializable
    @SerialName("app")
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem()
    
    // ... other variants
}

// Serialization becomes trivial:
val json = Json.encodeToString(homeItem)
val item = Json.decodeFromString<HomeItem>(json)
```

#### Benefits
- **Type-safe serialization**: No manual string parsing
- **Schema evolution**: Can add/remove fields with defaults
- **Null safety**: Handles nullable fields properly
- **Compressed JSON**: More efficient than pipe-delimited strings
- **IDE support**: Auto-completion, refactoring support

#### Migration Complexity: **MEDIUM**
- Add kotlinx-serialization-json dependency
- Annotate data classes with @Serializable
- Update HomeRepositoryImpl to use JSON instead of manual parsing
- Add migration logic for existing stored data
- Estimated effort: 2-3 days

---

### 2. Manual URL Detection - MEDIUM PRIORITY

#### Location
`app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt` (lines 389-466)

#### Current Implementation
```kotlin
// SearchViewModel.kt:389-446
private fun detectUrl(query: String): UrlSearchResult? {
    // FAST-FAIL: Empty or space-containing queries aren't URLs
    if (trimmed.isEmpty() || trimmed.contains(" ")) {
        return null
    }
    
    // STAGE 1: Check for explicit URL prefixes
    val hasSchemePrefix = trimmed.startsWith("http://") || 
                          trimmed.startsWith("https://") || 
                          trimmed.startsWith("www.")
    
    if (hasSchemePrefix) {
        val urlToCheck = if (trimmed.startsWith("www.")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        if (Patterns.WEB_URL.matcher(urlToCheck).matches()) {
            finalUrl = urlToCheck
        }
    }
    
    // STAGE 2: Try Android's built-in WEB_URL pattern
    if (finalUrl == null && Patterns.WEB_URL.matcher(trimmed).matches()) {
        finalUrl = trimmed
    }
    
    // STAGE 3: Fallback regex for newer/regional TLDs
    if (finalUrl == null) {
        val fallbackUrlPattern = Regex(
            "^[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,}(?:/.*)?$"
        )
        if (fallbackUrlPattern.matches(trimmed)) {
            finalUrl = trimmed
        }
    }
    // ...
}
```

#### Problems with Current Approach
1. **Complex regex logic**: Multiple stages of URL detection
2. **Maintenance burden**: New TLDs may not be recognized
3. **No URL normalization**: Different URL formats treated differently
4. **Limited validation**: Doesn't check if URL is actually valid

#### Recommended Library: Apache Commons Validator or custom URL library

```kotlin
// Option 1: Apache Commons Validator
implementation("commons-validator:commons-validator:1.8")

// Option 2: Keep current but extract to dedicated UrlValidator utility class
```

#### Benefits
- **Battle-tested URL parsing**: Handles edge cases
- **Better TLD support**: Regularly updated
- **URL normalization**: Consistent URL handling
- **Single responsibility**: Separate validation from ViewModel

#### Migration Complexity: **LOW**
- Extract URL detection to dedicated utility class
- Consider library or improve existing implementation
- Estimated effort: 1 day

---

### 3. Manual Debouncing (Already Well-Implemented) - NO ACTION NEEDED

#### Location
`app/src/main/java/com/milki/launcher/presentation/search/SearchViewModel.kt` (lines 154-160)

#### Current Implementation
```kotlin
// SearchViewModel.kt:154-160
private fun observeSearchQueries() {
    searchQuery
        .onEach { updateState { copy(isLoading = true) } }
        .mapLatest { query -> executeSearchLogic(query) }
        .onEach { results -> updateState { copy(results = results, isLoading = false) } }
        .launchIn(viewModelScope)
}
```

#### Assessment
The codebase uses `mapLatest` from Kotlin Flow, which automatically cancels previous searches when new queries arrive. This is the **recommended modern approach** for debouncing in Kotlin.

#### Recommendation: **No library needed**
The current implementation is idiomatic and efficient. If debounce delay is needed, simply add:
```kotlin
searchQuery
    .debounce(300) // Add debounce delay if needed
    .mapLatest { query -> executeSearchLogic(query) }
```

---

### 4. Permission Handling - LOW PRIORITY

#### Location
- `app/src/main/java/com/milki/launcher/handlers/PermissionHandler.kt`
- `app/src/main/java/com/milki/launcher/util/PermissionUtil.kt`

#### Current Implementation
Manual permission handling using Activity Result API with manual state tracking.

#### Assessment
The implementation is comprehensive and follows Android best practices:
- Uses ActivityResultContracts (modern API)
- Handles version-specific permissions correctly
- Proper callback structure

#### Optional Library: Accompanist Permissions (for Compose)

```kotlin
// With Accompanist
val permissionState = rememberPermissionState(
    Manifest.permission.READ_CONTACTS
)
Button(onClick = { permissionState.launchPermissionRequest() }) {
    Text("Request permission")
}
```

#### Benefits
- More declarative for Compose UI
- Simpler state management in composables
- Less boilerplate

#### Migration Complexity: **LOW-MEDIUM**
- Refactor permission handling to Compose-side
- Remove PermissionHandler boilerplate
- Estimated effort: 1-2 days

#### Recommendation: **Optional**
Current implementation works well. Consider Accompanist if simplifying Compose-side permission handling.

---

### 5. No Database Layer - APPROPRIATE CHOICE

#### Current State
The app uses DataStore Preferences for all data storage. There is no Room/SQLite database.

#### Assessment
This is the **correct architectural choice** for this app because:
1. **Simple data structures**: Settings and pinned items don't need complex queries
2. **Small data volume**: Pinned items list is typically < 50 items
3. **Key-value access**: All data is accessed by key, not queried
4. **Reactive**: DataStore provides Flow-based updates automatically

#### Recommendation: **No Room needed**
Adding Room would add complexity without benefit. DataStore is appropriate for:
- Settings (SettingsRepositoryImpl)
- Pinned items (HomeRepositoryImpl)
- Recent apps (AppRepositoryImpl)
- Recent contacts (ContactsRepositoryImpl)

---

### 6. No Networking Layer - APPROPRIATE CHOICE

#### Current State
The app does not make any HTTP/network requests. All data is local to the device.

#### Assessment
No Retrofit/OkHttp needed because:
- Web search opens browser (no API calls)
- YouTube search opens YouTube app
- No backend integration required

#### Recommendation: **No networking library needed**
If future features require API calls, add Retrofit + OkHttp.

---

### 7. Manual MIME Type Management - LOW PRIORITY

#### Location
`app/src/main/java/com/milki/launcher/util/MimeTypeUtil.kt`

#### Current Implementation
```kotlin
object MimeTypeUtil {
    const val MIME_PDF = "application/pdf"
    const val MIME_EPUB = "application/epub+zip"
    // ... more constants
    
    private val extensionToMimeType: Map<String, String> = mapOf(
        "pdf" to MIME_PDF,
        "epub" to MIME_EPUB,
        // ... more mappings
    )
}
```

#### Problems
1. **Incomplete list**: Many file types not covered
2. **Maintenance burden**: New formats need manual addition
3. **Limited utility**: Basic checks only

#### Recommended Library: Android's MimeTypeMap

```kotlin
// Android provides MimeTypeMap
import android.webkit.MimeTypeMap

fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.').lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) 
        ?: "application/octet-stream"
}
```

#### Benefits
- **Complete coverage**: All standard MIME types
- **System maintained**: Android updates the list
- **Less code**: Remove manual mappings

#### Migration Complexity: **LOW**
- Replace manual mapping with MimeTypeMap
- Keep constants for special cases
- Estimated effort: 2-4 hours

---

### 8. Testing Infrastructure - MEDIUM PRIORITY

#### Location
- `app/src/test/java/com/milki/launcher/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/milki/launcher/ExampleInstrumentedTest.kt`

#### Current State
Only example tests exist. No actual test coverage.

#### Recommended Libraries
```kotlin
// Unit testing
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
testImplementation("app.cash.turbine:turbine:1.0.0") // Flow testing
testImplementation("io.mockk:mockk:1.13.9") // Mocking

// Compose UI testing
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("io.mockk:mockk-android:1.13.9")
```

#### Benefits
- **Coroutines test**: Test suspend functions and Flows
- **Turbine**: Test Flow emissions easily
- **MockK**: Kotlin-friendly mocking

#### Migration Complexity: **MEDIUM**
- Add test dependencies
- Write tests for critical paths (FilterAppsUseCase, repositories)
- Estimated effort: 2-3 days for basic coverage

---

## Summary Table

| Finding | Priority | Recommended Library | Complexity | Benefit |
|---------|----------|---------------------|------------|---------|
| Manual Serialization | HIGH | kotlinx.serialization | MEDIUM | Type safety, maintainability |
| URL Detection | MEDIUM | Extract to utility class | LOW | Cleaner code |
| Debouncing | NONE | Already optimal | N/A | N/A |
| Permission Handling | LOW | Accompanist Permissions | LOW-MEDIUM | Less boilerplate |
| Database Layer | NONE | Not needed | N/A | N/A |
| Networking | NONE | Not needed | N/A | N/A |
| MIME Types | LOW | Android MimeTypeMap | LOW | Better coverage |
| Testing | MEDIUM | kotlinx-coroutines-test, Turbine, MockK | MEDIUM | Code quality |

---

## Recommended Actions

### Immediate (High Priority)
1. **Add kotlinx.serialization** for HomeItem serialization
   - Replace manual pipe-delimited parsing
   - Add JSON serialization for data classes
   - Implement migration for existing stored data

### Short-term (Medium Priority)
2. **Add testing infrastructure**
   - Add kotlinx-coroutines-test, Turbine, MockK
   - Write tests for critical business logic

3. **Refactor URL detection**
   - Extract to dedicated UrlValidator utility class
   - Consider library or improve implementation

### Optional (Low Priority)
4. **Replace MIME type utility**
   - Use Android's MimeTypeMap instead of manual mappings

5. **Consider Accompanist Permissions**
   - If Compose-side permission handling would simplify code

---

## Dependency Recommendations

### Add to `libs.versions.toml`:
```toml
[versions]
kotlinx-serialization = "1.6.3"
turbine = "1.0.0"
mockk = "1.13.9"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

### Add to `app/build.gradle.kts`:
```kotlin
plugins {
    // Add serialization plugin
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
```

---

## Conclusion

The codebase is well-structured and already uses modern Android libraries. The most impactful improvement would be **replacing manual serialization with kotlinx.serialization**, which would eliminate fragile string parsing and provide type safety for stored data.

The lack of Room and Retrofit is appropriate for this app's use case, as all data is local and accessed via key-value pairs rather than complex queries.

Adding testing infrastructure should be prioritized to ensure code quality and prevent regressions.
