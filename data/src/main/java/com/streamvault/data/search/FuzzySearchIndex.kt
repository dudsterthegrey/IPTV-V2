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

    /**
     * The catalog id of a title whose words equal [title]'s (ignoring a year and quality tags),
     * preferring one whose year is within a year of [year]. Null when none matches exactly.
     */
    suspend fun exactTitleMatch(providerId: Long, isSeries: Boolean, title: String, year: Int?): Long? =
        withContext(Dispatchers.Default) {
            val wanted = comparableKey(FuzzyTitleMatcher.tokenize(title))
            if (wanted.isEmpty()) return@withContext null
            val titles = titlesFor(providerId)
            val candidates = (if (isSeries) titles.series else titles.movies)
                .filter { comparableKey(it.tokens) == wanted }
            candidates.firstOrNull { entry ->
                val entryYear = entry.tokens.lastOrNull()?.takeIf { it.length == 4 }?.toIntOrNull()
                year != null && entryYear != null && kotlin.math.abs(entryYear - year) <= 1
            }?.id ?: candidates.firstOrNull()?.id
        }

    /** Words without quality tags or a trailing year ("1883 (2021)" → "1883"). */
    private fun comparableKey(tokens: List<String>): String {
        val words = tokens.filterNot { it in QUALITY_TOKENS }
        val last = words.lastOrNull()
        val trailingYear = words.size > 1 && last != null && last.length == 4 && last.toIntOrNull() in 1900..2099
        return (if (trailingYear) words.dropLast(1) else words).joinToString(" ")
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
        val QUALITY_TOKENS = setOf("4k", "uhd", "fhd", "hd", "sd", "hdr", "1080p", "720p", "2160p")
    }
}
