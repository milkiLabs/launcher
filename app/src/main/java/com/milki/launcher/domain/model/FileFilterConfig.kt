
package com.milki.launcher.domain.model

object FileFilterConfig {
        private val EXCLUDED_FILENAME_PREFIXES = setOf(
        ".",    
        "~"     
    )
        private val EXCLUDED_EXTENSIONS = setOf(
        "tmp",      
        "temp",     
        "cache",   
        "bak",    
        "backup",
        "lock", 
        "log", 
        "part",         
        "partial",      
        "crdownload",   
        "download",     
        "ds_store",     
        "nomedia",      
        "thumbnails",   
        "thumb",        
        "thumbdata"     
    )
        private val EXCLUDED_DIRECTORY_NAMES = setOf(
        "cache",        
        "tmp",          
        "temp",         
        ".cache",       
        "code_cache",   
        "files_cache",  
        "thumbnails"    
    )
        private val DEFAULT_EXCLUDED_MIME_PREFIXES = setOf(
        "image/", 
        "video/", 
        "audio/"  
    )

        private val ALLOWED_MIME_PREFIXES = setOf(
        "application/vnd.openxmlformats-officedocument.",
        "application/vnd.ms-"
    )

        private val ALLOWED_EXACT_MIME_TYPES = setOf(
        "application/pdf",
        "application/epub+zip",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/zip",
        "application/x-rar-compressed",
        "application/x-7z-compressed",
        "application/gzip",
        "application/vnd.android.package-archive",
        "application/json",
        "application/xml",
        "application/rtf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "text/tab-separated-values",
        "text/xml"
    )

        private val DEFAULT_ALLOWED_EXTENSIONS: Set<String> = buildSet {
        addAll(FileSearchExtensionConfig.categoryExtensions[FileSearchCategory.DOCUMENTS].orEmpty())
        addAll(FileSearchExtensionConfig.categoryExtensions[FileSearchCategory.ARCHIVES].orEmpty())
        addAll(FileSearchExtensionConfig.categoryExtensions[FileSearchCategory.CODE].orEmpty())
    }
        const val MIN_FILE_SIZE_BYTES: Long = 1024L

    /**
     * Check if a file should be included in search results.
     *
     * @param allowedExtensions Override for the allowed extensions set.
     *        When null, uses the default set derived from
     *        [FileSearchExtensionConfig.categoryExtensions] (documents, archives, code).
     * @param excludedMimePrefixes Override for the excluded MIME prefixes.
     *        When null, uses the default set (image/, video/, audio/).
     */
    fun shouldIncludeFile(
        fileName: String,
        mimeType: String,
        size: Long,
        relativePath: String,
        allowedExtensions: Set<String>? = null,
        excludedMimePrefixes: Set<String>? = null
    ): Boolean {
        if (hasExcludedPrefix(fileName)) {
            return false
        }
        if (hasExcludedExtension(fileName)) {
            return false
        }
        if (pathContainsExcludedDirectory(relativePath)) {
            return false
        }
        if (hasExcludedMimeType(mimeType, excludedMimePrefixes)) {
            return false
        }
        if (size < MIN_FILE_SIZE_BYTES) {
            return false
        }
        if (!matchesSupportedDocumentType(fileName, mimeType, allowedExtensions)) {
            return false
        }
        return true
    }

    /**
     * Check if a file matches a supported document type.
     *
     * @param allowedExtensions Override for the allowed extensions set.
     *        When null, uses the default set derived from
     *        [FileSearchExtensionConfig.categoryExtensions].
     */
    fun matchesSupportedDocumentType(
        fileName: String,
        mimeType: String,
        allowedExtensions: Set<String>? = null
    ): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = mimeType.trim().lowercase()

        val hasAllowedMimePrefix = ALLOWED_MIME_PREFIXES.any { prefix ->
            normalizedMimeType.startsWith(prefix)
        }

        val hasAllowedExactMimeType = normalizedMimeType in ALLOWED_EXACT_MIME_TYPES

        val effectiveAllowedExtensions = allowedExtensions ?: DEFAULT_ALLOWED_EXTENSIONS
        val hasAllowedExtension = extension.isNotBlank() && extension in effectiveAllowedExtensions

        return hasAllowedExactMimeType || hasAllowedMimePrefix || hasAllowedExtension
    }
    
        private fun hasExcludedPrefix(fileName: String): Boolean {
        return EXCLUDED_FILENAME_PREFIXES.any { prefix ->
            fileName.startsWith(prefix)
        }
    }
    
        fun hasExcludedExtension(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isEmpty()) {
            return false
        }
        return extension.lowercase() in EXCLUDED_EXTENSIONS
    }
    
        fun pathContainsExcludedDirectory(path: String): Boolean {
        val normalizedPath = path.replace('\\', '/')
        val directories = normalizedPath.split("/")
        
        return directories.any { directory ->
            directory.lowercase() in EXCLUDED_DIRECTORY_NAMES
        }
    }

    /**
     * Check if a MIME type is excluded.
     *
     * @param excludedMimePrefixes Override for the excluded MIME prefixes.
     *        When null, uses the default set (image/, video/, audio/).
     */
    fun hasExcludedMimeType(
        mimeType: String,
        excludedMimePrefixes: Set<String>? = null
    ): Boolean {
        if (mimeType.isEmpty()) {
            return false
        }
        val effectiveExcluded = excludedMimePrefixes ?: DEFAULT_EXCLUDED_MIME_PREFIXES
        return effectiveExcluded.any { prefix ->
            mimeType.lowercase().startsWith(prefix)
        }
    }
    
        fun meetsMinSizeRequirement(size: Long): Boolean {
        return size >= MIN_FILE_SIZE_BYTES
    }
}
