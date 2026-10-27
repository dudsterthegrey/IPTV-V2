package com.streamvault.feature.playback.player.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Season
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SeriesRepository
import com.streamvault.domain.usecase.SearchContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the in-player search panel: catalog search while the current video keeps playing, plus
 * the episode list of a series picked from the results.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PlayerContentSearchViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val searchContent: SearchContent,
    private val seriesRepository: SeriesRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _seriesDetail = MutableStateFlow<SeriesEpisodesState?>(null)
    val seriesDetail: StateFlow<SeriesEpisodesState?> = _seriesDetail.asStateFlow()

    private var seriesJob: Job? = null

    val results: StateFlow<PlayerContentSearchResults> = combine(
        providerRepository.getActiveProvider().map { it?.id }.distinctUntilChanged(),
        _query.debounce(QUERY_DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged()
    ) { providerId, query -> providerId to query }
        .flatMapLatest { (providerId, query) ->
            if (providerId == null || query.length < MIN_QUERY_LENGTH) {
                flowOf(PlayerContentSearchResults(queryTooShort = query.length < MIN_QUERY_LENGTH))
            } else {
                searchContent(
                    providerId = providerId,
                    query = query,
                    maxResultsPerSection = MAX_RESULTS_PER_SECTION
                ).map { found ->
                    // Locked and adult items are never surfaced over playback.
                    PlayerContentSearchResults(
                        channels = found.channels.filterNot { it.isAdult || it.isUserProtected },
                        movies = found.movies.filterNot { it.isAdult || it.isUserProtected },
                        series = found.series.filterNot { it.isAdult || it.isUserProtected },
                        hasSearched = true
                    )
                }.onStart { emit(PlayerContentSearchResults(isLoading = true)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerContentSearchResults())

    fun updateQuery(query: String) {
        _query.value = query
    }

    fun openSeries(series: Series) {
        seriesJob?.cancel()
        _seriesDetail.value = SeriesEpisodesState(series = series, isLoading = true)
        seriesJob = viewModelScope.launch {
            val providerId = series.providerId.takeIf { it > 0L }
                ?: providerRepository.getActiveProvider().first()?.id
                ?: return@launch
            val loaded = seriesRepository.getSeriesDetails(providerId, series.id)
            _seriesDetail.update { current ->
                if (current?.series?.id != series.id) return@update current
                when (loaded) {
                    is Result.Success -> current.copy(
                        series = loaded.data.copy(id = series.id, providerId = providerId),
                        seasons = loaded.data.seasons
                            .filter { it.episodes.isNotEmpty() }
                            .sortedBy { it.seasonNumber },
                        isLoading = false
                    )
                    else -> current.copy(isLoading = false, failed = true)
                }
            }
        }
    }

    fun closeSeries() {
        seriesJob?.cancel()
        _seriesDetail.value = null
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
        const val MAX_RESULTS_PER_SECTION = 40
    }
}

data class PlayerContentSearchResults(
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val queryTooShort: Boolean = true
) {
    val isEmpty: Boolean
        get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

data class SeriesEpisodesState(
    val series: Series,
    val seasons: List<Season> = emptyList(),
    val isLoading: Boolean = false,
    val failed: Boolean = false
)
