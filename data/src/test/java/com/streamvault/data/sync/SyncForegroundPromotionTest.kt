package com.streamvault.data.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncForegroundPromotionTest {
    @Test
    fun `quick work never becomes a foreground service`() = runTest {
        var promotions = 0
        val result = SyncForegroundPromotion.runPromotingAfter(
            delayMillis = 60_000L,
            promote = { promotions++ },
            onPromotionFailed = {},
        ) {
            delay(5_000L)
            "done"
        }

        assertThat(result).isEqualTo("done")
        assertThat(promotions).isEqualTo(0)
    }

    @Test
    fun `long work is promoted once after the delay`() = runTest {
        var promotedAt = -1L
        var promotions = 0
        SyncForegroundPromotion.runPromotingAfter(
            delayMillis = 60_000L,
            promote = { promotions++; promotedAt = testScheduler.currentTime },
            onPromotionFailed = {},
        ) {
            delay(15 * 60_000L)
        }

        assertThat(promotions).isEqualTo(1)
        assertThat(promotedAt).isEqualTo(60_000L)
    }

    @Test
    fun `a refused promotion does not stop the work`() = runTest {
        val failures = mutableListOf<Exception>()
        val result = SyncForegroundPromotion.runPromotingAfter(
            delayMillis = 60_000L,
            promote = { throw IllegalStateException("not allowed from background") },
            onPromotionFailed = { failures += it },
        ) {
            delay(2 * 60_000L)
            42
        }

        assertThat(result).isEqualTo(42)
        assertThat(failures).hasSize(1)
    }
}
