package com.streamvault.data.repository

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.TrackedSeriesDao
import com.streamvault.data.local.entity.TrackedSeriesEntity
import com.streamvault.domain.model.Episode
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Season
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

class TrackedSeriesRepositoryImplTest {

    private val dao = FakeTrackedSeriesDao()

    @Test
    fun `new episodes flag the series and clearing removes the badge`() = runTest {
        val seriesRepository = seriesRepositoryReturning(series(episodes = 3), series(episodes = 5))
        val repository = TrackedSeriesRepositoryImpl(dao, seriesRepository)

        repository.track(series(episodes = 3))
        val flagged = repository.checkForNewEpisodes(PROVIDER_ID, minIntervalMs = -1L)

        assertThat((flagged as Result.Success).data).isEqualTo(0)
        val secondCheck = repository.checkForNewEpisodes(PROVIDER_ID, minIntervalMs = -1L)
        assertThat((secondCheck as Result.Success).data).isEqualTo(1)
        val tracked = repository.observeTrackedSeries(PROVIDER_ID, SERIES_ID).first()!!
        assertThat(tracked.hasNewEpisodes).isTrue()
        assertThat(tracked.newEpisodeCount).isEqualTo(2)
        assertThat(tracked.knownEpisodeCount).isEqualTo(5)
        assertThat(repository.observeSeriesWithNewEpisodes().first()).containsExactly(PROVIDER_ID to SERIES_ID)

        repository.clearNewEpisodes(PROVIDER_ID, SERIES_ID)

        assertThat(repository.observeTrackedSeries(PROVIDER_ID, SERIES_ID).first()!!.hasNewEpisodes).isFalse()
        assertThat(repository.observeSeriesWithNewEpisodes().first()).isEmpty()
    }

    @Test
    fun `zero baseline adopts the real episode count without flagging`() = runTest {
        val repository = TrackedSeriesRepositoryImpl(dao, seriesRepositoryReturning(series(episodes = 8)))

        repository.track(series(episodes = 0))
        repository.checkForNewEpisodes(PROVIDER_ID, minIntervalMs = -1L)

        val tracked = repository.observeTrackedSeries(PROVIDER_ID, SERIES_ID).first()!!
        assertThat(tracked.hasNewEpisodes).isFalse()
        assertThat(tracked.knownEpisodeCount).isEqualTo(8)
    }

    @Test
    fun `recently checked series are skipped`() = runTest {
        val seriesRepository = seriesRepositoryReturning(series(episodes = 9))
        val repository = TrackedSeriesRepositoryImpl(dao, seriesRepository)

        repository.track(series(episodes = 3))
        repository.checkForNewEpisodes(PROVIDER_ID, minIntervalMs = 60_000L)

        verifyBlocking(seriesRepository, never()) { getSeriesDetails(any(), any(), anyOrNull()) }
        assertThat(repository.observeTrackedSeries(PROVIDER_ID, SERIES_ID).first()!!.hasNewEpisodes).isFalse()
    }

    @Test
    fun `untrack removes the series`() = runTest {
        val repository = TrackedSeriesRepositoryImpl(dao, seriesRepositoryReturning(series(episodes = 1)))

        repository.track(series(episodes = 1))
        repository.untrack(PROVIDER_ID, SERIES_ID)

        assertThat(repository.observeTrackedSeries(PROVIDER_ID).first()).isEmpty()
    }

    private fun seriesRepositoryReturning(vararg results: Series): SeriesRepository = mock {
        onBlocking { getSeriesDetails(any(), any(), anyOrNull()) } doReturnConsecutively
            results.map { Result.success(it) }
    }

    private fun series(episodes: Int) = Series(
        id = SERIES_ID,
        name = "Show",
        providerId = PROVIDER_ID,
        seasons = listOf(
            Season(
                seasonNumber = 1,
                episodes = (1..episodes).map { number ->
                    Episode(
                        id = number.toLong(),
                        title = "Episode $number",
                        episodeNumber = number,
                        seasonNumber = 1,
                        seriesId = SERIES_ID,
                        providerId = PROVIDER_ID
                    )
                }
            )
        )
    )

    private companion object {
        const val PROVIDER_ID = 7L
        const val SERIES_ID = 42L
    }
}

private class FakeTrackedSeriesDao : TrackedSeriesDao {
    private val rows = MutableStateFlow<Map<Pair<Long, Long>, TrackedSeriesEntity>>(emptyMap())

    override fun observeByProvider(providerId: Long): Flow<List<TrackedSeriesEntity>> =
        rows.map { all -> all.values.filter { it.providerId == providerId } }

    override fun observe(providerId: Long, seriesId: Long): Flow<TrackedSeriesEntity?> =
        rows.map { it[providerId to seriesId] }

    override fun observeWithNewEpisodes(): Flow<List<TrackedSeriesEntity>> =
        rows.map { all -> all.values.filter { it.hasNewEpisodes } }

    override suspend fun get(providerId: Long, seriesId: Long): TrackedSeriesEntity? =
        rows.value[providerId to seriesId]

    override suspend fun getDueForCheck(providerId: Long, checkedBefore: Long): List<TrackedSeriesEntity> =
        rows.value.values.filter { it.providerId == providerId && it.lastCheckedAt <= checkedBefore }

    override suspend fun upsert(entity: TrackedSeriesEntity) {
        rows.value = rows.value + ((entity.providerId to entity.seriesId) to entity)
    }

    override suspend fun delete(providerId: Long, seriesId: Long) {
        rows.value = rows.value - (providerId to seriesId)
    }

    override suspend fun clearNewEpisodes(providerId: Long, seriesId: Long) {
        val row = rows.value[providerId to seriesId] ?: return
        upsert(row.copy(hasNewEpisodes = false, newEpisodeCount = 0))
    }

    override suspend fun deleteByProvider(providerId: Long) {
        rows.value = rows.value.filterKeys { it.first != providerId }
    }
}
