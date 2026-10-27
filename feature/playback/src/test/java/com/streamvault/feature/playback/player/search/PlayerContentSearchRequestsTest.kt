package com.streamvault.feature.playback.player.search

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Episode
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.ChannelRepository
import org.junit.Test

class PlayerContentSearchRequestsTest {

    @Test
    fun `channel without a category plays from the all-channels list`() {
        val request = Channel(id = 5, name = "News", streamUrl = "http://x/5", providerId = 2, streamId = 77)
            .toSearchPlayerRequest()

        assertThat(request.contentType).isEqualTo("LIVE")
        assertThat(request.categoryId).isEqualTo(ChannelRepository.ALL_CHANNELS_ID)
        assertThat(request.internalId).isEqualTo(5)
        assertThat(request.remoteContentId).isEqualTo("77")
    }

    @Test
    fun `movie request carries poster and provider`() {
        val request = Movie(id = 9, name = "Film", streamUrl = "http://x/9", providerId = 3, posterUrl = "p.jpg")
            .toSearchPlayerRequest()

        assertThat(request.contentType).isEqualTo("MOVIE")
        assertThat(request.providerId).isEqualTo(3)
        assertThat(request.artworkUrl).isEqualTo("p.jpg")
    }

    @Test
    fun `episode falls back to the picked series for ids and artwork`() {
        val series = Series(id = 40, name = "Show", providerId = 3, posterUrl = "show.jpg", providerSeriesId = " 1234 ")
        val episode = Episode(id = 401, title = "Pilot", episodeNumber = 1, seasonNumber = 2, streamUrl = "http://x/401")

        val request = episode.toSearchPlayerRequest(series)

        assertThat(request.contentType).isEqualTo("SERIES_EPISODE")
        assertThat(request.title).isEqualTo("Show - S2E1")
        assertThat(request.seriesId).isEqualTo(40)
        assertThat(request.providerId).isEqualTo(3)
        assertThat(request.artworkUrl).isEqualTo("show.jpg")
        assertThat(request.remoteSeriesId).isEqualTo("1234")
    }
}
