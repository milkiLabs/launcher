## Findings

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

| Finding              | Priority | Recommended Library                     | Complexity | Benefit                      |
| -------------------- | -------- | --------------------------------------- | ---------- | ---------------------------- |
| Manual Serialization | HIGH     | kotlinx.serialization                   | MEDIUM     | Type safety, maintainability |
| URL Detection        | MEDIUM   | Extract to utility class                | LOW        | Cleaner code                 |
| Debouncing           | NONE     | Already optimal                         | N/A        | N/A                          |
| Permission Handling  | LOW      | Accompanist Permissions                 | LOW-MEDIUM | Less boilerplate             |
| Database Layer       | NONE     | Not needed                              | N/A        | N/A                          |
| Networking           | NONE     | Not needed                              | N/A        | N/A                          |
| MIME Types           | LOW      | Android MimeTypeMap                     | LOW        | Better coverage              |
| Testing              | MEDIUM   | kotlinx-coroutines-test, Turbine, MockK | MEDIUM     | Code quality                 |

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
