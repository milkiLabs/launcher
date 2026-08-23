package com.milki.launcher.data.search

import android.os.Build
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.FileSearchExtensionConfig
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.repository.SearchRequest
import kotlinx.coroutines.flow.first

/**
 * Search provider for device files (activated by "f" prefix).
 *
 * Behavior:
 * - Permission not granted → permission prompt
 * - Blank query → recent files
 * - Typed query → search + rank files using [QueryRanker]
 */
class FilesSearchProvider(
    private val filesRepository: FilesRepository
) : RecentBackedSearchProvider<FileDocument>() {

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = ProviderId.FILES,
        prefix = "f",
        name = "Files",
        description = "Search documents on device"
    )

    override fun permissionState(request: SearchRequest): PermissionAccessState =
        request.filesPermissionState

    override fun permissionPrompt(state: PermissionAccessState): PermissionRequestResult {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MANAGE_EXTERNAL_STORAGE_PERMISSION
        } else {
            READ_EXTERNAL_STORAGE_PERMISSION
        }

        val requiresSettings = state == PermissionAccessState.REQUIRES_SETTINGS

        return PermissionRequestResult(
            permission = permission,
            providerPrefix = config.prefix,
            message = when {
                requiresSettings -> "File access is blocked. Open Settings to search files"
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> "Allow file access in Settings to search all files"
                else -> "Storage permission required to search files"
            },
            buttonText = if (requiresSettings) "Open Settings" else "Grant Permission"
        )
    }

    override suspend fun searchTypedItems(request: SearchRequest): List<FileDocument> =
        filesRepository.searchFiles(
            query = request.query,
            maxItems = MAX_SEARCH_RESULTS,
            extensionConfig = request.fileSearchExtensionConfig
        )

    override suspend fun resolveRecentItems(request: SearchRequest): List<FileDocument> {
        val recentIds = filesRepository.getRecentFileIds().first()
        if (recentIds.isEmpty()) return emptyList()

        val filesById = filesRepository.getFilesByIds(
            ids = recentIds,
            extensionConfig = request.fileSearchExtensionConfig
        )

        return recentIds.mapNotNull { id -> filesById[id] }
    }

    override val toSearchResult: (FileDocument) -> SearchResult = { FileDocumentSearchResult(it) }

    override val nameSelector: (FileDocument) -> String = { it.name }

    override val identitySelector: (FileDocument) -> String = { it.id.toString() }
}
