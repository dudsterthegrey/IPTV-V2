package com.streamvault.data.repository

import android.content.Context
import com.streamvault.data.local.dao.MovieDao
import com.streamvault.data.local.dao.SeriesDao
import com.streamvault.data.local.dao.TitleMetadataDao
import com.streamvault.data.mapper.toDomain
import com.streamvault.data.remote.tmdb.TmdbClient
import com.streamvault.data.remote.tmdb.TmdbTitle
import com.streamvault.data.search.FuzzySearchIndex
import com.streamvault.domain.model.CatalogGenre
import com.streamvault.domain.model.CatalogGenreCount
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.CatalogInsightsRepository
import com.streamvault.domain.repository.TopTenEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogInsightsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val titleMetadataDao: TitleMetadataDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val tmdb: TmdbClient,
    private val fuzzySearchIndex: FuzzySearchIndex
) : CatalogInsightsRepository {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val country: MutableStateFlow<String?> by lazy {
        MutableStateFlow(prefs.getString(KEY_COUNTRY, null))
    }

    private data class CachedTopTen(val key: String, val dayStamp: Long, val entries: List<TopTenEntry>)
    private val topTenMutex = Mutex()
    private var cachedTopTen: CachedTopTen? = null

    override fun observeGenreCounts(providerId: Long, isSeries: Boolean): Flow<List<CatalogGenreCount>> =
        titleMetadataDao.observeGenreLists(providerId, if (isSeries) "SERIES" else "MOVIE").map { lists ->
            val counts = IntArray(CatalogGenre.entries.size)
            lists.forEach { stored -> CatalogGenre.decode(stored).forEach { counts[it.ordinal]++ } }
            CatalogGenre.entries
                .filter { counts[it.ordinal] > 0 }
                .map { CatalogGenreCount(it, counts[it.ordinal]) }
                .sortedByDescending { it.count }
        }

    override fun observeMoviesInGenre(providerId: Long, genre: CatalogGenre, limit: Int): Flow<List<Movie>> =
        titleMetadataDao.observeMoviesInGenre(providerId, genrePattern(genre), limit)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeSeriesInGenre(providerId: Long, genre: CatalogGenre, limit: Int): Flow<List<Series>> =
        titleMetadataDao.observeSeriesInGenre(providerId, genrePattern(genre), limit)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun topTenToday(providerId: Long, countryCode: String?): List<TopTenEntry> =
        topTenMutex.withLock {
            val key = "$providerId:${countryCode.orEmpty()}"
            val today = System.currentTimeMillis() / DAY_MS
            cachedTopTen?.takeIf { it.key == key && it.dayStamp == today && it.entries.isNotEmpty() }?.let {
                return@withLock it.entries
            }
            val popular = try {
                if (countryCode == null) tmdb.trendingToday() else tmdb.popularIn(countryCode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emptyList()
            }
            val entries = mutableListOf<TopTenEntry>()
            for (title in popular) {
                if (entries.size >= TOP_TEN) break
                val entry = matchInCatalog(providerId, title, rank = entries.size + 1) ?: continue
                val movieId = entry.movie?.id
                val seriesId = entry.series?.id
                val alreadyListed = entries.any { existing ->
                    (movieId != null && existing.movie?.id == movieId) ||
                        (seriesId != null && existing.series?.id == seriesId)
                }
                if (!alreadyListed) entries += entry
            }
            cachedTopTen = CachedTopTen(key, today, entries)
            entries
        }

    private suspend fun matchInCatalog(providerId: Long, title: TmdbTitle, rank: Int): TopTenEntry? {
        val isSeries = title.mediaType == "tv"
        if (isSeries) {
            val series = titleMetadataDao.seriesByTmdbId(providerId, title.id)
                ?: fuzzySearchIndex.exactTitleMatch(providerId, isSeries = true, title.title, title.year)
                    ?.let { seriesDao.getById(it) }
            return series?.let { TopTenEntry(rank, series = it.toDomain()) }
        }
        val movie = titleMetadataDao.movieByTmdbId(providerId, title.id)
            ?: fuzzySearchIndex.exactTitleMatch(providerId, isSeries = false, title.title, title.year)
                ?.let { movieDao.getById(it) }
        return movie?.let { TopTenEntry(rank, movie = it.toDomain()) }
    }

    override val topTenCountry: StateFlow<String?> get() = country.asStateFlow()

    override suspend fun setTopTenCountry(countryCode: String?) {
        prefs.edit().putString(KEY_COUNTRY, countryCode).apply()
        country.value = countryCode
    }

    private fun genrePattern(genre: CatalogGenre) = "%|${genre.key}|%"

    private companion object {
        const val PREFS_NAME = "catalog_insights"
        const val KEY_COUNTRY = "top_ten_country"
        const val TOP_TEN = 10
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
