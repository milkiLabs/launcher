package com.milki.launcher.domain.search

import com.milki.launcher.domain.model.PrefixConfig
import com.milki.launcher.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefixConfigurationMergerTest {

    @Test
    fun merge_uses_defaults_when_user_has_no_customization() {
        val result = PrefixConfigurationMerger.merge(
            prefixConfigurations = emptyMap(),
            contactsSearchEnabled = true,
            filesSearchEnabled = true,
            sourcePrefixConfigurations = emptyMap()
        )

        assertEquals(
            mapOf(
                ProviderId.CONTACTS to PrefixConfig(listOf("c")),
                ProviderId.FILES to PrefixConfig(listOf("f"))
            ),
            result
        )
    }

    @Test
    fun merge_prefers_user_customization_over_defaults() {
        val customContacts = PrefixConfig(listOf("c", "ct"))
        val result = PrefixConfigurationMerger.merge(
            prefixConfigurations = mapOf(ProviderId.CONTACTS to customContacts),
            contactsSearchEnabled = true,
            filesSearchEnabled = true,
            sourcePrefixConfigurations = emptyMap()
        )

        assertEquals(customContacts, result[ProviderId.CONTACTS])
        assertEquals(PrefixConfig(listOf("f")), result[ProviderId.FILES])
    }

    @Test
    fun merge_excludes_disabled_fixed_providers() {
        val result = PrefixConfigurationMerger.merge(
            prefixConfigurations = emptyMap(),
            contactsSearchEnabled = false,
            filesSearchEnabled = true,
            sourcePrefixConfigurations = emptyMap()
        )

        assertEquals(setOf(ProviderId.FILES), result.keys)
    }

    @Test
    fun merge_appends_source_prefix_configurations() {
        val sources = mapOf(
            "source_web" to PrefixConfig(listOf("w")),
            "source_yt" to PrefixConfig(listOf("yt"))
        )
        val result = PrefixConfigurationMerger.merge(
            prefixConfigurations = emptyMap(),
            contactsSearchEnabled = false,
            filesSearchEnabled = false,
            sourcePrefixConfigurations = sources
        )

        assertEquals(sources, result)
    }
}
