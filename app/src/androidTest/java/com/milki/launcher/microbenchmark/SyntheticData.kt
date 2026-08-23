package com.milki.launcher.microbenchmark

import com.milki.launcher.domain.model.AppInfo
import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.SearchProviderConfig
import com.milki.launcher.domain.model.SearchResult
import com.milki.launcher.domain.repository.SearchProvider
import com.milki.launcher.domain.repository.SearchRequest
import com.milki.launcher.domain.search.SearchProviderRegistry
import kotlin.random.Random

object SyntheticApps {

    private val nameWords = listOf(
        "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel",
        "India", "Juliet", "Kilo", "Lima", "Mike", "November", "Oscar", "Papa",
        "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey",
        "Xray", "Yankee", "Zulu", "Nova", "Orbit", "Pixel", "Quartz", "Rocket",
        "Storm", "Titan", "Ultra", "Vector", "Wave"
    )

    private val categoryWords = listOf(
        "Music", "Maps", "Chat", "Mail", "Notes", "Photos", "Store", "Player",
        "Browser", "Wallet", "Fitness", "Reader", "Cloud", "Radio", "Camera",
        "Editor", "Backup", "Tracker", "Studio", "Terminal"
    )

    private val packagePrefixes = listOf(
        "com.acme", "dev.open", "org.libre", "io.cloud", "net.fast",
        "app.zen", "co.pico", "com.mega"
    )

    fun installed(count: Int): List<AppInfo> {
        val random = Random(42)
        val apps = ArrayList<AppInfo>(count)
        for (i in 0 until count) {
            val useTwoWords = random.nextInt(4) != 0
            val name = if (useTwoWords) {
                "${nameWords[random.nextInt(nameWords.size)]} ${categoryWords[random.nextInt(categoryWords.size)]}"
            } else {
                nameWords[random.nextInt(nameWords.size)]
            }
            val packageName = "${packagePrefixes[random.nextInt(packagePrefixes.size)]}.app$i"
            apps.add(AppInfo(name = name, packageName = packageName, activityName = "$packageName.MainActivity"))
        }
        return apps
    }
}

object SyntheticProviders {

    private class StubProvider(
        override val config: SearchProviderConfig,
    ) : SearchProvider {
        override suspend fun search(request: SearchRequest): List<SearchResult> = emptyList()
    }

    fun registry(providerCount: Int): SearchProviderRegistry {
        val providers = (0 until providerCount).map { index ->
            StubProvider(
                SearchProviderConfig(
                    providerId = if (index == 0) "web" else "source_$index",
                    prefix = "p$index",
                    name = "Provider $index",
                    description = "Benchmark provider $index",
                ),
            )
        }
        return SearchProviderRegistry(providers.toList()).also { registry ->
            val configurations = providers.associate { provider ->
                provider.config.providerId to PrefixConfig(listOf(provider.config.prefix, "x${provider.config.prefix}"))
            }
            registry.updatePrefixConfigurations(configurations)
        }
    }
}
