package com.streamvault.data.search

import com.streamvault.data.local.dao.SearchDao
import com.streamvault.data.local.dao.SearchHitEntity
import com.streamvault.data.local.dao.SearchTitleRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tokenized titles of one provider's catalog, kept in memory for the typo-tolerant search pass.
 * Rebuilt at most every [MAX_AGE_MS] so catalog refreshes show up. The build runs in its own
 * scope, so a search that stops waiting for it (see SearchRepositoryImpl) doesn't throw the work
 * away: the next search finds it ready.
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
    private val buildScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cachedProviderId: Long? = null
    private var cached: ProviderTitles? = null
    private var building: Deferred<ProviderTitles>? = null
    private var buildingProviderId: Long? = null

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
        val context = coroutineContext
        buildList {
            fun section(enabled: Boolean, type: String, sectionRank: Int, entries: List<Entry>) {
                if (!enabled) return
                val exact = exactHits.filter { it.contentType == type }
                if (exact.size >= minHits) return
                val exactIds = exact.mapTo(HashSet()) { it.contentId }
                val scored = ArrayList<Pair<Entry, Int>>()
                entries.forEachIndexed { index, entry ->
                    // Large catalogs take a while on a TV; let a search that gave up stop here.
                    if (index % CANCEL_CHECK_EVERY == 0) context.ensureActive()
                    if (entry.id in exactIds) return@forEachIndexed
                    FuzzyTitleMatcher.score(queryTokens, entry.tokens)?.let { scored += entry to it }
                }
                scored
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

    private suspend fun titlesFor(providerId: Long): ProviderTitles {
        val job = mutex.withLock {
            val current = cached
            if (current != null && cachedProviderId == providerId &&
                System.currentTimeMillis() - current.builtAt < MAX_AGE_MS
            ) {
                return current
            }
            building?.takeIf { buildingProviderId == providerId && it.isActive }
                ?: buildScope.async {
                    val built = ProviderTitles(
                        builtAt = System.currentTimeMillis(),
                        channels = searchDao.channelTitles(providerId).toEntries(),
                        movies = searchDao.movieTitles(providerId).toEntries(),
                        series = searchDao.seriesTitles(providerId).toEntries()
                    )
                    mutex.withLock {
                        cachedProviderId = providerId
                        cached = built
                    }
                    built
                }.also {
                    building = it
                    buildingProviderId = providerId
                }
        }
        return job.await()
    }

    private fun List<SearchTitleRow>.toEntries(): List<Entry> =
        map { Entry(it.id, it.name, FuzzyTitleMatcher.tokenize(it.name)) }

    private companion object {
        const val MAX_AGE_MS = 15 * 60 * 1000L
        const val MIN_EXACT_HITS = 8
        const val CANCEL_CHECK_EVERY = 2_000
        // Exact hits use match ranks 0..2; typo matches always come after them.
        const val FUZZY_RANK_BASE = 100
        val QUALITY_TOKENS = setOf("4k", "uhd", "fhd", "hd", "sd", "hdr", "1080p", "720p", "2160p")
    }
}
