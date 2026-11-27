package com.streamvault.feature.playback.player

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.settings.VodTrackPreference
import com.streamvault.domain.settings.VodTrackPreferenceScope
import com.streamvault.domain.settings.VodTrackPreferences
import org.junit.Test

class EpisodeSubtitlePreferenceTest {
    private val audio = VodTrackPreference(language = "en")

    @Test
    fun `episode scope only exists for episodes`() {
        assertThat(buildEpisodeSubtitleScope(ContentType.SERIES_EPISODE, 1L, 77L))
            .isEqualTo(VodTrackPreferenceScope.Episode(1L, 77L))
        assertThat(buildEpisodeSubtitleScope(ContentType.MOVIE, 1L, 77L)).isNull()
        assertThat(buildEpisodeSubtitleScope(ContentType.SERIES_EPISODE, 0L, 77L)).isNull()
    }

    @Test
    fun `a subtitle file saved for another episode never carries over`() {
        val series = VodTrackPreferences(
            audio = audio,
            subtitle = VodTrackPreference(language = "fr", externalSubtitleDownloadId = "other-episode")
        )

        val merged = mergeEpisodeSubtitlePreference(series, episode = null)

        assertThat(merged?.audio).isEqualTo(audio)
        assertThat(merged?.subtitle).isNull()
    }

    @Test
    fun `the episode's own subtitle wins and series audio is kept`() {
        val series = VodTrackPreferences(audio = audio, subtitle = VodTrackPreference(language = "fr"))
        val own = VodTrackPreference(language = "en", label = "English", externalSubtitleDownloadId = "4242")

        val merged = mergeEpisodeSubtitlePreference(series, VodTrackPreferences(subtitle = own))

        assertThat(merged).isEqualTo(VodTrackPreferences(audio = audio, subtitle = own))
    }

    @Test
    fun `series subtitle language choice without a file still applies`() {
        val series = VodTrackPreferences(subtitle = VodTrackPreference(language = "fr"))

        assertThat(mergeEpisodeSubtitlePreference(series, VodTrackPreferences())).isEqualTo(series)
    }
}
