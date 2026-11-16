package com.streamvault.feature.catalog.presentation.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.CatalogGenre
import com.streamvault.domain.model.CatalogGenreCount
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.CatalogInsightsRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GenresUiState(
    val isSeries: Boolean = false,
    val genres: List<CatalogGenreCount> = emptyList(),
    val selectedGenre: CatalogGenre? = null,
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val isLoading: Boolean = true
)

/** Movies and series sorted into fixed genres (see CatalogGenre), each list best rated first. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GenresViewModel @Inject constructor(
    providerRepository: ProviderRepository,
    private val insights: CatalogInsightsRepository
) : ViewModel() {
    private val showSeries = MutableStateFlow(false)
    private val chosenGenre = MutableStateFlow<CatalogGenre?>(null)

    private val providerId = providerRepository.getActiveProvider()
        .map { it?.id }
        .distinctUntilChanged()

    val uiState: StateFlow<GenresUiState> = combine(providerId, showSeries, chosenGenre) { id, series, genre ->
        Triple(id, series, genre)
    }.flatMapLatest { (id, series, chosen) ->
        if (id == null) return@flatMapLatest flowOf(GenresUiState(isSeries = series, isLoading = false))
        insights.observeGenreCounts(id, series).flatMapLatest { counts ->
            val selected = chosen?.takeIf { genre -> counts.any { it.genre == genre } } ?: counts.firstOrNull()?.genre
            when {
                selected == null -> flowOf(GenresUiState(isSeries = series, genres = counts, isLoading = false))
                series -> insights.observeSeriesInGenre(id, selected, ITEM_LIMIT).map { items ->
                    GenresUiState(isSeries = true, genres = counts, selectedGenre = selected, series = items, isLoading = false)
                }
                else -> insights.observeMoviesInGenre(id, selected, ITEM_LIMIT).map { items ->
                    GenresUiState(isSeries = false, genres = counts, selectedGenre = selected, movies = items, isLoading = false)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenresUiState())

    fun showSeries(series: Boolean) {
        showSeries.value = series
    }

    fun selectGenre(genre: CatalogGenre) {
        chosenGenre.value = genre
    }

    private companion object {
        const val ITEM_LIMIT = 300
    }
}
