
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
        private val EXCLUDED_MIME_PREFIXES = setOf(
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

        private val ALLOWED_EXTENSIONS = setOf(
        "pdf", "epub", "txt", "rtf", "md",
        "doc", "docx", "odt",
        "xls", "xlsx", "ods", "csv", "tsv",
        "ppt", "pptx", "odp",
        "json", "xml", "yaml", "yml", "toml", "ini", "conf",
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk"
    )
        const val MIN_FILE_SIZE_BYTES: Long = 1024L
        fun shouldIncludeFile(
        fileName: String,
        mimeType: String,
        size: Long,
        relativePath: String
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
        if (hasExcludedMimeType(mimeType)) {
            return false
        }
        if (size < MIN_FILE_SIZE_BYTES) {
            return false
        }
        if (!matchesSupportedDocumentType(fileName, mimeType)) {
            return false
        }
        return true
    }

        fun matchesSupportedDocumentType(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = mimeType.trim().lowercase()

        val hasAllowedMimePrefix = ALLOWED_MIME_PREFIXES.any { prefix ->
            normalizedMimeType.startsWith(prefix)
        }

        val hasAllowedExactMimeType = normalizedMimeType in ALLOWED_EXACT_MIME_TYPES

        val hasAllowedExtension = extension.isNotBlank() && extension in ALLOWED_EXTENSIONS

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
    
        fun hasExcludedMimeType(mimeType: String): Boolean {
        if (mimeType.isEmpty()) {
            return false
        }
        return EXCLUDED_MIME_PREFIXES.any { prefix ->
            mimeType.lowercase().startsWith(prefix)
        }
    }
    
        fun meetsMinSizeRequirement(size: Long): Boolean {
        return size >= MIN_FILE_SIZE_BYTES
    }
}
