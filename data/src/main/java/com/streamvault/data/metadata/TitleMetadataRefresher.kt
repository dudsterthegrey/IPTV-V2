package com.streamvault.data.metadata

import android.content.Context
import android.util.Log
import com.streamvault.data.local.dao.CatalogTitleRow
import com.streamvault.data.local.dao.TitleMetadataDao
import com.streamvault.data.local.entity.TitleMetadataEntity
import com.streamvault.data.remote.tmdb.TmdbClient
import com.streamvault.data.remote.tmdb.TmdbRateLimitedException
import com.streamvault.domain.model.CatalogGenre
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills [TitleMetadataDao] for a provider's movies and series, in three steps that can each stop
 * and resume: genres from the provider's own data (local, fast), TMDB matching (genres, anime,
 * IMDb id), and IMDb ratings from IMDb's public ratings file, which are then applied to the
 * catalog so rating sorts and "Top rated" use them.
 */
@Singleton
class TitleMetadataRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: TitleMetadataDao,
    private val tmdb: TmdbClient,
    private val okHttpClient: OkHttpClient
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** Adds or updates genre rows for every catalog title; keeps what TMDB already found. */
    suspend fun refreshLocalGenres(providerId: Long) = withContext(Dispatchers.Default) {
        refreshSection(providerId, MOVIE, dao.movieTitles(providerId))
        refreshSection(providerId, SERIES, dao.seriesTitles(providerId))
    }

    private suspend fun refreshSection(providerId: Long, contentType: String, titles: List<CatalogTitleRow>) {
        val existing = dao.getAll(providerId, contentType).associateBy { it.remoteId }
        val changed = titles.mapNotNull { row ->
            val current = existing[row.remoteId]
            val genres = CatalogGenre.encode(
                GenreClassifier.classify(
                    providerGenre = row.genre,
                    categoryName = row.categoryName,
                    tmdbGenreIds = current?.tmdbGenreIds.toGenreIds(),
                    originalLanguage = current?.originalLanguage
                )
            )
            val year = row.yearText?.let(YEAR::find)?.value?.toIntOrNull()
            when {
                current == null -> TitleMetadataEntity(
                    providerId = providerId,
                    contentType = contentType,
                    remoteId = row.remoteId,
                    title = row.name,
                    year = year,
                    genres = genres
                )
                current.genres != genres || current.title != row.name || current.year != year ->
                    current.copy(title = row.name, year = year, genres = genres)
                else -> null
            }
        }
        changed.chunked(UPSERT_CHUNK).forEach { dao.upsertAll(it) }
        val gone = existing.keys - titles.mapTo(HashSet()) { it.remoteId }
        gone.chunked(UPSERT_CHUNK).forEach { dao.delete(providerId, contentType, it) }
    }

    /**
     * Matches up to [maxTitles] unchecked titles on TMDB. Stops early when [shouldPause] says so
     * (something started playing). Returns how many titles are still waiting.
     */
    suspend fun lookUpOnTmdb(providerId: Long, maxTitles: Int, shouldPause: () -> Boolean): Int {
        if (!tmdb.isConfigured) return 0
        var processed = 0
        var lastPending = Int.MAX_VALUE
        val gate = Semaphore(TMDB_PARALLEL_LOOKUPS)
        while (processed < maxTitles && !shouldPause()) {
            val batch = dao.pendingTmdbLookups(providerId, TMDB_BATCH)
            if (batch.isEmpty()) break
            try {
                coroutineScope {
                    batch.map { row -> async { gate.withPermit { lookUp(row) } } }.awaitAll()
                }
            } catch (rateLimited: TmdbRateLimitedException) {
                Log.i(TAG, "TMDB rate limit; pausing lookups")
                delay(RATE_LIMIT_BACKOFF_MS)
            }
            processed += batch.size
            // Lookups that failed on the network stay pending; stop rather than retry them in a loop.
            val pending = dao.pendingTmdbLookupCount(providerId)
            if (pending >= lastPending) break
            lastPending = pending
        }
        return dao.pendingTmdbLookupCount(providerId)
    }

    private suspend fun lookUp(row: TitleMetadataEntity) {
        val isSeries = row.contentType == SERIES
        val match = try {
            tmdb.findTitle(row.title, row.year, isSeries)
        } catch (rateLimited: TmdbRateLimitedException) {
            throw rateLimited
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Network trouble: leave it unchecked for the next run.
            return
        }
        val imdbId = match?.let { runCatching { tmdb.imdbId(it.id, isSeries) }.getOrNull() }
        val genreIds = match?.genreIds.orEmpty()
        val genres = GenreClassifier.classify(
            providerGenre = null,
            categoryName = null,
            tmdbGenreIds = genreIds,
            originalLanguage = match?.originalLanguage
        ) + CatalogGenre.decode(row.genres)
        dao.updateTmdbMatch(
            providerId = row.providerId,
            contentType = row.contentType,
            remoteId = row.remoteId,
            tmdbId = match?.id,
            imdbId = imdbId,
            originalLanguage = match?.originalLanguage,
            tmdbGenreIds = genreIds.joinToString(","),
            popularity = match?.popularity ?: 0.0,
            genres = CatalogGenre.encode(withAnimeRule(genres, match?.originalLanguage)),
            checkedAt = System.currentTimeMillis()
        )
    }

    /**
     * Downloads IMDb's ratings (about 8 MB, refreshed weekly or once enough new titles were
     * matched) and applies them to [providerIds]' catalogs. Skipped when nothing has an IMDb id.
     */
    suspend fun refreshImdbRatings(providerIds: List<Long>, force: Boolean = false) = withContext(Dispatchers.IO) {
        val imdbIds = dao.allImdbIds().toHashSet()
        if (imdbIds.isEmpty()) return@withContext
        val lastDownload = prefs.getLong(KEY_RATINGS_AT, 0L)
        val idsAtLastDownload = prefs.getInt(KEY_RATINGS_ID_COUNT, 0)
        val stale = System.currentTimeMillis() - lastDownload > RATINGS_MAX_AGE_MS
        val manyNewIds = imdbIds.size - idsAtLastDownload >= NEW_IDS_FOR_REFRESH
        if (force || stale || manyNewIds) {
            val ratings = downloadRatings(imdbIds) ?: return@withContext
            ratings.entries.chunked(UPSERT_CHUNK).forEach { chunk ->
                dao.updateImdbRatings(chunk.associate { it.key to it.value })
            }
            prefs.edit()
                .putLong(KEY_RATINGS_AT, System.currentTimeMillis())
                .putInt(KEY_RATINGS_ID_COUNT, imdbIds.size)
                .apply()
            Log.i(TAG, "IMDb ratings applied: ${ratings.size} of ${imdbIds.size} titles")
        }
        providerIds.forEach { providerId ->
            dao.applyImdbRatingsToMovies(providerId)
            dao.applyImdbRatingsToSeries(providerId)
        }
    }

    private fun downloadRatings(wanted: Set<String>): Map<String, Pair<Double, Int>>? {
        val request = Request.Builder().url(IMDB_RATINGS_URL).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val ratings = HashMap<String, Pair<Double, Int>>(wanted.size)
                BufferedReader(InputStreamReader(GZIPInputStream(body.byteStream()), Charsets.UTF_8)).useLines { lines ->
                    // tconst \t averageRating \t numVotes
                    lines.drop(1).forEach { line ->
                        val firstTab = line.indexOf('\t')
                        if (firstTab <= 0) return@forEach
                        val id = line.substring(0, firstTab)
                        if (id !in wanted) return@forEach
                        val secondTab = line.indexOf('\t', firstTab + 1)
                        if (secondTab <= 0) return@forEach
                        val rating = line.substring(firstTab + 1, secondTab).toDoubleOrNull() ?: return@forEach
                        val votes = line.substring(secondTab + 1).trim().toIntOrNull() ?: 0
                        ratings[id] = rating to votes
                    }
                }
                ratings
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "IMDb ratings download failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun withAnimeRule(genres: Set<CatalogGenre>, originalLanguage: String?): Set<CatalogGenre> =
        if (CatalogGenre.ANIMATION in genres && originalLanguage.equals("ja", ignoreCase = true)) {
            genres + CatalogGenre.ANIME
        } else {
            genres
        }

    private fun String?.toGenreIds(): List<Int> =
        this.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }

    companion object {
        const val MOVIE = "MOVIE"
        const val SERIES = "SERIES"
        private const val TAG = "TitleMetadata"
        private const val PREFS_NAME = "title_metadata"
        private const val KEY_RATINGS_AT = "imdb_ratings_downloaded_at"
        private const val KEY_RATINGS_ID_COUNT = "imdb_ratings_id_count"
        private const val IMDB_RATINGS_URL = "https://datasets.imdbws.com/title.ratings.tsv.gz"
        private const val RATINGS_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val NEW_IDS_FOR_REFRESH = 2_000
        private const val UPSERT_CHUNK = 500
        private const val TMDB_BATCH = 60
        private const val TMDB_PARALLEL_LOOKUPS = 6
        private const val RATE_LIMIT_BACKOFF_MS = 10_000L
        private val YEAR = Regex("(19|20)\\d{2}")
    }
}
