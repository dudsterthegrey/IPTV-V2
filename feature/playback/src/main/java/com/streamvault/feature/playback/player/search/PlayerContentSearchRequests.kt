package com.streamvault.feature.playback.player.search

import com.streamvault.core.navigation.PlayerNavigationRequest
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Episode
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.ChannelRepository

// Mirrors the app module's PlayerNavigationAdapters so a search pick plays exactly as it would
// when opened from the catalog.

internal fun Channel.toSearchPlayerRequest(): PlayerNavigationRequest = PlayerNavigationRequest(
    streamUrl = streamUrl,
    title = name,
    channelId = epgChannelId,
    internalId = id,
    categoryId = categoryId ?: ChannelRepository.ALL_CHANNELS_ID,
    providerId = providerId,
    contentType = "LIVE",
    remoteContentId = streamId.takeIf { it > 0L }?.toString()
)

internal fun Movie.toSearchPlayerRequest(): PlayerNavigationRequest = PlayerNavigationRequest(
    streamUrl = streamUrl,
    title = name,
    internalId = id,
    categoryId = categoryId,
    providerId = providerId,
    contentType = "MOVIE",
    remoteContentId = streamId.takeIf { it > 0L }?.toString(),
    artworkUrl = posterUrl ?: backdropUrl
)

internal fun Episode.toSearchPlayerRequest(series: Series): PlayerNavigationRequest = PlayerNavigationRequest(
    streamUrl = streamUrl,
    title = "${series.name} - S${seasonNumber}E${episodeNumber}",
    internalId = id,
    providerId = providerId.takeIf { it > 0L } ?: series.providerId,
    contentType = "SERIES_EPISODE",
    artworkUrl = coverUrl ?: series.posterUrl,
    seriesId = seriesId.takeIf { it > 0L } ?: series.id,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeId = episodeId.takeIf { it > 0L },
    remoteContentId = episodeId.takeIf { it > 0L }?.toString(),
    remoteSeriesId = series.providerSeriesId?.trim()?.takeIf(String::isNotBlank)
)
