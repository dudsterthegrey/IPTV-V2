package com.streamvault.data.epg

import android.content.Context
import android.util.Log
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.EpgSourceRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Guides added for every install so a new device doesn't need them typed in by hand. */
internal data class BuiltInEpgSource(val name: String, val url: String)

internal val BUILT_IN_EPG_SOURCES = listOf(
    BuiltInEpgSource("epgshare01 US", "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz"),
    BuiltInEpgSource("epgshare01 UK", "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz"),
)

/**
 * Adds [BUILT_IN_EPG_SOURCES] once and assigns them to each provider once, including providers
 * added later. Each step is remembered, so a source the user deletes or unassigns stays gone.
 * The provider's own guide still wins over name matches from these (see [EpgResolutionEngine]),
 * so they only fill channels that would otherwise be empty.
 */
@Singleton
class BuiltInEpgSourceSeeder @Inject constructor(
    @ApplicationContext context: Context,
    private val epgSourceRepository: EpgSourceRepository,
    private val providerRepository: ProviderRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Watches the provider list for the life of the process; call once at startup. */
    suspend fun run() {
        addSourcesOnce()
        providerRepository.getProviders().collect { providers ->
            providers.forEach { provider -> assignOnce(provider.id) }
        }
    }

    private suspend fun addSourcesOnce() {
        if (prefs.getBoolean(KEY_SOURCES_ADDED, false)) return
        BUILT_IN_EPG_SOURCES.forEach { source ->
            // An error here usually means the URL is already present, which is fine.
            val result = epgSourceRepository.addSource(name = source.name, url = source.url)
            if (result is Result.Error) Log.i(TAG, "Built-in guide '${source.name}' not added: ${result.message}")
        }
        prefs.edit().putBoolean(KEY_SOURCES_ADDED, true).apply()
    }

    private suspend fun assignOnce(providerId: Long) {
        val assigned = prefs.getStringSet(KEY_ASSIGNED_PROVIDERS, emptySet()).orEmpty()
        if (providerId.toString() in assigned) return

        val builtInUrls = BUILT_IN_EPG_SOURCES.map { it.url }.toSet()
        val sources = epgSourceRepository.getAllSources().first().filter { it.url in builtInUrls }
        val existing = epgSourceRepository.getAssignmentsForProvider(providerId).first()
        var priority = existing.maxOfOrNull { it.priority } ?: 0
        sources.filter { source -> existing.none { it.epgSourceId == source.id } }.forEach { source ->
            priority++
            epgSourceRepository.assignSourceToProvider(providerId, source.id, priority)
            // A new source has no programmes yet; download it so the assignment produces matches.
            val refresh = epgSourceRepository.refreshSource(source.id)
            if (refresh is Result.Error) Log.w(TAG, "Built-in guide '${source.name}' refresh failed: ${refresh.message}")
        }
        prefs.edit().putStringSet(KEY_ASSIGNED_PROVIDERS, assigned + providerId.toString()).apply()
    }

    private companion object {
        const val TAG = "BuiltInEpgSources"
        const val PREFS_NAME = "built_in_epg_sources"
        const val KEY_SOURCES_ADDED = "sources_added_v1"
        const val KEY_ASSIGNED_PROVIDERS = "assigned_provider_ids_v1"
    }
}
