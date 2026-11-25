package com.streamvault.data.epg

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GuideIngestWindowTest {
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour
    private val now = 1_800_000_000_000L

    @Test
    fun `keeps current and upcoming programmes within the lookahead`() {
        val window = GuideIngestWindow.forCatchUpDays(now, maxCatchUpDays = 0)

        assertThat(window.keeps(now - hour, now + hour)).isTrue()
        assertThat(window.keeps(now + 4 * day, now + 4 * day + hour)).isTrue()
    }

    @Test
    fun `drops programmes far in the past or future`() {
        val window = GuideIngestWindow.forCatchUpDays(now, maxCatchUpDays = 0)

        assertThat(window.keeps(now - 3 * day, now - 3 * day + hour)).isFalse()
        assertThat(window.keeps(now + 7 * day, now + 7 * day + hour)).isFalse()
    }

    @Test
    fun `catch-up days keep older programmes`() {
        val window = GuideIngestWindow.forCatchUpDays(now, maxCatchUpDays = 7)

        assertThat(window.keeps(now - 6 * day, now - 6 * day + hour)).isTrue()
        assertThat(window.keeps(now - 9 * day, now - 9 * day + hour)).isFalse()
    }

    @Test
    fun `programmes without parsed times are kept`() {
        val window = GuideIngestWindow.forCatchUpDays(now, maxCatchUpDays = 0)

        assertThat(window.keeps(0L, 0L)).isTrue()
    }

    @Test
    fun `failed or missing catch-up lookup falls back to the minimum retention`() = runBlocking {
        val failed = GuideIngestWindow.forCatchUpLookup(now) { error("database closed") }
        val missing = GuideIngestWindow.forCatchUpLookup(now) { null }

        assertThat(failed.earliestEnd).isEqualTo(GuideIngestWindow.forCatchUpDays(now, 0).earliestEnd)
        assertThat(missing.earliestEnd).isEqualTo(failed.earliestEnd)
    }
}
