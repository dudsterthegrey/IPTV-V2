package com.streamvault.domain.model

import java.time.Year

/** Release years a shelf can be narrowed to. */
enum class CatalogYearFilter {
    ANY, THIS_YEAR, LAST_3_YEARS, LAST_10_YEARS, Y2010S, Y2000S, Y1990S, OLDER;

    /** Inclusive (from, to) years, or null for [ANY]. */
    fun range(currentYear: Int = Year.now().value): IntRange? = when (this) {
        ANY -> null
        THIS_YEAR -> currentYear..currentYear
        LAST_3_YEARS -> (currentYear - 2)..currentYear
        LAST_10_YEARS -> (currentYear - 9)..currentYear
        Y2010S -> 2010..2019
        Y2000S -> 2000..2009
        Y1990S -> 1990..1999
        OLDER -> 1800..1989
    }
}

/**
 * Filters for a Home shelf such as "Recently Added Movies". Empty sets and zero mean "any".
 * [countries] are ISO 3166-1 codes ("US"); [minRating] is out of 10 (IMDb when known).
 */
data class CatalogShelfFilter(
    val genres: Set<CatalogGenre> = emptySet(),
    val countries: Set<String> = emptySet(),
    val minRating: Int = 0,
    val year: CatalogYearFilter = CatalogYearFilter.ANY
) {
    val isActive: Boolean
        get() = genres.isNotEmpty() || countries.isNotEmpty() || minRating > 0 || year != CatalogYearFilter.ANY

    /** "g=ACTION,DRAMA;c=US,GB;r=7;y=Y2010S" */
    fun encode(): String = listOf(
        "g=" + genres.joinToString(",") { it.key },
        "c=" + countries.joinToString(","),
        "r=$minRating",
        "y=${year.name}"
    ).joinToString(";")

    companion object {
        fun decode(stored: String?): CatalogShelfFilter {
            if (stored.isNullOrBlank()) return CatalogShelfFilter()
            val parts = stored.split(';').mapNotNull { part ->
                part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            return CatalogShelfFilter(
                genres = parts["g"].orEmpty().split(',').mapNotNull { CatalogGenre.fromKey(it) }.toSet(),
                countries = parts["c"].orEmpty().split(',').filter { it.length == 2 }.toSet(),
                minRating = parts["r"]?.toIntOrNull()?.coerceIn(0, 9) ?: 0,
                year = runCatching { CatalogYearFilter.valueOf(parts["y"].orEmpty()) }.getOrDefault(CatalogYearFilter.ANY)
            )
        }
    }
}

/** What a shelf's filters can offer: genres and countries that have titles, most first. */
data class CatalogShelfFilterOptions(
    val genres: List<CatalogGenre> = emptyList(),
    val countries: List<String> = emptyList()
)
