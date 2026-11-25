package com.streamvault.data.epg

/**
 * The span of guide data worth storing when a guide file is imported.
 *
 * Guide files often hold one to two weeks of programmes for every channel. Storing all of it made
 * imports on low-end TV sticks slow enough to be stopped before finishing, which left the guide
 * empty. Only programmes that the guide, catch-up or the maintenance retention can still use are
 * kept: from the catch-up retention (at least 12 hours) before now, to [LOOKAHEAD_MILLIS] ahead.
 * [SHIFT_MARGIN_MILLIS] on both ends covers the per-provider guide time shift applied at read time.
 */
internal class GuideIngestWindow(
    val earliestEnd: Long,
    val latestStart: Long,
) {
    fun keeps(startTime: Long, endTime: Long): Boolean {
        // Unparseable times arrive as 0; keep them rather than guess.
        if (startTime <= 0L || endTime <= 0L) return true
        return endTime >= earliestEnd && startTime <= latestStart
    }

    companion object {
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val MIN_RETENTION_MILLIS = 12L * HOUR_MILLIS
        const val LOOKAHEAD_MILLIS = 5L * DAY_MILLIS
        const val SHIFT_MARGIN_MILLIS = 12L * HOUR_MILLIS

        /** A failed catch-up lookup must not stop a guide import; fall back to the minimum. */
        suspend fun forCatchUpLookup(now: Long, maxCatchUpDays: suspend () -> Int?): GuideIngestWindow {
            val days = try {
                maxCatchUpDays() ?: 0
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0
            }
            return forCatchUpDays(now, days)
        }

        fun forCatchUpDays(now: Long, maxCatchUpDays: Int): GuideIngestWindow {
            val retention = maxOf(MIN_RETENTION_MILLIS, maxCatchUpDays.coerceAtLeast(0) * DAY_MILLIS)
            return GuideIngestWindow(
                earliestEnd = now - retention - SHIFT_MARGIN_MILLIS,
                latestStart = now + LOOKAHEAD_MILLIS + SHIFT_MARGIN_MILLIS,
            )
        }
    }
}
