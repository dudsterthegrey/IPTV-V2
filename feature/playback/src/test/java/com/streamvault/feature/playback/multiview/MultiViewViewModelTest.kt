package com.streamvault.feature.playback.multiview

import com.google.common.truth.Truth.assertThat
import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import com.streamvault.domain.manager.ParentalControlManager
import com.streamvault.domain.model.Channel
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.FavoriteRepository
import com.streamvault.domain.repository.MovieRepository
import com.streamvault.domain.repository.SeriesRepository
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.StreamInfo
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import com.streamvault.domain.repository.PlaybackHistoryRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.settings.PlayerPreferences
import com.streamvault.domain.usecase.UnlockParentalCategory
import com.streamvault.player.PlayerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class MultiViewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = mock()
    private val activityManager: ActivityManager = mock()
    private val manager = MultiViewManager()
    private val playerEngine: PlayerEngine = mock()
    private val playerEngineProvider: Provider<PlayerEngine> = mock()
    private val preferences: PlayerPreferences = mock()
    private val channels: ChannelRepository = mock()
    private val favorites: FavoriteRepository = mock()
    private val history: PlaybackHistoryRepository = mock()
    private val providers: ProviderRepository = mock()
    private val parentalControl: ParentalControlManager = mock()
    private val unlockParentalCategory: UnlockParentalCategory = mock()
    private val movies: MovieRepository = mock()
    private val series: SeriesRepository = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
        whenever(activityManager.memoryClass).thenReturn(128)
        whenever(activityManager.isLowRamDevice).thenReturn(true)
        whenever(playerEngineProvider.get()).thenReturn(playerEngine)
        whenever(preferences.playerAudioVideoSyncEnabled).thenReturn(flowOf(false))
        whenever(preferences.playerAudioVideoOffsetMs).thenReturn(flowOf(0))
        whenever(preferences.getMultiViewPreset(any())).thenReturn(flowOf(emptyList()))
        whenever(preferences.multiViewPerformanceMode).thenReturn(flowOf(null))
        whenever(preferences.multiViewCenterTwoSlotLayout).thenReturn(flowOf(false))
        whenever(preferences.multiViewRespectProviderConnectionLimit).thenReturn(flowOf(false))
        whenever(preferences.parentalControlLevel).thenReturn(flowOf(0))
        whenever(preferences.lastActiveProviderId).thenReturn(flowOf(null))
        whenever(providers.getActiveProvider()).thenReturn(flowOf(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initSlots_blocksExtraOccupiedSlotsForTheSelectedPerformancePolicy() {
        whenever(preferences.multiViewPerformanceMode).thenReturn(flowOf(MultiViewPerformanceMode.CONSERVATIVE.name))
        manager.setSlots(listOf(channel(1), channel(2), null, null))
        val viewModel = createViewModel()
        dispatcher.scheduler.runCurrent()

        viewModel.initSlots()

        assertThat(viewModel.uiState.value.slots[0].performanceBlockedReason).isNull()
        assertThat(viewModel.uiState.value.slots[1].performanceBlockedReason).contains("policy")
        assertThat(viewModel.uiState.value.slots.filter(MultiViewSlot::isEmpty).map(MultiViewSlot::index))
            .containsExactly(2, 3).inOrder()
        clear(viewModel)
    }

    @Test
    fun clearSlot_cancelsStaggeredGenerationBeforeAnAuxiliaryEngineIsCreated() {
        whenever(preferences.multiViewPerformanceMode).thenReturn(flowOf(MultiViewPerformanceMode.MAXIMUM.name))
        manager.setSlots(listOf(channel(1), channel(2), null, null))
        val viewModel = createViewModel()
        dispatcher.scheduler.runCurrent()

        viewModel.initSlots()
        dispatcher.scheduler.runCurrent()
        viewModel.clearSlot(1)
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()

        verify(playerEngineProvider, times(1)).get()
        assertThat(manager.slots.value).containsExactly(channel(1), null, null, null).inOrder()
        clear(viewModel)
    }

    @Test
    fun setFocus_makesTheFocusedEngineAudibleAndTheOtherEngineSilent() {
        val viewModel = createViewModel()
        dispatcher.scheduler.runCurrent()
        val firstEngine: PlayerEngine = mock()
        val secondEngine: PlayerEngine = mock()
        attachEngine(viewModel, 0, firstEngine)
        attachEngine(viewModel, 1, secondEngine)

        viewModel.setFocus(1)

        verify(secondEngine).setVolume(1f)
        verify(firstEngine).setVolume(0f)
        clear(viewModel)
    }

    @Test
    fun pinnedAudio_overridesFocusAndClearingPinReturnsAudioToFocusedSlot() {
        val viewModel = createViewModel()
        dispatcher.scheduler.runCurrent()
        val firstEngine: PlayerEngine = mock()
        val secondEngine: PlayerEngine = mock()
        attachEngine(viewModel, 0, firstEngine)
        attachEngine(viewModel, 1, secondEngine)

        viewModel.pinAudioToFocusedSlot()
        clearInvocations(firstEngine, secondEngine)
        viewModel.setFocus(1)

        verify(firstEngine).setVolume(1f)
        verify(secondEngine).setVolume(0f)

        clearInvocations(firstEngine, secondEngine)
        viewModel.clearPinnedAudio()

        verify(firstEngine).setVolume(0f)
        verify(secondEngine).setVolume(1f)
        clear(viewModel)
    }

    private fun createViewModel() = MultiViewViewModel(
        context = context,
        multiViewManager = manager,
        playerEngineProvider = playerEngineProvider,
        preferencesRepository = preferences,
        channelRepository = channels,
        favoriteRepository = favorites,
        playbackHistoryRepository = history,
        providerRepository = providers,
        parentalControlManager = parentalControl,
        unlockParentalCategory = unlockParentalCategory,
        movieRepository = movies,
        seriesRepository = series,
    )

    @Test
    fun movieTile_resolvesThroughTheMovieLibraryAndResumesWhereThePlayerWas() {
        whenever(preferences.multiViewPerformanceMode).thenReturn(flowOf(MultiViewPerformanceMode.MAXIMUM.name))
        whenever(preferences.playerFastRetryOnTransientFailures).thenReturn(flowOf(false))
        whenever(preferences.playerPlaybackBufferMode).thenReturn(flowOf(com.streamvault.domain.model.PlaybackBufferMode.AUTO))
        whenever(playerEngine.error).thenReturn(flowOf(null))
        val movie = Movie(id = 42, name = "Heat")
        wheneverBlocking { movies.getMovie(42) }.thenReturn(movie)
        wheneverBlocking { movies.getStreamInfo(movie) }.thenReturn(Result.success(StreamInfo(url = "https://example.test/heat.mp4")))
        val tile = multiViewVodChannel(MultiViewVodKind.MOVIE, contentId = 42, title = "Heat")
        manager.setSlots(listOf(tile, null, null, null))
        manager.setStartPosition(tile.id, 90_000L)
        val viewModel = createViewModel()
        dispatcher.scheduler.runCurrent()

        viewModel.initSlots()
        dispatcher.scheduler.runCurrent()

        verify(playerEngine).prepare(StreamInfo(url = "https://example.test/heat.mp4"))
        verify(playerEngine).seekTo(90_000L)
        verifyBlocking(channels, never()) { getStreamInfo(any(), any()) }
        clear(viewModel)
    }

    @Test
    fun vodTiles_neverCollideWithChannelsAndAreRecognisedByKind() {
        val movie = multiViewVodChannel(MultiViewVodKind.MOVIE, contentId = 7, title = "M")
        val episode = multiViewVodChannel(MultiViewVodKind.EPISODE, contentId = 7, title = "E")

        assertThat(movie.id).isLessThan(0L)
        assertThat(episode.id).isLessThan(0L)
        assertThat(movie.id).isNotEqualTo(episode.id)
        assertThat(movie.multiViewVodKind).isEqualTo(MultiViewVodKind.MOVIE)
        assertThat(episode.multiViewVodKind).isEqualTo(MultiViewVodKind.EPISODE)
        assertThat(episode.streamId).isEqualTo(7L)
        assertThat(channel(7).multiViewVodKind).isNull()
    }

    @Test
    fun startPositions_areForgottenWhenTheTileLeavesMultiView() {
        val tile = multiViewVodChannel(MultiViewVodKind.EPISODE, contentId = 3, title = "E")
        manager.setChannel(0, tile)
        manager.setStartPosition(tile.id, 5_000L)

        manager.clearSlot(0)

        assertThat(manager.startPositionFor(tile.id)).isEqualTo(0L)
    }

    private fun channel(id: Long) = Channel(
        id = id,
        name = "Channel $id",
        streamUrl = "https://example.test/$id.m3u8"
    )

    private fun attachEngine(viewModel: MultiViewViewModel, slotIndex: Int, engine: PlayerEngine) {
        val field = MultiViewViewModel::class.java.getDeclaredField("playerEngines")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val engines = field.get(viewModel) as MutableMap<Int, PlayerEngine>
        engines[slotIndex] = engine
    }

    private fun clear(viewModel: ViewModel) {
        val clearMethod = ViewModel::class.java.declaredMethods.first { method ->
            method.parameterCount == 0 && method.name.startsWith("clear")
        }
        clearMethod.isAccessible = true
        clearMethod.invoke(viewModel)
    }
}
