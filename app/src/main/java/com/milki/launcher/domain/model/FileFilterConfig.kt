/**
 * FileFilterConfig.kt - Centralized configuration for filtering "noise" files from search results
 * 
 * This file defines the rules for filtering out files that users typically don't want to see
 * in search results. These include temporary files, cache files, system files, and other
 * "noise" that clutters search results.
 * 
 * WHY FILTER FILES?
 * ================
 * When searching files on a device, the MediaStore database contains MANY files that are
 * not user-created documents. These include:
 * 
 * - Temporary files created by apps (e.g., .tmp, .temp)
 * - Cache files that apps store (e.g., .cache)
 * - Lock files for synchronization (e.g., .lock)
 * - Backup files from editors (e.g., file.txt.bak, file.txt~)
 * - Partial downloads that haven't completed (e.g., .part, .crdownload)
 * - System files created by the OS (e.g., .DS_Store on macOS, .nomedia)
 * - Hidden files (files starting with a dot on Unix-like systems)
 * - Very small files (often placeholders or corrupted files)
 * 
 * Filtering these out makes search results cleaner and more useful.
 * 
 * HOW TO USE THIS CONFIG:
 * ======================
 * 
 * // Check if a file should be included in results:
 * if (FileFilterConfig.shouldIncludeFile(
 *     fileName = "document.pdf",
 *     mimeType = "application/pdf",
 *     size = 1024,
 *     relativePath = "Documents/Work"
 * )) {
 *     // Include this file in results
 * }
 * 
 * // Check just the extension:
 * if (FileFilterConfig.isExcludedExtension("tmp")) {
 *     // Skip this file
 * }
 * 
 * // Check if path contains excluded directory:
 * if (FileFilterConfig.pathContainsExcludedDirectory("/storage/Android/data/com.app/cache")) {
 *     // Skip this file
 * }
 * 
 * CUSTOMIZATION:
 * =============
 * If you want to change the filtering behavior, you can modify the sets below.
 * For example, to allow .log files (maybe you're a developer searching for logs):
 * 
 * 1. Remove "log" from EXCLUDED_EXTENSIONS
 * 2. Or create a custom check in your code before calling shouldIncludeFile()
 * 
 * EDUCATIONAL NOTE FOR NEW ANDROID DEVELOPERS:
 * ===========================================
 * The sets below use Kotlin's setOf() function to create immutable sets.
 * Immutable means the sets cannot be modified after creation - this prevents
 * accidental changes to the filter rules at runtime.
 * 
 * The companion object makes these properties and methods available without
 * needing to create an instance of FileFilterConfig. This is similar to static
 * methods in Java. You call them like: FileFilterConfig.shouldIncludeFile(...)
 * 
 * The sets are defined in the companion object (not in the class body) because
 * they don't need separate instances - there's only one copy of these rules.
 */

package com.milki.launcher.domain.model

/**
 * Configuration object containing all the rules for filtering files from search results.
 * 
 * This is an object (singleton) rather than a class because:
 * 1. We only need one instance of these filter rules
 * 2. All methods are pure functions - they don't modify any state
 * 3. The filter rules should be consistent across the entire app
 * 
 * THREAD SAFETY:
 * This object is thread-safe because all properties are immutable (val, not var)
 * and all methods are pure functions (they only use their input parameters).
 */
object FileFilterConfig {
    
    // ========================================================================
    // FILENAME PREFIX EXCLUSIONS
    // ========================================================================
    // These prefixes indicate "hidden" or special files that should be excluded.
    // On Unix-like systems (Linux, macOS, Android), files starting with a dot
    // are hidden by convention - they don't show up in normal file listings.
    // 
    // Examples of files that will be excluded:
    // - .hidden_file.txt (dot prefix = hidden file)
    // - ~backup_file.doc (tilde prefix = often a backup)
    // - .nomedia (special file that tells Android not to show media)
    /**
     * Filename prefixes that indicate the file should be excluded.
     * 
     * Files starting with these characters are typically:
     * - Hidden files (dot prefix on Unix-like systems)
     * - Backup files (tilde prefix used by some editors)
     */
    private val EXCLUDED_FILENAME_PREFIXES = setOf(
        ".",    // Hidden files on Unix/Linux/Android (e.g., .hidden_file)
        "~"     // Backup files created by some editors (e.g., ~document.txt)
    )
    
