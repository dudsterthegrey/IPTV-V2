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
import com.streamvault.domain.model.CatalogShelfFilter
import com.streamvault.domain.model.CatalogShelfFilterOptions
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
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

    override fun observeFilteredRecentMovies(providerId: Long, filter: CatalogShelfFilter, limit: Int): Flow<List<Movie>> {
        val year = "COALESCE(m.year, CAST(substr(COALESCE(mo.year, mo.release_date), 1, 4) AS INTEGER))"
        val query = filteredQuery(
            select = "SELECT mo.* FROM movies mo LEFT JOIN title_metadata m " +
                "ON m.provider_id = mo.provider_id AND m.content_type = 'MOVIE' AND m.remote_id = mo.stream_id " +
                "WHERE mo.provider_id = ? AND mo.added_at > 0",
            rating = "mo.rating",
            year = year,
            order = "ORDER BY mo.added_at DESC, mo.name ASC, mo.id ASC",
            providerId = providerId,
            filter = filter,
            limit = limit
        )
        return titleMetadataDao.observeFilteredMovies(query).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeFilteredRecentSeries(providerId: Long, filter: CatalogShelfFilter, limit: Int): Flow<List<Series>> {
        val year = "COALESCE(m.year, CAST(substr(se.release_date, 1, 4) AS INTEGER))"
        val query = filteredQuery(
            select = "SELECT se.* FROM series se LEFT JOIN title_metadata m " +
                "ON m.provider_id = se.provider_id AND m.content_type = 'SERIES' AND m.remote_id = se.series_id " +
                "WHERE se.provider_id = ? AND se.last_modified > 0",
            rating = "se.rating",
            year = year,
            order = "ORDER BY se.last_modified DESC, se.name ASC",
            providerId = providerId,
            filter = filter,
            limit = limit
        )
        return titleMetadataDao.observeFilteredSeries(query).map { rows -> rows.map { it.toDomain() } }
    }

    /** The shelf's own query plus one condition per active filter; genres and countries match any. */
    private fun filteredQuery(
        select: String,
        rating: String,
        year: String,
        order: String,
        providerId: Long,
        filter: CatalogShelfFilter,
        limit: Int
    ): SimpleSQLiteQuery {
        val sql = StringBuilder(select)
        val args = mutableListOf<Any>(providerId)
        if (filter.genres.isNotEmpty()) {
            sql.append(" AND (").append(filter.genres.joinToString(" OR ") { "m.genres LIKE ?" }).append(")")
            filter.genres.forEach { args += genrePattern(it) }
        }
        if (filter.countries.isNotEmpty()) {
            sql.append(" AND (").append(filter.countries.joinToString(" OR ") { "m.countries LIKE ?" }).append(")")
            filter.countries.forEach { args += "%|$it|%" }
        }
        if (filter.minRating > 0) {
            sql.append(" AND $rating >= ?")
            args += filter.minRating
        }
        filter.year.range()?.let { range ->
            sql.append(" AND $year BETWEEN ? AND ?")
            args += range.first
            args += range.last
        }
        sql.append(' ').append(order).append(" LIMIT ?")
        args += limit
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun observeShelfFilterOptions(providerId: Long, isSeries: Boolean): Flow<CatalogShelfFilterOptions> {
        val type = if (isSeries) "SERIES" else "MOVIE"
        // While the metadata worker is filling titles in, these change often; settle first.
        return combine(
            observeGenreCounts(providerId, isSeries).debounce(OPTIONS_SETTLE_MS),
            titleMetadataDao.observeCountryLists(providerId, type).debounce(OPTIONS_SETTLE_MS)
        ) { genres, countryLists ->
            val counts = HashMap<String, Int>()
            countryLists.forEach { stored ->
                stored.split('|').filter { it.length == 2 }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
            CatalogShelfFilterOptions(
                genres = genres.map { it.genre },
                countries = counts.entries.sortedByDescending { it.value }.map { it.key }
            )
        }.distinctUntilChanged()
    }

    private val shelfFilters: Map<Boolean, MutableStateFlow<CatalogShelfFilter>> by lazy {
        listOf(false, true).associateWith { isSeries ->
            MutableStateFlow(CatalogShelfFilter.decode(prefs.getString(shelfFilterKey(isSeries), null)))
        }
    }

    override fun observeRecentShelfFilter(isSeries: Boolean): Flow<CatalogShelfFilter> =
        shelfFilters.getValue(isSeries).asStateFlow()

    override suspend fun setRecentShelfFilter(isSeries: Boolean, filter: CatalogShelfFilter) {
        prefs.edit().putString(shelfFilterKey(isSeries), filter.encode()).apply()
        shelfFilters.getValue(isSeries).value = filter
    }

    private fun shelfFilterKey(isSeries: Boolean) = if (isSeries) KEY_RECENT_SERIES_FILTER else KEY_RECENT_MOVIES_FILTER

    private companion object {
        const val PREFS_NAME = "catalog_insights"
        const val KEY_COUNTRY = "top_ten_country"
        const val KEY_RECENT_MOVIES_FILTER = "recent_movies_filter"
        const val OPTIONS_SETTLE_MS = 2_000L
        const val KEY_RECENT_SERIES_FILTER = "recent_series_filter"
        const val TOP_TEN = 10
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
