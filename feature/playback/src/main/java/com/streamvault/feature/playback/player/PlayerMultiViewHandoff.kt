package com.streamvault.feature.playback.player

import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ContentType
import com.streamvault.feature.playback.multiview.MultiViewVodKind
import com.streamvault.feature.playback.multiview.multiViewVodChannel

/**
 * The movie or episode now playing, wrapped for the Multi-View planner, or null when it cannot
 * be replayed from the library (live, catch-up, Stremio and other remote-only content).
 */
internal fun PlayerViewModel.multiViewItemForCurrentVod(
    movieLabel: String,
    episodeLabel: (season: Int, episode: Int, series: String) -> String,
): Channel? {
    if (isCatchUpPlayback.value) return null
    return when (currentContentType) {
        ContentType.MOVIE, ContentType.VOD -> currentContentId.takeIf { it > 0L }?.let { movieId ->
            multiViewVodChannel(
                kind = MultiViewVodKind.MOVIE,
                contentId = movieId,
                title = currentTitle,
                subtitle = movieLabel,
                artworkUrl = currentArtworkUrl,
                providerId = currentProviderId,
            )
        }
        ContentType.SERIES_EPISODE -> currentEpisode.value?.takeIf { it.id > 0L }?.let { episode ->
            val seriesName = currentSeries.value?.name.orEmpty()
            multiViewVodChannel(
                kind = MultiViewVodKind.EPISODE,
                contentId = episode.id,
                title = currentTitle.ifBlank { episode.title },
                subtitle = episodeLabel(episode.seasonNumber, episode.episodeNumber, seriesName)
                    .trimEnd(' ', '·'),
                artworkUrl = episode.coverUrl ?: currentArtworkUrl,
                providerId = currentProviderId,
            )
        }
        else -> null
    }
}

/** Lets a movie or episode tile pick up where the full-screen player was. */
internal fun PlayerViewModel.rememberMultiViewStartPosition(item: Channel?) {
    item ?: return
    if (multiViewManager.slots.value.any { it?.id == item.id }) {
        multiViewManager.setStartPosition(item.id, playerEngine.currentPosition.value)
    }
}