    // ========================================================================
    // FILE EXTENSION EXCLUSIONS
    // ========================================================================
    // These file extensions indicate files that are typically not user documents.
    // The extensions are stored WITHOUT the dot (e.g., "tmp" not ".tmp") for
    // easier comparison. The actual check converts the file's extension to
    // lowercase and compares against this set.
    // 
    // Categories of excluded extensions:
    // 
    // TEMPORARY FILES:
    // - tmp, temp: Generic temporary files created by apps
    // - cache: Cached data that can be recreated
    // 
    // BACKUP FILES:
    // - bak, backup: Backup copies of files
    // 
    // LOCK FILES:
    // - lock: Used by apps to prevent concurrent access
    // 
    // LOG FILES:
    // - log: Application logs (developers might want to remove this)
    // 
    // PARTIAL DOWNLOADS:
    // - part, partial, crdownload, download: Incomplete downloads
    //   - .crdownload is Chrome's partial download format
    //   - .part is used by Firefox and other downloaders
    // 
    // SYSTEM FILES:
    // - ds_store: macOS folder settings file (might appear on SD cards)
    // - nomedia: Android file that tells MediaStore to ignore a folder
    // - thumbnails: Thumbnail cache databases
    /**
     * File extensions that should be excluded from search results.
     * 
     * Extensions are stored WITHOUT the leading dot and in lowercase.
     * For example, ".TMP" would be stored as "tmp" and compared case-insensitively.
     */
    private val EXCLUDED_EXTENSIONS = setOf(
        // Temporary files - created by apps for short-term use
        "tmp",      // Generic temporary file
        "temp",     // Generic temporary file
        "cache",    // Cached data
        
        // Backup files - copies created by editors or apps
        "bak",      // Backup file
        "backup",   // Backup file
        
        // Lock files - used for synchronization
        "lock",     // Lock file (prevents concurrent access)
        
        // Log files - application logs
        "log",      // Application log file
        
        // Partial/incomplete downloads
        "part",         // Firefox partial download
        "partial",      // Generic partial download
        "crdownload",   // Chrome partial download
        "download",     // Generic partial download
        
        // System files
        "ds_store",     // macOS folder settings
        "nomedia",      // Android "ignore this folder" marker
        "thumbnails",   // Thumbnail cache database
        "thumb",        // Thumbnail file
        "thumbdata"     // Android thumbnail data file
    )
    
    // ========================================================================
    // DIRECTORY NAME EXCLUSIONS
    // ========================================================================
    // If a file's path contains any of these directory names, it should be
    // excluded. This helps filter out files in cache directories, temporary
    // folders, and other system locations.
    // 
    // How the check works:
    // - The path is split by "/" and each directory name is checked
    // - If ANY directory matches, the file is excluded
    // 
    // Example:
    // - Path: "/storage/emulated/0/Android/data/com.app/cache/somefile.tmp"
    // - Contains "cache" directory -> file is excluded
    // 
    // ANDROID-SPECIFIC DIRECTORIES:
    // - cache: App cache directory (can be cleared by user/system)
    // - code_cache: Code cache directory (Android 5.0+)
    // - files_cache: Some apps create this for cached files
    // - .cache: Hidden cache directory (dot prefix)
    // - tmp, temp: Temporary directories
    /**
     * Directory names that indicate files should be excluded.
     * 
     * If a file's path contains any of these directory names, the file is excluded.
     * Directory names are compared case-insensitively.
     */
    private val EXCLUDED_DIRECTORY_NAMES = setOf(
        // Generic temporary/cache directories
        "cache",        // Standard cache directory name
        "tmp",          // Temporary directory
        "temp",         // Temporary directory
        
        // Hidden cache directories (dot prefix on Unix)
        ".cache",       // Hidden cache directory
        
        // Android-specific directories
        "code_cache",   // Android code cache (API 21+)
        "files_cache",  // Some apps use this name
        
        // Thumbnail directories
        "thumbnails"    // Android thumbnail cache directory
    )
    
