package com.streamvault.data.remote.tmdb

import com.streamvault.data.search.FuzzyTitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A TMDB title as IPTV Player uses it. [mediaType] is "movie" or "tv". */
data class TmdbTitle(
    val id: Long,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val genreIds: List<Int>,
    val originalLanguage: String?,
    val popularity: Double
)

/** Small TMDB v3 client: title matching, IMDb ids, and what is popular today. */
@Singleton
class TmdbClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @param:Named("tmdbApiKey") private val apiKey: String
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** The TMDB title that best matches a provider's title, or null when nothing is close enough. */
    suspend fun findTitle(providerTitle: String, year: Int?, isSeries: Boolean): TmdbTitle? {
        val cleaned = TmdbTitleCleaner.clean(providerTitle)
        if (cleaned.title.isBlank()) return null
        val searchYear = year ?: cleaned.year
        val mediaType = if (isSeries) "tv" else "movie"
        val url = baseUrl("search/$mediaType")
            .addQueryParameter("query", cleaned.title)
            .addQueryParameter("include_adult", "false")
            .apply {
                if (searchYear != null) addQueryParameter(if (isSeries) "first_air_date_year" else "year", searchYear.toString())
            }
            .build()
        var results = getJson(url)?.parseResults(mediaType).orEmpty()
        if (results.isEmpty() && searchYear != null) {
            // Providers' years are often off by one or are the year it was added; retry without it.
            results = getJson(baseUrl("search/$mediaType").addQueryParameter("query", cleaned.title).build())
                ?.parseResults(mediaType).orEmpty()
        }
        val queryTokens = FuzzyTitleMatcher.tokenize(cleaned.title)
        return results
            .asSequence()
            .take(MAX_CANDIDATES)
            .mapNotNull { candidate ->
                val score = FuzzyTitleMatcher.score(queryTokens, FuzzyTitleMatcher.tokenize(candidate.title))
                    ?: return@mapNotNull null
                val yearPenalty = when {
                    searchYear == null || candidate.year == null -> 5
                    kotlin.math.abs(searchYear - candidate.year) <= 1 -> 0
                    else -> 40
                }
                candidate to (score + yearPenalty)
            }
            .filter { it.second < MAX_MATCH_SCORE }
            .minByOrNull { it.second }
            ?.first
    }

    /** A TMDB title's IMDb id and countries (ISO 3166-1, e.g. "US"). */
    data class TmdbDetails(val imdbId: String?, val countries: List<String>)

    /**
     * IMDb id and countries of a TMDB title in one request. Countries are the origin countries,
     * or the production countries when TMDB has no origin.
     */
    suspend fun details(tmdbId: Long, isSeries: Boolean): TmdbDetails? {
        val path = if (isSeries) "tv/$tmdbId" else "movie/$tmdbId"
        val json = getJson(baseUrl(path).addQueryParameter("append_to_response", "external_ids").build()) ?: return null
        val imdbId = (json.optJSONObject("external_ids")?.optString("imdb_id") ?: json.optString("imdb_id"))
            ?.takeIf { it.startsWith("tt") }
        val origin = json.optJSONArray("origin_country")?.let { list -> (0 until list.length()).map { list.optString(it) } }.orEmpty()
        val production = json.optJSONArray("production_countries")?.let { list ->
            (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("iso_3166_1") }
        }.orEmpty()
        val countries = origin.ifEmpty { production }
            .map { it.trim().uppercase() }
            .filter { it.length == 2 }
            .distinct()
        return TmdbDetails(imdbId, countries)
    }

    /** The IMDb id ("tt0903747") of a TMDB title, if TMDB knows it. */
    suspend fun imdbId(tmdbId: Long, isSeries: Boolean): String? {
        val path = if (isSeries) "tv/$tmdbId/external_ids" else "movie/$tmdbId/external_ids"
        return getJson(baseUrl(path).build())?.optString("imdb_id")?.takeIf { it.startsWith("tt") }
    }

    /** What is trending worldwide today, movies and shows together, most popular first. */
    suspend fun trendingToday(): List<TmdbTitle> =
        listOf("movie", "tv").flatMap { type ->
            getJson(baseUrl("trending/$type/day").build())?.parseResults(type).orEmpty()
        }.sortedByDescending { it.popularity }

    /** What is most popular on streaming in [countryCode] (ISO 3166-1, e.g. "US") right now. */
    suspend fun popularIn(countryCode: String): List<TmdbTitle> =
        listOf("movie", "tv").flatMap { type ->
            val url = baseUrl("discover/$type")
                .addQueryParameter("watch_region", countryCode)
                .addQueryParameter("with_watch_monetization_types", "flatrate|free|ads|rent|buy")
                .addQueryParameter("sort_by", "popularity.desc")
                .build()
            getJson(url)?.parseResults(type).orEmpty()
        }.sortedByDescending { it.popularity }

    private fun baseUrl(path: String): HttpUrl.Builder =
        "https://api.themoviedb.org/3/$path".toHttpUrl().newBuilder().addQueryParameter("api_key", apiKey)

    private suspend fun getJson(url: HttpUrl): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> throw TmdbRateLimitedException()
                    !response.isSuccessful -> null
                    else -> response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
                }
            }
        } catch (error: IOException) {
            throw error
        }
    }

    private fun JSONObject.parseResults(mediaType: String): List<TmdbTitle> {
        val array = optJSONArray("results") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
            val title = item.optString(if (mediaType == "tv") "name" else "title").ifBlank { return@mapNotNull null }
            val date = item.optString(if (mediaType == "tv") "first_air_date" else "release_date")
            val genres = item.optJSONArray("genre_ids")
            TmdbTitle(
                id = id,
                mediaType = mediaType,
                title = title,
                year = date.take(4).toIntOrNull(),
                genreIds = genres?.let { list -> (0 until list.length()).map { list.optInt(it) } }.orEmpty(),
                originalLanguage = item.optString("original_language").takeIf { it.isNotBlank() },
                popularity = item.optDouble("popularity", 0.0)
            )
        }
    }

    private companion object {
        const val MAX_CANDIDATES = 8
        // FuzzyTitleMatcher scores: exact 0, each typo 10; plus a year mismatch penalty of 40.
        const val MAX_MATCH_SCORE = 30
    }
}

