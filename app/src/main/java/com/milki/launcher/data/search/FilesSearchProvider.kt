package com.milki.launcher.data.search

import android.Manifest
import android.os.Build
import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.FileDocumentSearchResult
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.model.ProviderId
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.repository.SearchRequest
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.search.QueryRanker
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
) : SearchProvider {

    private companion object {
        const val MAX_RESULTS = 10
    }

    override val config: SearchProviderConfig = SearchProviderConfig(
        providerId = ProviderId.FILES,
        prefix = "f",
        name = "Files",
        description = "Search documents on device"
    )

    override suspend fun search(request: SearchRequest): List<SearchResult> {
        if (!request.filesPermissionState.isGranted) {
            return listOf(permissionPrompt(request.filesPermissionState))
        }

        if (request.query.isBlank()) {
            return resolveRecentFiles()
        }

        val files = filesRepository.searchFiles(query = request.query, maxItems = MAX_RESULTS)
        val recentFiles = resolveRecentFilesForBoosting()

        return QueryRanker.rank(
            items = files,
            query = request.query,
            recentItems = recentFiles,
            nameSelector = { it.name },
            identitySelector = { it.id.toString() },
        )
            .map { FileDocumentSearchResult(it) }
            .take(MAX_RESULTS)
    }

    private fun permissionPrompt(state: PermissionAccessState): PermissionRequestResult {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
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

    private suspend fun resolveRecentFiles(): List<SearchResult> {
        val recentIds = filesRepository.getRecentFileIds().first()
        if (recentIds.isEmpty()) return emptyList()

        val filesById = filesRepository.getFilesByIds(recentIds)

        return recentIds.take(MAX_RESULTS).mapNotNull { id ->
            filesById[id]?.let(::FileDocumentSearchResult)
        }
    }

    private suspend fun resolveRecentFilesForBoosting(): List<FileDocument> {
        val recentIds = filesRepository.getRecentFileIds().first()
        if (recentIds.isEmpty()) return emptyList()

        val filesById = filesRepository.getFilesByIds(recentIds)

        return recentIds.mapNotNull { id -> filesById[id] }
    }
}