    // ========================================================================
    // MIME TYPE EXCLUSIONS
    // ========================================================================
    // MIME types indicate the general category of a file. We exclude media
    // types (images, videos, audio) because this app is focused on documents.
    // 
    // MIME type format: "type/subtype" (e.g., "image/jpeg", "application/pdf")
    // 
    // By excluding entire prefixes, we filter out ALL subtypes:
    // - image/* -> image/jpeg, image/png, image/gif, etc.
    // - video/* -> video/mp4, video/webm, etc.
    // - audio/* -> audio/mpeg (mp3), audio/ogg, etc.
    /**
     * MIME type prefixes that indicate files should be excluded.
     * 
     * By checking prefixes, we exclude entire categories of files.
     * For example, "image/" excludes "image/jpeg", "image/png", etc.
     */
    private val EXCLUDED_MIME_PREFIXES = setOf(
        "image/",   // All image types (jpeg, png, gif, webp, etc.)
        "video/",   // All video types (mp4, webm, mkv, etc.)
        "audio/"    // All audio types (mp3, wav, flac, ogg, etc.)
    )
    
    // ========================================================================
    // SIZE EXCLUSIONS
    // ========================================================================
    // Files below this size are typically:
    // - Empty placeholder files (0 bytes)
    // - Corrupted or incomplete files
    // - Marker files (like .nomedia which is 0 bytes)
    // - Very small config files that aren't user documents
    // 
    // We set 1KB (1024 bytes) as the minimum because:
    // - Most legitimate documents are larger than 1KB
    // - It filters out empty files and tiny placeholders
    // - Small config/marker files are typically under 1KB
    /**
     * Minimum file size in bytes for a file to be included in results.
     * 
     * Files smaller than this are typically:
     * - Empty placeholder files (0 bytes)
     * - Corrupted or incomplete files
     * - System marker files (like .nomedia)
     * - Small configuration files
     * 
     * Default: 1024 bytes (1 KB)
     */
    const val MIN_FILE_SIZE_BYTES: Long = 1024L
    
    // ========================================================================
    // MAIN FILTER METHOD
    // ========================================================================
    /**
     * Check if a file should be included in search results.
     * 
     * This is the main entry point for filtering. It checks all the exclusion
     * rules and returns true only if the file passes ALL checks.
     * 
     * @param fileName The name of the file (e.g., "document.pdf")
     * @param mimeType The MIME type of the file (e.g., "application/pdf")
     *                 Can be empty string if unknown
     * @param size The size of the file in bytes
     * @param relativePath The path to the file (e.g., "Documents/Work")
     *                     Used to check for excluded directories
     * @return true if the file should be INCLUDED in results, false to exclude
     * 
     * EXAMPLES:
     * shouldIncludeFile("report.pdf", "application/pdf", 50000, "Documents")
     *   -> true (valid PDF document)
     * 
     * shouldIncludeFile(".hidden", "", 0, "config")
     *   -> false (hidden file, empty)
     * 
     * shouldIncludeFile("temp.tmp", "", 500, "cache")
     *   -> false (excluded extension, in cache directory, under 1KB)
     */
    fun shouldIncludeFile(
        fileName: String,
        mimeType: String,
        size: Long,
        relativePath: String
    ): Boolean {
        // Check 1: Not a hidden file (doesn't start with . or ~)
        if (hasExcludedPrefix(fileName)) {
            return false
        }
        
        // Check 2: Extension not in excluded list
        if (hasExcludedExtension(fileName)) {
            return false
        }
        
        // Check 3: Path doesn't contain excluded directories
        if (pathContainsExcludedDirectory(relativePath)) {
            return false
        }
        
        // Check 4: MIME type not excluded (image, video, audio)
        if (hasExcludedMimeType(mimeType)) {
            return false
        }
        
        // Check 5: File size is at least MIN_FILE_SIZE_BYTES
        if (size < MIN_FILE_SIZE_BYTES) {
            return false
        }
        
        // All checks passed - include this file
        return true
    }
    
    // ========================================================================
    // INDIVIDUAL CHECK METHODS
    // ========================================================================
    // These methods are broken out for two reasons:
    // 1. Readability - each check is clearly named and documented
    // 2. Reusability - callers can check individual conditions if needed
    // 
    // All methods are private except when there's a use case for calling them
    // directly (like isExcludedExtension for quick checks).
    
