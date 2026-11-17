package com.streamvault.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamvault.data.local.entity.MovieEntity
import com.streamvault.data.local.entity.SeriesEntity
import com.streamvault.data.local.entity.TitleMetadataEntity
import kotlinx.coroutines.flow.Flow

/** A catalog title as the genre pass needs it. */
data class CatalogTitleRow(
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "year_text") val yearText: String?
)

@Dao
interface TitleMetadataDao {
    @Query("SELECT stream_id AS remote_id, name, genre, category_name, COALESCE(year, release_date) AS year_text FROM movies WHERE provider_id = :providerId")
    suspend fun movieTitles(providerId: Long): List<CatalogTitleRow>

    @Query("SELECT series_id AS remote_id, name, genre, category_name, release_date AS year_text FROM series WHERE provider_id = :providerId")
    suspend fun seriesTitles(providerId: Long): List<CatalogTitleRow>

    @Query("SELECT * FROM title_metadata WHERE provider_id = :providerId AND content_type = :contentType")
    suspend fun getAll(providerId: Long, contentType: String): List<TitleMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TitleMetadataEntity>)

    @Query("DELETE FROM title_metadata WHERE provider_id = :providerId AND content_type = :contentType AND remote_id IN (:remoteIds)")
    suspend fun delete(providerId: Long, contentType: String, remoteIds: List<Long>)

    /**
     * Titles TMDB has not been asked about yet: series before movies, and animation first within
     * each (that is where anime is hiding), then newest first.
     */
    @Query(
        """
        SELECT * FROM title_metadata
        WHERE provider_id = :providerId AND tmdb_checked_at = 0
        ORDER BY CASE content_type WHEN 'SERIES' THEN 0 ELSE 1 END,
                 CASE WHEN genres LIKE '%|ANIMATION|%' THEN 0 ELSE 1 END,
                 remote_id DESC
        LIMIT :limit
        """
    )
    suspend fun pendingTmdbLookups(providerId: Long, limit: Int): List<TitleMetadataEntity>

    @Query("SELECT COUNT(*) FROM title_metadata WHERE provider_id = :providerId AND tmdb_checked_at = 0")
    suspend fun pendingTmdbLookupCount(providerId: Long): Int

    @Query(
        """
        UPDATE title_metadata
        SET tmdb_id = :tmdbId, imdb_id = :imdbId, original_language = :originalLanguage,
            tmdb_genre_ids = :tmdbGenreIds, popularity = :popularity, genres = :genres,
            tmdb_checked_at = :checkedAt, countries = :countries
        WHERE provider_id = :providerId AND content_type = :contentType AND remote_id = :remoteId
        """
    )
    suspend fun updateTmdbMatch(
        providerId: Long,
        contentType: String,
        remoteId: Long,
        tmdbId: Long?,
        imdbId: String?,
        originalLanguage: String?,
        tmdbGenreIds: String,
        popularity: Double,
        genres: String,
        checkedAt: Long,
        countries: String?
    )

    @Query("SELECT DISTINCT imdb_id FROM title_metadata WHERE imdb_id IS NOT NULL AND imdb_id != ''")
    suspend fun allImdbIds(): List<String>

    @Query("UPDATE title_metadata SET imdb_rating = :rating, imdb_votes = :votes WHERE imdb_id = :imdbId")
    suspend fun updateImdbRating(imdbId: String, rating: Double, votes: Int)

    @Transaction
    suspend fun updateImdbRatings(ratings: Map<String, Pair<Double, Int>>) {
        ratings.forEach { (imdbId, value) -> updateImdbRating(imdbId, value.first, value.second) }
    }

    /** Puts IMDb ratings into the catalog so every rating sort, "Top rated" and card uses them. */
    @Query(
        """
        UPDATE movies SET rating = (
            SELECT CASE WHEN m.imdb_votes >= ${TitleMetadataTriggers.MIN_TRUSTED_VOTES} THEN m.imdb_rating ELSE 0 END
            FROM title_metadata m
            WHERE m.provider_id = movies.provider_id AND m.content_type = 'MOVIE' AND m.remote_id = movies.stream_id
        )
        WHERE provider_id = :providerId AND EXISTS (
            SELECT 1 FROM title_metadata m
            WHERE m.provider_id = movies.provider_id AND m.content_type = 'MOVIE'
              AND m.remote_id = movies.stream_id AND m.imdb_rating IS NOT NULL
        )
        """
    )
    suspend fun applyImdbRatingsToMovies(providerId: Long)

    @Query(
        """
        UPDATE series SET rating = (
            SELECT CASE WHEN m.imdb_votes >= ${TitleMetadataTriggers.MIN_TRUSTED_VOTES} THEN m.imdb_rating ELSE 0 END
            FROM title_metadata m
            WHERE m.provider_id = series.provider_id AND m.content_type = 'SERIES' AND m.remote_id = series.series_id
        )
        WHERE provider_id = :providerId AND EXISTS (
            SELECT 1 FROM title_metadata m
            WHERE m.provider_id = series.provider_id AND m.content_type = 'SERIES'
              AND m.remote_id = series.series_id AND m.imdb_rating IS NOT NULL
        )
        """
    )
    suspend fun applyImdbRatingsToSeries(providerId: Long)

    @Query("SELECT genres FROM title_metadata WHERE provider_id = :providerId AND content_type = :contentType AND genres != ''")
    fun observeGenreLists(providerId: Long, contentType: String): Flow<List<String>>

    @Query(
        """
        SELECT mo.* FROM movies mo
        INNER JOIN title_metadata m
            ON m.provider_id = mo.provider_id AND m.content_type = 'MOVIE' AND m.remote_id = mo.stream_id
        WHERE mo.provider_id = :providerId AND m.genres LIKE :genrePattern
        ORDER BY mo.rating DESC, mo.name ASC
        LIMIT :limit
        """
    )
    fun observeMoviesInGenre(providerId: Long, genrePattern: String, limit: Int): Flow<List<MovieEntity>>

    @Query(
        """
        SELECT se.* FROM series se
        INNER JOIN title_metadata m
            ON m.provider_id = se.provider_id AND m.content_type = 'SERIES' AND m.remote_id = se.series_id
        WHERE se.provider_id = :providerId AND m.genres LIKE :genrePattern
        ORDER BY se.rating DESC, se.name ASC
        LIMIT :limit
        """
    )
    fun observeSeriesInGenre(providerId: Long, genrePattern: String, limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM movies WHERE provider_id = :providerId AND stream_id IN (SELECT remote_id FROM title_metadata WHERE provider_id = :providerId AND content_type = 'MOVIE' AND tmdb_id = :tmdbId) LIMIT 1")
    suspend fun movieByTmdbId(providerId: Long, tmdbId: Long): MovieEntity?

    @Query("SELECT * FROM series WHERE provider_id = :providerId AND series_id IN (SELECT remote_id FROM title_metadata WHERE provider_id = :providerId AND content_type = 'SERIES' AND tmdb_id = :tmdbId) LIMIT 1")
    suspend fun seriesByTmdbId(providerId: Long, tmdbId: Long): SeriesEntity?

    /** Titles matched on TMDB before countries were kept; filled in a little at a time. */
    @Query("SELECT * FROM title_metadata WHERE provider_id = :providerId AND tmdb_id IS NOT NULL AND countries IS NULL LIMIT :limit")
    suspend fun pendingCountryLookups(providerId: Long, limit: Int): List<TitleMetadataEntity>

    @Query("UPDATE title_metadata SET countries = :countries WHERE provider_id = :providerId AND content_type = :contentType AND remote_id = :remoteId")
    suspend fun updateCountries(providerId: Long, contentType: String, remoteId: Long, countries: String)

    @Transaction
    suspend fun updateCountriesBatch(rows: List<Pair<TitleMetadataEntity, String>>) {
        rows.forEach { (row, countries) -> updateCountries(row.providerId, row.contentType, row.remoteId, countries) }
    }

    @Query("SELECT countries FROM title_metadata WHERE provider_id = :providerId AND content_type = :contentType AND countries IS NOT NULL AND countries != ''")
    fun observeCountryLists(providerId: Long, contentType: String): Flow<List<String>>

    /** Home shelf filters; see CatalogInsightsRepositoryImpl for the query. */
    @RawQuery(observedEntities = [MovieEntity::class, TitleMetadataEntity::class])
    fun observeFilteredMovies(query: SupportSQLiteQuery): Flow<List<MovieEntity>>

    @RawQuery(observedEntities = [SeriesEntity::class, TitleMetadataEntity::class])
    fun observeFilteredSeries(query: SupportSQLiteQuery): Flow<List<SeriesEntity>>
}

