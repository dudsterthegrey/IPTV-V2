package com.streamvault.feature.playback.multiview

import com.streamvault.domain.model.Channel

/**
 * Movies and episodes ride through Multi-View as [Channel] values so the planner, grid, audio
 * focus and presets keep working unchanged. They are told apart by [Channel.groupTitle], carry
 * the movie or episode id in [Channel.streamId], and use a negative [Channel.id] so they can never
 * collide with a real channel (presets already skip ids that are not positive).
 */
enum class MultiViewVodKind(internal val marker: String) {
    MOVIE("multiview:movie"),
    EPISODE("multiview:episode"),
}

fun multiViewVodChannel(
    kind: MultiViewVodKind,
    contentId: Long,
    title: String,
    subtitle: String? = null,
    artworkUrl: String? = null,
    providerId: Long = 0L,
    streamUrl: String = "",
): Channel {
    require(contentId > 0L) { "contentId must be positive" }
    return Channel(
        id = -(contentId * 2 + kind.ordinal),
        name = title,
        logoUrl = artworkUrl,
        groupTitle = kind.marker,
        categoryName = subtitle,
        streamUrl = streamUrl,
        providerId = providerId,
        streamId = contentId,
    )
}

val Channel.multiViewVodKind: MultiViewVodKind?
    get() = if (id < 0L) MultiViewVodKind.entries.firstOrNull { it.marker == groupTitle } else null
