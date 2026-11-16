package com.streamvault.feature.catalog.presentation.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.navigation.AppDestination
import com.streamvault.core.ui.design.AppColors
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.CatalogGenre
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.feature.catalog.R
import com.streamvault.feature.catalog.api.CatalogNavigationChrome
import com.streamvault.feature.catalog.api.CatalogScaffoldContent
import com.streamvault.feature.catalog.presentation.components.MovieCard
import com.streamvault.feature.catalog.presentation.components.SeriesCard

/**
 * Movies and series by genre (Action, Comedy, Anime…) instead of the provider's categories,
 * each genre listed best rated first.
 */
@Composable
fun GenresScreen(
    scaffold: CatalogScaffoldContent,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: GenresViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    scaffold(
        AppDestination.Genres,
        stringResource(R.string.genres_title),
        stringResource(R.string.genres_subtitle),
        CatalogNavigationChrome.TopBar,
        true,
        true,
        true
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LazyColumn(
                modifier = Modifier.width(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item(key = "type") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        GenreChip(
                            label = stringResource(R.string.genres_movies),
                            selected = !state.isSeries,
                            onClick = { viewModel.showSeries(false) }
                        )
                        GenreChip(
                            label = stringResource(R.string.genres_series),
                            selected = state.isSeries,
                            onClick = { viewModel.showSeries(true) }
                        )
                    }
                }
                items(state.genres, key = { it.genre.key }) { count ->
                    GenreChip(
                        label = stringResource(R.string.genres_count_label, stringResource(count.genre.labelRes()), count.count),
                        selected = count.genre == state.selectedGenre,
                        onClick = { viewModel.selectGenre(count.genre) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when {
                !state.isLoading && state.genres.isEmpty() -> Text(
                    text = stringResource(R.string.genres_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                state.isSeries -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.series, key = { it.id }) { series ->
                        SeriesCard(series = series, onClick = { onSeriesClick(series) })
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.movies, key = { it.id }) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvClickableSurface(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.Start) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) AppColors.Brand else AppColors.TextPrimary
            )
        }
    }
}

internal fun CatalogGenre.labelRes(): Int = when (this) {
    CatalogGenre.ACTION -> R.string.genre_action
    CatalogGenre.ADVENTURE -> R.string.genre_adventure
    CatalogGenre.ANIMATION -> R.string.genre_animation
    CatalogGenre.ANIME -> R.string.genre_anime
    CatalogGenre.COMEDY -> R.string.genre_comedy
    CatalogGenre.CRIME -> R.string.genre_crime
    CatalogGenre.DOCUMENTARY -> R.string.genre_documentary
    CatalogGenre.DRAMA -> R.string.genre_drama
    CatalogGenre.FAMILY -> R.string.genre_family
    CatalogGenre.FANTASY -> R.string.genre_fantasy
    CatalogGenre.HISTORY -> R.string.genre_history
    CatalogGenre.HORROR -> R.string.genre_horror
    CatalogGenre.KIDS -> R.string.genre_kids
    CatalogGenre.MUSIC -> R.string.genre_music
    CatalogGenre.MYSTERY -> R.string.genre_mystery
    CatalogGenre.REALITY -> R.string.genre_reality
    CatalogGenre.ROMANCE -> R.string.genre_romance
    CatalogGenre.SCI_FI -> R.string.genre_sci_fi
    CatalogGenre.SPORT -> R.string.genre_sport
    CatalogGenre.TALK -> R.string.genre_talk
    CatalogGenre.THRILLER -> R.string.genre_thriller
    CatalogGenre.WAR -> R.string.genre_war
    CatalogGenre.WESTERN -> R.string.genre_western
    CatalogGenre.CHRISTMAS -> R.string.genre_christmas
}
