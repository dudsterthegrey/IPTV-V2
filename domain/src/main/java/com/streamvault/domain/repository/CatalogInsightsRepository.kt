package com.streamvault.domain.repository

import com.streamvault.domain.model.CatalogGenre
import com.streamvault.domain.model.CatalogGenreCount
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import kotlinx.coroutines.flow.Flow

/** One entry of the Top 10: exactly one of [movie] or [series] is set. */
data class TopTenEntry(
    val rank: Int,
    val movie: Movie? = null,
    val series: Series? = null
)

/** Genres, IMDb-ranked lists and today's Top 10, built on top of the provider's catalog. */
interface CatalogInsightsRepository {
    /** Genres that have titles, most titles first. [isSeries] picks series instead of movies. */
    fun observeGenreCounts(providerId: Long, isSeries: Boolean): Flow<List<CatalogGenreCount>>

    /** Movies of a genre, highest rated (IMDb when known) first. */
    fun observeMoviesInGenre(providerId: Long, genre: CatalogGenre, limit: Int): Flow<List<Movie>>

    /** Series of a genre, highest rated (IMDb when known) first. */
    fun observeSeriesInGenre(providerId: Long, genre: CatalogGenre, limit: Int): Flow<List<Series>>

    /**
     * Today's most popular titles that this provider has, worldwide or for [countryCode]
     * (ISO 3166-1 alpha-2, null = worldwide). Empty when TMDB can't be reached.
     */
    suspend fun topTenToday(providerId: Long, countryCode: String?): List<TopTenEntry>

    /** The country chosen for the Top 10 row; null = worldwide. */
    val topTenCountry: Flow<String?>

    suspend fun setTopTenCountry(countryCode: String?)
}
