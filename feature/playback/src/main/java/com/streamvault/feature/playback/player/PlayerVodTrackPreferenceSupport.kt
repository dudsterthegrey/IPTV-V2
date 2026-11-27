package com.streamvault.feature.playback.player

import com.streamvault.domain.model.ContentType
import com.streamvault.domain.settings.VodTrackPreferenceScope
import com.streamvault.domain.settings.VodTrackPreferences

internal fun buildVodTrackPreferenceScope(
    contentType: ContentType,
    providerId: Long,
    contentId: Long,
    seriesId: Long?
): VodTrackPreferenceScope? {
    if (providerId <= 0L || contentId <= 0L) return null

    return when (contentType) {
        ContentType.MOVIE,
        ContentType.VOD -> VodTrackPreferenceScope.Movie(
            providerId = providerId,
            contentId = contentId
        )

        ContentType.SERIES_EPISODE -> seriesId
            ?.takeIf { it > 0L }
            ?.let { VodTrackPreferenceScope.Series(providerId = providerId, contentId = it) }

        ContentType.LIVE,
        ContentType.SERIES -> null
    }
}

/** Where an online subtitle chosen for one episode is kept, keyed by the episode id. */
internal fun buildEpisodeSubtitleScope(
    contentType: ContentType,
    providerId: Long,
    contentId: Long
): VodTrackPreferenceScope.Episode? =
    if (contentType == ContentType.SERIES_EPISODE && providerId > 0L && contentId > 0L) {
        VodTrackPreferenceScope.Episode(providerId = providerId, contentId = contentId)
    } else {
        null
    }

/**
 * Series-level choices apply to every episode, but a downloaded subtitle file belongs to one
 * episode, so it only comes from [episode]'s own entry.
 */
internal fun mergeEpisodeSubtitlePreference(
    base: VodTrackPreferences?,
    episode: VodTrackPreferences?
): VodTrackPreferences? {
    val episodeSubtitle = episode?.subtitle?.takeIf { it.externalSubtitleDownloadId != null }
    val withoutForeignFile = base?.let { prefs ->
        prefs.copy(subtitle = prefs.subtitle?.takeIf { it.externalSubtitleDownloadId == null })
    }
    return when {
        episodeSubtitle != null -> (withoutForeignFile ?: VodTrackPreferences()).copy(subtitle = episodeSubtitle)
        else -> withoutForeignFile
    }
}

