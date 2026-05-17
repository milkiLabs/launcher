package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.SearchSource
import com.milki.launcher.domain.repository.SearchProvider

fun interface SearchProviderFactory {
    fun create(source: SearchSource): SearchProvider
}
