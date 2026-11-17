package com.streamvault.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * What IPTV Player learned about a movie or series beyond the provider's own data: TMDB/IMDb
 * identity, the IMDb rating, and normalized genres. Keyed by the provider's own id for the title
 * (movies.stream_id / series.series_id) so it survives catalog refreshes, which re-create rows.
 */
@Entity(
    tableName = "title_metadata",
    primaryKeys = ["provider_id", "content_type", "remote_id"],
    indices = [
        Index(value = ["provider_id", "content_type", "genres"]),
        Index(value = ["provider_id", "tmdb_id"])
    ]
)
data class TitleMetadataEntity(
    @ColumnInfo(name = "provider_id") val providerId: Long,
    /** "MOVIE" or "SERIES". */
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    val title: String,
    /** Release year from the provider, used to tell remakes apart when matching on TMDB. */
    val year: Int? = null,
    /** Normalized genres, "|ACTION|ANIME|"; see CatalogGenre. */
    @ColumnInfo(defaultValue = "''") val genres: String = "",
    @ColumnInfo(name = "tmdb_id") val tmdbId: Long? = null,
    @ColumnInfo(name = "imdb_id") val imdbId: String? = null,
    @ColumnInfo(name = "original_language") val originalLanguage: String? = null,
    @ColumnInfo(name = "tmdb_genre_ids", defaultValue = "''") val tmdbGenreIds: String = "",
    @ColumnInfo(defaultValue = "0") val popularity: Double = 0.0,
    /** IMDb rating, applied to movies/series.rating once [imdbVotes] is high enough to trust. */
    @ColumnInfo(name = "imdb_rating") val imdbRating: Double? = null,
    @ColumnInfo(name = "imdb_votes", defaultValue = "0") val imdbVotes: Int = 0,
    /** When TMDB was asked about this title; 0 = not yet. A failed match is still "asked". */
    @ColumnInfo(name = "tmdb_checked_at", defaultValue = "0") val tmdbCheckedAt: Long = 0L,
    /** TMDB's countries as "|US|GB|"; null until TMDB has been asked, "" when it lists none. */
    val countries: String? = null
)
