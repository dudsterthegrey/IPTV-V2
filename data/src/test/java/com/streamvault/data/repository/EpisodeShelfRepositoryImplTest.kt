package com.streamvault.data.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.StreamVaultDatabase
import com.streamvault.data.local.entity.EpisodeEntity
import com.streamvault.data.local.entity.PlaybackHistoryEntity
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.data.local.entity.SeriesEntity
import com.streamvault.data.local.entity.TrackedSeriesEntity
import com.streamvault.data.remote.xtream.parseXtreamAddedMillis
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.ProviderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EpisodeShelfRepositoryImplTest {

    private lateinit var db: StreamVaultDatabase
    private lateinit var repository: EpisodeShelfRepositoryImpl
    private var seriesLocalId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StreamVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.providerDao().insert(ProviderEntity(id = PROVIDER_ID, name = "Fixture", type = ProviderType.XTREAM_CODES))
        db.seriesDao().insertAll(listOf(SeriesEntity(providerId = PROVIDER_ID, seriesId = 77, name = "Show", posterUrl = "poster")))
        seriesLocalId = db.seriesDao().getBySeriesId(PROVIDER_ID, 77)!!.id
        repository = EpisodeShelfRepositoryImpl(db.episodeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `first load keeps undated episodes unknown and later new ones are dated`() = runTest {
        val dao = db.episodeDao()
        dao.replaceAll(seriesLocalId, PROVIDER_ID, listOf(episode(1, 1), episode(1, 2, addedAt = 5_000L)))

        val firstLoad = dao.getEntitiesBySeriesSync(seriesLocalId).associate { it.episodeNumber to it.addedAt }
        assertThat(firstLoad).containsExactly(1, 0L, 2, 5_000L)

        val before = System.currentTimeMillis()
        dao.replaceAll(seriesLocalId, PROVIDER_ID, listOf(episode(1, 1), episode(1, 2), episode(1, 3)))

        val reload = dao.getEntitiesBySeriesSync(seriesLocalId).associate { it.episodeNumber to it.addedAt }
        assertThat(reload[1]).isEqualTo(0L)
        // A reload without the provider's date keeps the one already stored.
        assertThat(reload[2]).isEqualTo(5_000L)
        assertThat(reload[3]).isAtLeast(before)
    }

    @Test
    fun `recently added lists only tracked shows, newest first, skipping undated episodes`() = runTest {
        db.episodeDao().replaceAll(
            seriesLocalId,
            PROVIDER_ID,
            listOf(episode(1, 1), episode(1, 2, addedAt = 1_000L), episode(1, 3, addedAt = 3_000L))
        )
        assertThat(repository.observeRecentlyAddedForTrackedSeries(PROVIDER_ID, 10).first()).isEmpty()

        db.trackedSeriesDao().upsert(TrackedSeriesEntity(PROVIDER_ID, seriesLocalId, "Show", null, 3, false, 0, 0, 0, 0))

        val items = repository.observeRecentlyAddedForTrackedSeries(PROVIDER_ID, 10).first()
        assertThat(items.map { it.episode.episodeNumber }).containsExactly(3, 2).inOrder()
        assertThat(items.first().seriesName).isEqualTo("Show")
        assertThat(items.first().episode.addedAt).isEqualTo(3_000L)
    }

    @Test
    fun `up next follows the last finished episode and skips shows still in progress`() = runTest {
        db.episodeDao().replaceAll(
            seriesLocalId,
            PROVIDER_ID,
            listOf(episode(1, 1), episode(1, 2), episode(2, 1))
        )
        val episodes = db.episodeDao().getEntitiesBySeriesSync(seriesLocalId)
        val history = db.playbackHistoryDao()
        history.insertOrUpdate(historyRow(episodes[1], status = "COMPLETED_AUTO", watchedAt = 100L))

        assertThat(repository.observeUpNext(PROVIDER_ID, 10).first().map { it.episode.seasonNumber to it.episode.episodeNumber })
            .containsExactly(2 to 1)

        // Starting the next episode moves the show to Continue Watching instead.
        history.insertOrUpdate(historyRow(episodes[2], status = "IN_PROGRESS", watchedAt = 200L))
        assertThat(repository.observeUpNext(PROVIDER_ID, 10).first()).isEmpty()

        // Finishing the last episode leaves nothing to watch next.
        history.insertOrUpdate(historyRow(episodes[2], status = "COMPLETED_AUTO", watchedAt = 300L))
        assertThat(repository.observeUpNext(PROVIDER_ID, 10).first()).isEmpty()
    }

    @Test
    fun `xtream added accepts seconds or milliseconds`() {
        assertThat(parseXtreamAddedMillis("1700000000")).isEqualTo(1_700_000_000_000L)
        assertThat(parseXtreamAddedMillis("1700000000000")).isEqualTo(1_700_000_000_000L)
        assertThat(parseXtreamAddedMillis(null)).isEqualTo(0L)
        assertThat(parseXtreamAddedMillis("soon")).isEqualTo(0L)
    }

    private fun episode(season: Int, number: Int, addedAt: Long = 0L) = EpisodeEntity(
        episodeId = season * 100L + number,
        title = "S${season}E$number",
        episodeNumber = number,
        seasonNumber = season,
        seriesId = seriesLocalId,
        providerId = PROVIDER_ID,
        addedAt = addedAt
    )

    private fun historyRow(episode: EpisodeEntity, status: String, watchedAt: Long) = PlaybackHistoryEntity(
        contentId = episode.id,
        contentType = ContentType.SERIES_EPISODE,
        providerId = PROVIDER_ID,
        title = episode.title,
        lastWatchedAt = watchedAt,
        watchedStatus = status,
        seriesId = seriesLocalId,
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber
    )

    private companion object {
        const val PROVIDER_ID = 1L
    }
}
