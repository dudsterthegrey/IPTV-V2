package com.streamvault.data.search

import com.streamvault.data.local.dao.SearchDao
import com.streamvault.data.local.dao.SearchHitEntity
import com.streamvault.data.local.dao.SearchTitleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tokenized titles of one provider's catalog, kept in memory for the typo-tolerant search pass.
 * Rebuilt at most every [MAX_AGE_MS] so catalog refreshes show up.
 */
@Singleton
class FuzzySearchIndex @Inject constructor(
    private val searchDao: SearchDao
) {
    private class Entry(val id: Long, val title: String, val tokens: List<String>)

    private class ProviderTitles(
        val builtAt: Long,
        val channels: List<Entry>,
        val movies: List<Entry>,
        val series: List<Entry>
    )

    private val mutex = Mutex()
    private var cachedProviderId: Long? = null
    private var cached: ProviderTitles? = null

    /**
     * Typo-tolerant hits for the sections that found fewer than [minHits] exact hits, best first,
     * skipping ids already in [exactHits]. Their match rank sorts them after every exact hit.
     */
    suspend fun extraHits(
        providerId: Long,
        query: String,
        exactHits: List<SearchHitEntity>,
        includeLive: Boolean,
        includeMovies: Boolean,
        includeSeries: Boolean,
        maxPerSection: Int,
        minHits: Int = MIN_EXACT_HITS
    ): List<SearchHitEntity> = withContext(Dispatchers.Default) {
        val queryTokens = FuzzyTitleMatcher.tokenize(query)
        if (queryTokens.none { it.length >= 3 }) return@withContext emptyList()
        val titles = titlesFor(providerId)
        buildList {
            fun section(enabled: Boolean, type: String, sectionRank: Int, entries: List<Entry>) {
                if (!enabled) return
                val exact = exactHits.filter { it.contentType == type }
                if (exact.size >= minHits) return
                val exactIds = exact.mapTo(HashSet()) { it.contentId }
                entries.asSequence()
                    .filter { it.id !in exactIds }
                    .mapNotNull { entry -> FuzzyTitleMatcher.score(queryTokens, entry.tokens)?.let { entry to it } }
                    .sortedWith(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.title.length })
                    .take((maxPerSection - exact.size).coerceAtLeast(0))
                    .forEach { (entry, score) ->
                        add(SearchHitEntity(type, entry.id, entry.title, sectionRank, FUZZY_RANK_BASE + score))
                    }
            }
            section(includeLive, "LIVE", 0, titles.channels)
            section(includeMovies, "MOVIE", 1, titles.movies)
            section(includeSeries, "SERIES", 2, titles.series)
        }
    }

    private suspend fun titlesFor(providerId: Long): ProviderTitles = mutex.withLock {
        val current = cached
        if (current != null && cachedProviderId == providerId &&
            System.currentTimeMillis() - current.builtAt < MAX_AGE_MS
        ) {
            return current
        }
        val built = ProviderTitles(
            builtAt = System.currentTimeMillis(),
            channels = searchDao.channelTitles(providerId).toEntries(),
            movies = searchDao.movieTitles(providerId).toEntries(),
            series = searchDao.seriesTitles(providerId).toEntries()
        )
        cachedProviderId = providerId
        cached = built
        built
    }

    private fun List<SearchTitleRow>.toEntries(): List<Entry> =
        map { Entry(it.id, it.name, FuzzyTitleMatcher.tokenize(it.name)) }

    private companion object {
        const val MAX_AGE_MS = 15 * 60 * 1000L
        const val MIN_EXACT_HITS = 8
        // Exact hits use match ranks 0..2; typo matches always come after them.
        const val FUZZY_RANK_BASE = 100
    }
}
