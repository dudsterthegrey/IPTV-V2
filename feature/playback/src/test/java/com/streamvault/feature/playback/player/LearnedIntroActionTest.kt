package com.streamvault.feature.playback.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LearnedIntroActionTest {
    @Test
    fun `offers skip intro until shortly before the marked end`() {
        val action = learnedIntroAction(introEndMs = 90_000L, positionMs = 5_000L, durationMs = 1_400_000L)
        assertThat(action?.type).isEqualTo(SkippableChapterType.INTRO)
        assertThat(action?.targetPositionMs).isEqualTo(90_000L)

        assertThat(learnedIntroAction(90_000L, positionMs = 89_000L, durationMs = 1_400_000L)).isNull()
        assertThat(learnedIntroAction(90_000L, positionMs = 120_000L, durationMs = 1_400_000L)).isNull()
    }

    @Test
    fun `no mark or a mark past the episode end offers nothing`() {
        assertThat(learnedIntroAction(null, positionMs = 0L, durationMs = 1_400_000L)).isNull()
        assertThat(learnedIntroAction(90_000L, positionMs = 0L, durationMs = 60_000L)).isNull()
    }
}
