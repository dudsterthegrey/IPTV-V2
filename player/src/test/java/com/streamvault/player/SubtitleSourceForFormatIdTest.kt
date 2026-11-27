package com.streamvault.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubtitleSourceForFormatIdTest {
    @Test
    fun `merged and plain ids are both recognised`() {
        assertThat(subtitleSourceForFormatId(ONLINE_SUBTITLE_TRACK_ID)).isEqualTo(SubtitleTrackSource.ONLINE)
        assertThat(subtitleSourceForFormatId("1:$ONLINE_SUBTITLE_TRACK_ID")).isEqualTo(SubtitleTrackSource.ONLINE)
        assertThat(subtitleSourceForFormatId("2:1:$DEVICE_SUBTITLE_TRACK_ID")).isEqualTo(SubtitleTrackSource.DEVICE)
        assertThat(subtitleSourceForFormatId("1:$STREMIO_SUBTITLE_TRACK_ID")).isEqualTo(SubtitleTrackSource.STREMIO)
    }

    @Test
    fun `provider tracks stay provider tracks`() {
        assertThat(subtitleSourceForFormatId("0:3")).isEqualTo(SubtitleTrackSource.PROVIDER)
        assertThat(subtitleSourceForFormatId(null)).isEqualTo(SubtitleTrackSource.PROVIDER)
        assertThat(subtitleSourceForFormatId("eng-subs")).isEqualTo(SubtitleTrackSource.PROVIDER)
    }
}