/**
 * Keeps IMDb ratings on catalog rows: a catalog refresh re-creates movie/series rows with the
 * provider's rating, and these triggers put the IMDb rating back as each row is written.
 * Created on every database open (IF NOT EXISTS), so fresh installs and upgrades both get them.
 */
object TitleMetadataTriggers {
    const val MIN_TRUSTED_VOTES = 1000

    fun create(db: SupportSQLiteDatabase) {
        listOf(
            Triple("movies", "MOVIE", "stream_id"),
            Triple("series", "SERIES", "series_id")
        ).forEach { (table, type, remoteColumn) ->
            val match = "m.provider_id = NEW.provider_id AND m.content_type = '$type' AND m.remote_id = NEW.$remoteColumn"
            val imdbRating = "(SELECT CASE WHEN m.imdb_votes >= $MIN_TRUSTED_VOTES THEN m.imdb_rating ELSE 0 END " +
                "FROM title_metadata m WHERE $match)"
            val known = "EXISTS (SELECT 1 FROM title_metadata m WHERE $match AND m.imdb_rating IS NOT NULL)"
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS ${table}_imdb_rating_insert AFTER INSERT ON $table " +
                    "WHEN $known BEGIN UPDATE $table SET rating = $imdbRating WHERE id = NEW.id; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS ${table}_imdb_rating_update AFTER UPDATE OF rating ON $table " +
                    "WHEN $known AND NEW.rating IS NOT $imdbRating " +
                    "BEGIN UPDATE $table SET rating = $imdbRating WHERE id = NEW.id; END"
            )
        }
    }
}