class TmdbRateLimitedException : IOException("TMDB rate limit reached")

/** Strips provider decorations from a title before it is searched on TMDB. */
object TmdbTitleCleaner {
    data class Cleaned(val title: String, val year: Int?)

    private val trailingYear = Regex("""[\(\[]\s*((?:19|20)\d{2})\s*[\)\]]\s*$""")
    private val bareTrailingYear = Regex("""\s-?\s*((?:19|20)\d{2})\s*$""")
    private val leadingTag = Regex("""^\s*[A-Za-z]{2,4}\s*[:|]\s*|^\s*[A-Za-z]{2,3}\s+-\s+""")
    private val bracketed = Regex("""[\(\[][^)\]]*[\)\]]""")
    private val qualityTags = Regex("""(?i)\b(4k|uhd|fhd|hd|sd|hdr|1080p|720p|2160p|multi[- ]?sub|multi[- ]?audio|dubbed|subbed|cam|ts|hdcam)\b""")

    fun clean(raw: String): Cleaned {
        var text = raw.trim()
        val year = (trailingYear.find(text) ?: bareTrailingYear.find(text))?.groupValues?.get(1)?.toIntOrNull()
        text = trailingYear.replace(text, "")
        text = leadingTag.replace(text, "")
        text = bracketed.replace(text, " ")
        text = qualityTags.replace(text, " ")
        text = text.replace(Regex("""\s+"""), " ").trim().trimEnd('-', ':', '|').trim()
        return Cleaned(text, year)
    }
}
