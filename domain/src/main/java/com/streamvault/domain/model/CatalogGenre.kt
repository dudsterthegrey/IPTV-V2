package com.streamvault.domain.model

/**
 * The fixed genre list movies and series are sorted into, whatever categories the provider uses.
 * [key] is what is stored ("|ACTION|ANIME|") so renaming an entry never breaks saved data.
 */
enum class CatalogGenre(val key: String) {
    ACTION("ACTION"),
    ADVENTURE("ADVENTURE"),
    ANIMATION("ANIMATION"),
    ANIME("ANIME"),
    COMEDY("COMEDY"),
    CRIME("CRIME"),
    DOCUMENTARY("DOCUMENTARY"),
    DRAMA("DRAMA"),
    FAMILY("FAMILY"),
    FANTASY("FANTASY"),
    HISTORY("HISTORY"),
    HORROR("HORROR"),
    KIDS("KIDS"),
    MUSIC("MUSIC"),
    MYSTERY("MYSTERY"),
    REALITY("REALITY"),
    ROMANCE("ROMANCE"),
    SCI_FI("SCI_FI"),
    SPORT("SPORT"),
    TALK("TALK"),
    THRILLER("THRILLER"),
    WAR("WAR"),
    WESTERN("WESTERN"),
    CHRISTMAS("CHRISTMAS");

    companion object {
        fun fromKey(key: String): CatalogGenre? = entries.firstOrNull { it.key == key }

        /** "|ACTION|ANIME|" → [ACTION, ANIME]. */
        fun decode(stored: String?): Set<CatalogGenre> =
            stored.orEmpty().split('|').mapNotNullTo(linkedSetOf()) { part -> fromKey(part) }

        fun encode(genres: Collection<CatalogGenre>): String =
            if (genres.isEmpty()) "" else genres.sortedBy { it.ordinal }.joinToString("|", prefix = "|", postfix = "|") { it.key }
    }
}

/** Number of titles in one genre, for the genre list. */
data class CatalogGenreCount(val genre: CatalogGenre, val count: Int)
