# File Search Filtering

This document explains how file search results are filtered to provide clean, useful results without "noise" files.

The current strategy is **document-first**:

1. Normalize MIME metadata using Android system MIME lookup + curated fallback.
2. Apply explicit exclusion rules (hidden/temp/cache/media/small files).
3. Require files to match a supported document type allowlist.

This three-step model is intentionally stricter than the previous "exclude-only" approach.

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

Without strict filtering, search results become cluttered with these files, making it harder to find actual user documents.

Real-world examples that should not pollute launcher search:

- Hidden artifacts like `.crypto`
- Style/source assets like `.css`
- Temporary or partial downloader leftovers

## The Solution: `FileFilterConfig` + MIME Normalization

`FileFilterConfig` is the centralized filtering policy object. File metadata is normalized before policy checks.

MIME normalization source of truth:

- `MimeTypeMap` (Android system registry) as primary resolver
- Small internal fallback map for common document formats
- Final fallback: `application/octet-stream`

### Location
```
app/src/main/java/com/milki/launcher/domain/model/FileFilterConfig.kt
```

### How It Works

The config checks multiple criteria for each file:

```
File Found → Normalize MIME → Check Prefix → Check Extension Exclusions → Check Path Exclusions
                                                                                                     ↓ excluded                  ↓ excluded
                                                                                             TEMP/NOISE FILE               CACHE/SYSTEM PATH

                    → Check MIME Exclusions (media) → Check Minimum Size → Check Supported Document Allowlist → INCLUDE
                                                 ↓ excluded               ↓ excluded                         ↓ excluded
                                             MEDIA FILE                TOO SMALL                      UNSUPPORTED TYPE
```

If ANY check fails, the file is excluded from results.

Only files that survive exclusions **and** match supported document type policy are included.

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

### 6. Supported Document Allowlist (Document-First Gate)

After exclusions, files must match at least one supported type signal:

- MIME matches a known document MIME (exact value)
- MIME starts with known office-family prefixes
- Extension is in launcher-supported document extensions

This final gate is what prevents new unknown artifacts from leaking into results.

Examples:

- `report.pdf` → included
- `book.epub` → included
- `.crypto` → excluded (hidden prefix + unsupported)
- `styles.css` → excluded (unsupported extension/MIME for launcher document search)

## Usage in Code

### Basic Usage

```kotlin
// Check if a file should be included in launcher search
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
// Check if file matches document allowlist policy
if (!FileFilterConfig.matchesSupportedDocumentType("styles.css", "text/css")) {
    // CSS is not part of launcher document-first search surface
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FilesRepositoryImpl                       │
│  (Queries MediaStore, normalizes MIME, then filters)        │
└─────────────────────────────┬───────────────────────────────┘
                              │ uses
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      FileFilterConfig                        │
│  (Centralized, immutable, pure-function filtering policy)   │
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

### Add a New Supported Extension

```kotlin
private val ALLOWED_EXTENSIONS = setOf(
    "pdf", "docx", "xlsx",
    // Add your supported extension here:
    "abc"
)
```

### Add an Excluded Extension

If a noisy type appears repeatedly, add it to `EXCLUDED_EXTENSIONS`.

```kotlin
private val EXCLUDED_EXTENSIONS = setOf(
    "tmp", "cache", "log",
    // Add explicit blocklist entries here:
    "noiseext"
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
| `MimeTypeUtil.kt` | MIME normalization and fallback resolution |
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

### Why Document-First Beats Exclude-Only

Exclude-only filtering eventually leaks new junk types because unknown files are not automatically blocked.

Document-first filtering is safer for launcher UX because it makes inclusion explicit:

- New file types are excluded until intentionally allowed.
- Search results remain stable and relevant.
- Noise regressions are reduced when OEMs/apps emit unusual metadata.

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
