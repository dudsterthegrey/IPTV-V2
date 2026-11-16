package com.streamvault.data.metadata

import com.streamvault.domain.model.CatalogGenre
import java.util.Locale

/**
 * Turns whatever a provider says about a title (its genre text, its category name) and what TMDB
 * says (genre ids, original language) into [CatalogGenre]s. Anime is Animation made in Japanese,
 * or anything a provider files under an "anime" category.
 */
object GenreClassifier {

    fun classify(
        providerGenre: String?,
        categoryName: String?,
        tmdbGenreIds: Collection<Int> = emptyList(),
        originalLanguage: String? = null
    ): Set<CatalogGenre> {
        val genres = linkedSetOf<CatalogGenre>()
        tmdbGenreIds.mapNotNullTo(genres) { TMDB_GENRES[it] }
        textGenres(providerGenre).forEach(genres::add)
        textGenres(categoryName).forEach(genres::add)
        val category = categoryName.orEmpty().lowercase(Locale.ROOT)
        val japaneseAnimation = CatalogGenre.ANIMATION in genres && originalLanguage.equals("ja", ignoreCase = true)
        if (japaneseAnimation || "anime" in category) {
            genres += CatalogGenre.ANIME
            genres += CatalogGenre.ANIMATION
        }
        return genres
    }

    /** Genres named in free text such as "Action & Adventure, Sci-Fi & Fantasy" or "Movie-Horror". */
    internal fun textGenres(text: String?): Set<CatalogGenre> {
        if (text.isNullOrBlank()) return emptySet()
        val normalized = " " + text.lowercase(Locale.ROOT).replace(NON_LETTERS, " ") + " "
        return KEYWORDS.filter { (keyword, _) -> " $keyword " in normalized }.mapTo(linkedSetOf()) { it.second }
    }

    private val NON_LETTERS = Regex("[^a-z0-9]+")

    // Order matters only for readability; every keyword that appears is used.
    private val KEYWORDS: List<Pair<String, CatalogGenre>> = listOf(
        "action" to CatalogGenre.ACTION,
        "adventure" to CatalogGenre.ADVENTURE,
        "kung fu" to CatalogGenre.ACTION,
        "martial arts" to CatalogGenre.ACTION,
        "animation" to CatalogGenre.ANIMATION,
        "animated" to CatalogGenre.ANIMATION,
        "cartoon" to CatalogGenre.ANIMATION,
        "cartoons" to CatalogGenre.ANIMATION,
        "anime" to CatalogGenre.ANIME,
        "comedy" to CatalogGenre.COMEDY,
        "sitcom" to CatalogGenre.COMEDY,
        "stand up" to CatalogGenre.COMEDY,
        "crime" to CatalogGenre.CRIME,
        "documentary" to CatalogGenre.DOCUMENTARY,
        "documentaries" to CatalogGenre.DOCUMENTARY,
        "docuseries" to CatalogGenre.DOCUMENTARY,
        "drama" to CatalogGenre.DRAMA,
        "soap" to CatalogGenre.DRAMA,
        "family" to CatalogGenre.FAMILY,
        "fantasy" to CatalogGenre.FANTASY,
        "history" to CatalogGenre.HISTORY,
        "historical" to CatalogGenre.HISTORY,
        "horror" to CatalogGenre.HORROR,
        "kids" to CatalogGenre.KIDS,
        "children" to CatalogGenre.KIDS,
        "music" to CatalogGenre.MUSIC,
        "musical" to CatalogGenre.MUSIC,
        "concert" to CatalogGenre.MUSIC,
        "concerts" to CatalogGenre.MUSIC,
        "mystery" to CatalogGenre.MYSTERY,
        "reality" to CatalogGenre.REALITY,
        "romance" to CatalogGenre.ROMANCE,
        "romantic" to CatalogGenre.ROMANCE,
        "sci fi" to CatalogGenre.SCI_FI,
        "science fiction" to CatalogGenre.SCI_FI,
        "scifi" to CatalogGenre.SCI_FI,
        "sport" to CatalogGenre.SPORT,
        "sports" to CatalogGenre.SPORT,
        "ufc" to CatalogGenre.SPORT,
        "wwe" to CatalogGenre.SPORT,
        "boxing" to CatalogGenre.SPORT,
        "talk" to CatalogGenre.TALK,
        "thriller" to CatalogGenre.THRILLER,
        "suspense" to CatalogGenre.THRILLER,
        "war" to CatalogGenre.WAR,
        "western" to CatalogGenre.WESTERN,
        "westerns" to CatalogGenre.WESTERN,
        "christmas" to CatalogGenre.CHRISTMAS,
        "holiday" to CatalogGenre.CHRISTMAS
    )

    /** TMDB genre ids (movie and TV lists combined). */
    internal val TMDB_GENRES: Map<Int, CatalogGenre> = mapOf(
        28 to CatalogGenre.ACTION,
        12 to CatalogGenre.ADVENTURE,
        16 to CatalogGenre.ANIMATION,
        35 to CatalogGenre.COMEDY,
        80 to CatalogGenre.CRIME,
        99 to CatalogGenre.DOCUMENTARY,
        18 to CatalogGenre.DRAMA,
        10751 to CatalogGenre.FAMILY,
        14 to CatalogGenre.FANTASY,
        36 to CatalogGenre.HISTORY,
        27 to CatalogGenre.HORROR,
        10402 to CatalogGenre.MUSIC,
        9648 to CatalogGenre.MYSTERY,
        10749 to CatalogGenre.ROMANCE,
        878 to CatalogGenre.SCI_FI,
        53 to CatalogGenre.THRILLER,
        10752 to CatalogGenre.WAR,
        37 to CatalogGenre.WESTERN,
        10759 to CatalogGenre.ACTION, // Action & Adventure (TV)
        10762 to CatalogGenre.KIDS,
        10763 to CatalogGenre.DOCUMENTARY, // News
        10764 to CatalogGenre.REALITY,
        10765 to CatalogGenre.SCI_FI, // Sci-Fi & Fantasy (TV)
        10766 to CatalogGenre.DRAMA, // Soap
        10767 to CatalogGenre.TALK,
        10768 to CatalogGenre.WAR // War & Politics
    )
}
