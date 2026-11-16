package com.streamvault.feature.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.design.browse.TextPrimary
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.TopTenEntry
import com.streamvault.feature.catalog.R
import java.util.Locale

/**
 * "Top 10 Today": today's most popular titles that the provider has, each card marked with its
 * rank. The header button cycles the country the list is for.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopTenRow(
    entries: List<TopTenEntry>,
    countryCode: String?,
    onCycleCountry: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val countryLabel = countryCode
        ?.let { Locale("", it).displayCountry.takeIf { name -> name.isNotBlank() } ?: it }
        ?: stringResource(R.string.dashboard_top_ten_worldwide)

    Column(modifier = modifier.fillMaxWidth().suppressParentVerticalScroll()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_top_ten_title, countryLabel),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            TvButton(onClick = onCycleCountry) {
                Text(stringResource(R.string.dashboard_top_ten_change_country))
            }
        }

        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { entry -> "${entry.rank}:${entry.movie?.id}:${entry.series?.id}" }) { entry ->
                Box {
                    entry.movie?.let { movie -> MovieCard(movie = movie, onClick = { onMovieClick(movie) }) }
                    entry.series?.let { series -> SeriesCard(series = series, onClick = { onSeriesClick(series) }) }
                    Text(
                        text = entry.rank.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
