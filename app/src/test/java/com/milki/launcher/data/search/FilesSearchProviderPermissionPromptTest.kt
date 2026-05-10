package com.milki.launcher.data.search

import com.milki.launcher.domain.model.FileDocument
import com.milki.launcher.domain.model.PermissionAccessState
import com.milki.launcher.domain.model.PermissionRequestResult
import com.milki.launcher.domain.repository.FilesRepository
import com.milki.launcher.domain.repository.SearchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesSearchProviderPermissionPromptTest {

    @Test
    fun blocked_files_permission_surfaces_open_settings_prompt() = runBlocking {
        val provider = FilesSearchProvider(FakeFilesRepository())

        val results = provider.search(
            SearchRequest(
                query = "report",
                maxResults = 8,
                filesPermissionState = PermissionAccessState.REQUIRES_SETTINGS
            )
        )

        val prompt = results.single() as PermissionRequestResult

        assertEquals("Open Settings", prompt.buttonText)
        assertTrue(prompt.message.contains("Open Settings"))
    }

    private class FakeFilesRepository : FilesRepository {
        override fun hasFilesPermission(): Boolean = false

        override suspend fun searchFiles(query: String, maxItems: Int): List<FileDocument> = emptyList()

        override suspend fun getRecentFiles(limit: Int): List<FileDocument> = emptyList()
    }
}
