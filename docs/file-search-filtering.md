# File Search Filtering

This document explains how file search results are filtered to provide clean, useful results without "noise" files.

## The Problem

When searching files on an Android device, the MediaStore database contains many files that users typically don't want to see:

- **Temporary files**: `.tmp`, `.temp`, `.cache`
- **Backup files**: `.bak`, `.backup`, `~filename`
- **Lock files**: `.lock` (used for synchronization)
- **Log files**: `.log`
- **Partial downloads**: `.part`, `.crdownload`, `.download`
- **System files**: `.DS_Store`, `.nomedia`, `.thumbnails`
- **Hidden files**: Files starting with `.` on Unix-like systems
- **Empty files**: 0-byte placeholder files
- **Media files**: Images, videos, audio (not "documents")

Without filtering, search results become cluttered with these files, making it harder to find actual user documents.

## The Solution: FileFilterConfig

`FileFilterConfig` is a centralized configuration object that defines all filtering rules in one place.

### Location
```
app/src/main/java/com/milki/launcher/domain/model/FileFilterConfig.kt
```

### How It Works

The config checks multiple criteria for each file:

```
File Found → Check Prefix → Check Extension → Check Path → Check MIME → Check Size → INCLUDE
                ↓ excluded      ↓ excluded       ↓ excluded    ↓ excluded   ↓ excluded
            HIDDEN FILE    TEMP FILE       CACHE DIR    MEDIA FILE   TOO SMALL
```

If ANY check fails, the file is excluded from results.

## Filtering Rules

### 1. Filename Prefix Exclusions

Files starting with these characters are hidden:

| Prefix | Meaning | Example |
|--------|---------|---------|
| `.` | Hidden file (Unix convention) | `.hidden_file.txt` |
| `~` | Backup file (editor convention) | `~document.txt` |

### 2. Extension Exclusions

Files with these extensions are excluded:

| Category | Extensions |
|----------|------------|
| Temporary | `tmp`, `temp`, `cache` |
| Backup | `bak`, `backup` |
| Lock | `lock` |
| Log | `log` |
| Partial Downloads | `part`, `partial`, `crdownload`, `download` |
| System | `ds_store`, `nomedia`, `thumbnails`, `thumb`, `thumbdata` |

### 3. Directory Exclusions

Files in these directories are excluded:

- `cache`, `.cache` - Cache directories
- `tmp`, `temp` - Temporary directories
- `code_cache`, `files_cache` - Android-specific caches
- `thumbnails` - Thumbnail cache directories

### 4. MIME Type Exclusions

Files with these MIME type prefixes are excluded:

| Prefix | What it excludes |
|--------|------------------|
| `image/` | All images (jpeg, png, gif, webp, etc.) |
| `video/` | All videos (mp4, webm, mkv, etc.) |
| `audio/` | All audio (mp3, wav, flac, ogg, etc.) |

### 5. Size Exclusions

Files smaller than **1KB (1024 bytes)** are excluded.

Small files are typically:
- Empty placeholder files (0 bytes)
- Corrupted or incomplete files
- System marker files
- Small configuration files

## Usage in Code

### Basic Usage

```kotlin
// Check if a file should be included
if (FileFilterConfig.shouldIncludeFile(
    fileName = "document.pdf",
    mimeType = "application/pdf",
    size = 50000,
    relativePath = "Documents/Work"
)) {
    // Include this file in results
}
```

### Individual Checks

```kotlin
// Check just the extension
if (FileFilterConfig.hasExcludedExtension("tmp")) {
    // Skip .tmp files
}

// Check the path for excluded directories
if (FileFilterConfig.pathContainsExcludedDirectory("/storage/cache/file.pdf")) {
    // Skip files in cache directories
}

// Check if MIME type is media
if (FileFilterConfig.hasExcludedMimeType("image/jpeg")) {
    // Skip media files
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FilesRepositoryImpl                       │
│  (Queries MediaStore, uses FileFilterConfig for filtering)  │
└─────────────────────────────┬───────────────────────────────┘
                              │ uses
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      FileFilterConfig                        │
│  (Centralized, immutable, pure-function filtering rules)    │
└─────────────────────────────────────────────────────────────┘
```

### Why a Separate Config Object?

1. **Single Source of Truth**: All filtering rules in one place
2. **Testability**: Easy to unit test filtering logic
3. **Extensibility**: Easy to add new rules
4. **Documentation**: Each rule is documented in one file
5. **Performance**: Immutable sets, compiled once

## Customization

To change filtering behavior, edit `FileFilterConfig.kt`:

### Add a New Excluded Extension

```kotlin
private val EXCLUDED_EXTENSIONS = setOf(
    "tmp", "temp", "cache",
    // Add your extension here:
    "xyz",
    // ...
)
```

### Remove an Exclusion

To allow `.log` files (maybe you're a developer searching for logs):

```kotlin
private val EXCLUDED_EXTENSIONS = setOf(
    "tmp", "temp", "cache",
    // "log" - removed to allow log files
    "bak", "backup",
    // ...
)
```

### Change Minimum File Size

```kotlin
// Change from 1KB to 0 (no minimum)
const val MIN_FILE_SIZE_BYTES: Long = 0L

// Or increase to 10KB
const val MIN_FILE_SIZE_BYTES: Long = 10240L
```

## Related Files

| File | Purpose |
|------|---------|
| `FileFilterConfig.kt` | Filtering rules configuration |
| `FilesRepository.kt` | Interface defining file access |
| `FilesRepositoryImpl.kt` | Implementation using MediaStore |
| `FilesSearchProvider.kt` | Search provider for "f" prefix |
| `FileDocument.kt` | Data model for file results |

## Educational Notes for New Android Developers

### Why Use `object` Instead of `class`?

`FileFilterConfig` is declared as `object` (singleton) because:
- We only need one instance of the filter rules
- All methods are pure functions (no state to manage)
- No need to create instances with `new`

```kotlin
// No need for this:
val config = FileFilterConfig()
config.shouldIncludeFile(...)

// Just call directly:
FileFilterConfig.shouldIncludeFile(...)
```

### Why Immutable Sets?

The filter sets are created with `setOf()` which returns immutable sets:

```kotlin
private val EXCLUDED_EXTENSIONS = setOf("tmp", "temp", ...)
```

Immutable means they cannot be changed at runtime. This prevents bugs where code might accidentally add or remove filters.

### What is a Pure Function?

All methods in `FileFilterConfig` are pure functions:

```kotlin
fun shouldIncludeFile(fileName: String, ...): Boolean {
    // Only uses input parameters
    // No reading/writing external state
    // Same input always = same output
}
```

Benefits of pure functions:
- Easy to test (no mocking needed)
- Thread-safe (no shared state)
- Predictable behavior

### Why `companion object`?

If you see code with `companion object`, it's similar to `static` in Java:

```kotlin
class MyClass {
    companion object {
        const val MY_CONSTANT = "value"
        
        fun staticMethod() { ... }
    }
}

// Called as:
MyClass.MY_CONSTANT
MyClass.staticMethod()
```

`object` (used for `FileFilterConfig`) is a simpler syntax when the whole class should be singleton - no `companion object` needed.
