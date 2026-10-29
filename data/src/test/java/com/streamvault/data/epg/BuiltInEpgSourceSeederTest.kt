package com.streamvault.data.epg

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.EpgSource
import com.streamvault.domain.model.LegacyProvider as Provider
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.EpgSourceRepository
import com.streamvault.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BuiltInEpgSourceSeederTest {
    private val epgSources: EpgSourceRepository = mock()
    private val providers: ProviderRepository = mock()
    private val context = RuntimeEnvironment.getApplication()
    private val stored = BUILT_IN_EPG_SOURCES.mapIndexed { index, source ->
        EpgSource(id = index + 10L, name = source.name, url = source.url)
    }

    @Before
    fun setUp() = runTest {
        context.getSharedPreferences("built_in_epg_sources", 0).edit().clear().commit()
        whenever(epgSources.addSource(any(), any(), any(), anyOrNull())).thenReturn(Result.success(stored.first()))
        whenever(epgSources.getAllSources()).thenReturn(flowOf(stored))
        whenever(epgSources.getAssignmentsForProvider(any())).thenReturn(flowOf(emptyList()))
        whenever(epgSources.assignSourceToProvider(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(epgSources.refreshSource(any())).thenReturn(Result.success(Unit))
    }

    @Test
    fun `sources are added once and each provider is assigned once`() = runTest {
        whenever(providers.getProviders()).thenReturn(flowOf(listOf(provider(1))))
        BuiltInEpgSourceSeeder(context, epgSources, providers).run()

        verify(epgSources, times(BUILT_IN_EPG_SOURCES.size)).addSource(any(), any(), any(), anyOrNull())
        stored.forEach { verify(epgSources).assignSourceToProvider(eq(1L), eq(it.id), any()) }

        // Next launch, with a provider added since: only the new one is assigned.
        whenever(providers.getProviders()).thenReturn(flowOf(listOf(provider(1), provider(2))))
        BuiltInEpgSourceSeeder(context, epgSources, providers).run()

        verify(epgSources, times(BUILT_IN_EPG_SOURCES.size)).addSource(any(), any(), any(), anyOrNull())
        stored.forEach {
            verify(epgSources, times(1)).assignSourceToProvider(eq(1L), eq(it.id), any())
            verify(epgSources).assignSourceToProvider(eq(2L), eq(it.id), any())
        }
    }

    @Test
    fun `a built-in source the user deleted is not assigned to new providers`() = runTest {
        context.getSharedPreferences("built_in_epg_sources", 0).edit()
            .putBoolean("sources_added_v1", true).commit()
        whenever(epgSources.getAllSources()).thenReturn(flowOf(emptyList()))
        whenever(providers.getProviders()).thenReturn(flowOf(listOf(provider(3))))

        BuiltInEpgSourceSeeder(context, epgSources, providers).run()

        verify(epgSources, never()).addSource(any(), any(), any(), anyOrNull())
        verify(epgSources, never()).assignSourceToProvider(any(), any(), any())
        assertThat(context.getSharedPreferences("built_in_epg_sources", 0)
            .getStringSet("assigned_provider_ids_v1", emptySet())).contains("3")
    }

    private fun provider(id: Long) = Provider(
        id = id, name = "P$id", type = ProviderType.XTREAM_CODES, serverUrl = "https://example.invalid"
    )
}
