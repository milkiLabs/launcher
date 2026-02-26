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
