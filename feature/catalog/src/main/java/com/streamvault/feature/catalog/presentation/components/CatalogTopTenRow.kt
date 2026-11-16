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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import com.streamvault.core.ui.components.dialogs.PremiumDialog
import com.streamvault.core.ui.components.dialogs.PremiumDialogActionButton
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
 * rank. The header button opens a list of countries to pick from. The row stays visible when the
 * chosen country has none of the provider's titles, so another country can still be picked.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopTenRow(
    entries: List<TopTenEntry>,
    countryCode: String?,
    countries: List<String?>,
    onSelectCountry: (String?) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty() && countryCode == null) return
    val worldwide = stringResource(R.string.dashboard_top_ten_worldwide)
    val countryLabel = topTenCountryName(countryCode, worldwide)
    var showCountries by remember { mutableStateOf(false) }
    val countryButtonFocus = remember { FocusRequester() }

    if (showCountries) {
        TopTenCountryDialog(
            countries = countries,
            selected = countryCode,
            worldwide = worldwide,
            onSelect = { country ->
                showCountries = false
                if (country != countryCode) onSelectCountry(country)
            },
            onDismiss = { showCountries = false }
        )
    }

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
            TvButton(onClick = { showCountries = true }, modifier = Modifier.focusRequester(countryButtonFocus)) {
                Text(stringResource(R.string.dashboard_top_ten_change_country))
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_top_ten_none),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            return@Column
        }

        LazyRow(
            // The button sits at the far right of the header, so Up from a poster would otherwise
            // skip it and land on the row above; send Up to the button instead.
            modifier = Modifier
                .focusRestorer()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown) {
                        countryButtonFocus.requestFocus()
                        true
                    } else {
                        false
                    }
                },
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

private fun topTenCountryName(countryCode: String?, worldwide: String): String =
    countryCode
        ?.let { Locale("", it).displayCountry.takeIf { name -> name.isNotBlank() } ?: it }
        ?: worldwide

@Composable
private fun TopTenCountryDialog(
    countries: List<String?>,
    selected: String?,
    worldwide: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedFocus = remember { FocusRequester() }
    PremiumDialog(
        title = stringResource(R.string.dashboard_top_ten_change_country),
        onDismissRequest = onDismiss,
        initialBodyFocusRequester = selectedFocus,
        content = {
            countries.forEach { country ->
                val name = topTenCountryName(country, worldwide)
                val isSelected = country == selected
                PremiumDialogActionButton(
                    label = if (isSelected) "✓  $name" else name,
                    emphasized = isSelected,
                    onClick = { onSelect(country) },
                    modifier = if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier
                )
            }
        }
    )
}