    /**
     * Check if a filename starts with an excluded prefix (. or ~).
     * 
     * @param fileName The filename to check
     * @return true if the filename starts with an excluded prefix
     * 
     * EXAMPLES:
     * hasExcludedPrefix(".hidden") -> true
     * hasExcludedPrefix("~backup") -> true
     * hasExcludedPrefix("document.pdf") -> false
     */
    private fun hasExcludedPrefix(fileName: String): Boolean {
        // Iterate through each prefix and check if filename starts with it
        return EXCLUDED_FILENAME_PREFIXES.any { prefix ->
            fileName.startsWith(prefix)
        }
    }
    
    /**
     * Check if a filename has an excluded extension.
     * 
     * Extracts the extension from the filename (everything after the last dot)
     * and checks if it's in the excluded list.
     * 
     * @param fileName The filename to check
     * @return true if the extension is in the excluded list
     * 
     * EXAMPLES:
     * hasExcludedExtension("file.tmp") -> true
     * hasExcludedExtension("file.TMP") -> true (case-insensitive)
     * hasExcludedExtension("file.pdf") -> false
     * hasExcludedExtension("file") -> false (no extension)
     */
    fun hasExcludedExtension(fileName: String): Boolean {
        // Extract the extension (everything after the last dot)
        val extension = fileName.substringAfterLast('.', "")
        
        // Empty string means no extension - not excluded
        if (extension.isEmpty()) {
            return false
        }
        
        // Check against excluded extensions (case-insensitive)
        return extension.lowercase() in EXCLUDED_EXTENSIONS
    }
    
    /**
     * Check if a file path contains any excluded directory names.
     * 
     * Splits the path by "/" and checks if any directory name matches
     * the excluded list.
     * 
     * @param path The file path to check (e.g., "Documents/cache/file.pdf")
     * @return true if the path contains an excluded directory
     * 
     * EXAMPLES:
     * pathContainsExcludedDirectory("Documents/cache/file.pdf") -> true
     * pathContainsExcludedDirectory("Downloads/file.pdf") -> false
     * pathContainsExcludedDirectory("/storage/emulated/0/Android/data/com.app/cache") -> true
     */
    fun pathContainsExcludedDirectory(path: String): Boolean {
        // Normalize path separators (handle both / and \)
        val normalizedPath = path.replace('\\', '/')
        
        // Split by "/" and check each directory name
        val directories = normalizedPath.split("/")
        
        return directories.any { directory ->
            // Check if this directory name is in the excluded list
            // Comparison is case-insensitive
            directory.lowercase() in EXCLUDED_DIRECTORY_NAMES
        }
    }
    
    /**
     * Check if a MIME type is in the excluded list.
     * 
     * Checks if the MIME type starts with any excluded prefix
     * (image/, video/, audio/).
     * 
     * @param mimeType The MIME type to check (e.g., "image/jpeg")
     * @return true if the MIME type is excluded
     * 
     * EXAMPLES:
     * hasExcludedMimeType("image/jpeg") -> true
     * hasExcludedMimeType("video/mp4") -> true
     * hasExcludedMimeType("audio/mpeg") -> true
     * hasExcludedMimeType("application/pdf") -> false
     * hasExcludedMimeType("") -> false (empty string not excluded)
     */
    fun hasExcludedMimeType(mimeType: String): Boolean {
        // Empty MIME type means unknown - not excluded
        if (mimeType.isEmpty()) {
            return false
        }
        
        // Check if MIME type starts with any excluded prefix
        return EXCLUDED_MIME_PREFIXES.any { prefix ->
            mimeType.lowercase().startsWith(prefix)
        }
    }
    
    /**
     * Check if a file size meets the minimum requirement.
     * 
     * @param size The file size in bytes
     * @return true if the file is large enough to include
     * 
     * EXAMPLES:
     * meetsMinSizeRequirement(2048) -> true (2KB > 1KB minimum)
     * meetsMinSizeRequirement(512) -> false (512 bytes < 1KB minimum)
     * meetsMinSizeRequirement(0) -> false (empty file)
     */
    fun meetsMinSizeRequirement(size: Long): Boolean {
        return size >= MIN_FILE_SIZE_BYTES
    }
}
