package com.streamvault.domain.model

/** An episode shown on a Home row, with the show it belongs to. */
data class EpisodeShelfItem(
    val episode: Episode,
    val seriesName: String,
    val seriesPosterUrl: String? = null
)
