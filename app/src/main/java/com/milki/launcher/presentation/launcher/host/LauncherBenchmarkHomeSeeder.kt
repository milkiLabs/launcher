package com.milki.launcher.presentation.launcher.host

import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.AppRepository
import com.milki.launcher.domain.repository.HomeRepository

internal data class LauncherBenchmarkSeedConfig(
    val appCount: Int = 16,
    val columns: Int = 4
)

/**
 * Seeds a deterministic, non-empty homescreen for repeatable benchmark scenarios.
 */
internal class LauncherBenchmarkHomeSeeder(
    private val appRepository: AppRepository,
    private val homeRepository: HomeRepository,
    private val ownPackageName: String,
    private val seedConfig: LauncherBenchmarkSeedConfig = LauncherBenchmarkSeedConfig()
) {

    suspend fun seed(): Boolean {
        val candidateApps = appRepository.getInstalledApps()
            .asSequence()
            .filterNot { app -> app.packageName == ownPackageName }
            .distinctBy { app -> app.packageName }
            .sortedWith(
                compareBy(
                    { app -> app.packageName.lowercase() },
                    { app -> app.activityName.lowercase() }
                )
            )
            .take(seedConfig.appCount)
            .toList()

        if (candidateApps.isEmpty()) {
            return false
        }

        val seededItems = candidateApps.mapIndexed { index, appInfo ->
            HomeItem.PinnedApp.fromAppInfo(appInfo).copy(
                position = GridPosition(
                    row = index / seedConfig.columns,
                    column = index % seedConfig.columns
                )
            )
        }

        homeRepository.replacePinnedItems(seededItems)
        return true
    }
}
