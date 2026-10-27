package com.streamvault.domain.model

/**
 * A series the user asked to watch for new episodes. [hasNewEpisodes] stays set until the user
 * clears it or plays an episode of the series; opening the details page alone does not clear it.
 */
data class TrackedSeries(
    val providerId: Long,
    val seriesId: Long,
    val name: String,
    val posterUrl: String? = null,
    val knownEpisodeCount: Int = 0,
    val hasNewEpisodes: Boolean = false,
    val newEpisodeCount: Int = 0,
    val trackedAt: Long = 0L,
    val lastCheckedAt: Long = 0L,
    val newEpisodesFoundAt: Long = 0L
) {
    init {
        require(knownEpisodeCount >= 0) { "knownEpisodeCount must be non-negative" }
        require(newEpisodeCount >= 0) { "newEpisodeCount must be non-negative" }
    }
}
