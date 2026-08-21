package com.milki.launcher.domain.model

import kotlinx.serialization.Serializable

/**
 * FileSearchExtensionConfig.kt - User-configurable file search extension allowlist
 *
 * This model allows users to control which file types appear in search results
 * when using the file search prefix (e.g., "f").
 *
 * DESIGN:
 * - Extensions are organized into categories that can be toggled on/off
 * - Users can also add arbitrary custom extensions
 * - By default, only document-type categories are enabled (matching the
 *   previous hardcoded behavior in FileFilterConfig)
 * - Media categories (Images, Audio, Video) are OFF by default because
 *   they were previously excluded by MIME prefix
 *
 * DATA FLOW:
 * 1. User toggles a category or adds a custom extension in Settings
 * 2. SettingsViewModel updates LauncherSettings.fileSearchExtensionConfig
 * 3. SettingsRepositoryImpl persists to DataStore
 * 4. SearchViewModel observes the change and passes the config via SearchRequest
 * 5. FilesSearchProvider passes it to FilesRepository
 * 6. FileFilterConfig uses the resolved extensions for filtering
 */

@Serializable
enum class FileSearchCategory(val displayName: String) {
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    AUDIO("Audio"),
    VIDEO("Video"),
    ARCHIVES("Archives"),
    CODE("Code & Config")
}

@Serializable
data class FileSearchExtensionConfig(
    val enabledCategories: Set<FileSearchCategory> = DEFAULT_ENABLED_CATEGORIES,
    val customExtensions: Set<String> = emptySet()
) {
    companion object {
        val DEFAULT_ENABLED_CATEGORIES: Set<FileSearchCategory> = setOf(
            FileSearchCategory.DOCUMENTS,
            FileSearchCategory.ARCHIVES,
            FileSearchCategory.CODE
        )

        /** Extensions grouped by category */
        val categoryExtensions: Map<FileSearchCategory, Set<String>> = mapOf(
            FileSearchCategory.DOCUMENTS to setOf(
                "pdf", "epub", "txt", "rtf", "md",
                "doc", "docx", "odt",
                "xls", "xlsx", "ods", "csv", "tsv",
                "ppt", "pptx", "odp"
            ),
            FileSearchCategory.IMAGES to setOf(
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff"
            ),
            FileSearchCategory.AUDIO to setOf(
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"
            ),
            FileSearchCategory.VIDEO to setOf(
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp"
            ),
            FileSearchCategory.ARCHIVES to setOf(
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk"
            ),
            FileSearchCategory.CODE to setOf(
                "json", "xml", "yaml", "yml", "toml", "ini", "conf"
            )
        )

        /** MIME prefixes that correspond to media categories */
        val categoryMimePrefixes: Map<FileSearchCategory, String> = mapOf(
            FileSearchCategory.IMAGES to "image/",
            FileSearchCategory.AUDIO to "audio/",
            FileSearchCategory.VIDEO to "video/"
        )
    }

    /**
     * Compute the effective set of allowed extensions from enabled categories + custom.
     */
    fun resolveAllowedExtensions(): Set<String> {
        val extensions = mutableSetOf<String>()
        for (category in enabledCategories) {
            categoryExtensions[category]?.let { extensions.addAll(it) }
        }
        extensions.addAll(customExtensions.map { it.lowercase().trim() })
        return extensions
    }

    /**
     * Compute which MIME prefixes should be excluded.
     *
     * Media categories that are NOT enabled should have their MIME prefix excluded
     * (preventing those files from appearing even if their extension isn't checked).
     * Categories that ARE enabled should NOT have their MIME prefix excluded.
     */
    fun resolveExcludedMimePrefixes(): Set<String> {
        return categoryMimePrefixes
            .filterKeys { category -> category !in enabledCategories }
            .values
            .toSet()
    }
}
