package com.streamvault.feature.catalog.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamvault.core.ui.components.dialogs.PremiumDialog
import com.streamvault.core.ui.components.dialogs.PremiumDialogActionButton
import com.streamvault.core.ui.components.dialogs.PremiumDialogFooterButton
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.design.browse.FocusBorder
import com.streamvault.core.ui.design.browse.OnSurface
import com.streamvault.core.ui.design.browse.Primary
import com.streamvault.core.ui.design.browse.SurfaceElevated
import com.streamvault.core.ui.design.browse.SurfaceHighlight
import com.streamvault.domain.model.CatalogGenre
import com.streamvault.domain.model.CatalogShelfFilter
import com.streamvault.domain.model.CatalogShelfFilterOptions
import com.streamvault.domain.model.CatalogYearFilter
import com.streamvault.feature.catalog.R
import com.streamvault.feature.catalog.presentation.genres.labelRes
import java.util.Locale

private enum class FilterPicker { GENRE, RATING, COUNTRY, YEAR }

private val RatingSteps = listOf(0, 5, 6, 7, 8)

/**
 * Filter chips for a Home shelf: Genre, Rating, Country and Year. Each change is applied (and
 * saved) right away; genres and countries toggle on and off, several at a time.
 */
@Composable
fun ShelfFilterBar(
    filter: CatalogShelfFilter,
    options: CatalogShelfFilterOptions,
    onFilterChange: (CatalogShelfFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var picker by remember { mutableStateOf<FilterPicker?>(null) }
    val any = stringResource(R.string.shelf_filter_any)

    LazyRow(
        modifier = modifier,
        // Room for the focused chip's outline.
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "genre") {
            val genreNames = filter.genres.map { stringResource(it.labelRes()) }
            FilterChip(
                label = stringResource(R.string.shelf_filter_genre),
                value = summary(genreNames, any),
                active = filter.genres.isNotEmpty(),
                onClick = { picker = FilterPicker.GENRE }
            )
        }
        item(key = "rating") {
            FilterChip(
                label = stringResource(R.string.shelf_filter_rating),
                value = if (filter.minRating > 0) stringResource(R.string.shelf_filter_rating_value, filter.minRating) else any,
                active = filter.minRating > 0,
                onClick = { picker = FilterPicker.RATING }
            )
        }
        item(key = "country") {
            FilterChip(
                label = stringResource(R.string.shelf_filter_country),
                value = summary(filter.countries.map(::countryName), any),
                active = filter.countries.isNotEmpty(),
                onClick = { picker = FilterPicker.COUNTRY }
            )
        }
        item(key = "year") {
            FilterChip(
                label = stringResource(R.string.shelf_filter_year),
                value = if (filter.year == CatalogYearFilter.ANY) any else yearLabel(filter.year),
                active = filter.year != CatalogYearFilter.ANY,
                onClick = { picker = FilterPicker.YEAR }
            )
        }
        if (filter.isActive) {
            item(key = "clear") {
                FilterChip(
                    label = null,
                    value = stringResource(R.string.shelf_filter_clear),
                    active = false,
                    icon = true,
                    onClick = { onFilterChange(CatalogShelfFilter()) }
                )
            }
        }
    }

    when (picker) {
        FilterPicker.GENRE -> ToggleListDialog(
            title = stringResource(R.string.shelf_filter_genre),
            emptyNote = null,
            choices = (options.genres + filter.genres).distinct().map { it to stringResource(it.labelRes()) },
            selected = filter.genres,
            onToggle = { genre ->
                onFilterChange(filter.copy(genres = if (genre in filter.genres) filter.genres - genre else filter.genres + genre))
            },
            onClear = { onFilterChange(filter.copy(genres = emptySet<CatalogGenre>())) },
            onDismiss = { picker = null }
        )
        FilterPicker.COUNTRY -> ToggleListDialog(
            title = stringResource(R.string.shelf_filter_country),
            emptyNote = stringResource(R.string.shelf_filter_countries_pending),
            choices = (options.countries + filter.countries).distinct().map { it to countryName(it) },
            selected = filter.countries,
            onToggle = { code ->
                onFilterChange(filter.copy(countries = if (code in filter.countries) filter.countries - code else filter.countries + code))
            },
            onClear = { onFilterChange(filter.copy(countries = emptySet())) },
            onDismiss = { picker = null }
        )
        FilterPicker.RATING -> SingleChoiceDialog(
            title = stringResource(R.string.shelf_filter_rating),
            choices = RatingSteps.map { step ->
                step to if (step == 0) any else stringResource(R.string.shelf_filter_rating_value, step)
            },
            selected = filter.minRating,
            onPick = { step ->
                onFilterChange(filter.copy(minRating = step))
                picker = null
            },
            onDismiss = { picker = null }
        )
        FilterPicker.YEAR -> SingleChoiceDialog(
            title = stringResource(R.string.shelf_filter_year),
            choices = CatalogYearFilter.entries.map { year -> year to if (year == CatalogYearFilter.ANY) any else yearLabel(year) },
            selected = filter.year,
            onPick = { year ->
                onFilterChange(filter.copy(year = year))
                picker = null
            },
            onDismiss = { picker = null }
        )
        null -> Unit
    }
}

@Composable
private fun FilterChip(
    label: String?,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: Boolean = false
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Primary.copy(alpha = 0.18f) else SurfaceElevated,
            focusedContainerColor = SurfaceHighlight,
            contentColor = if (active) Primary else OnSurface,
            focusedContentColor = OnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(BrowseShapes.Control),
        border = ClickableSurfaceDefaults.border(
            border = if (active) Border(BorderStroke(1.dp, Primary.copy(alpha = 0.5f)), shape = BrowseShapes.Control) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, FocusBorder), shape = BrowseShapes.Control)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val text = if (label == null) value else "$label: $value"
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Icon(
                imageVector = if (icon) Icons.Default.Close else Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun <T> ToggleListDialog(
    title: String,
    emptyNote: String?,
    choices: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    PremiumDialog(
        title = title,
        subtitle = if (choices.isEmpty()) emptyNote else null,
        onDismissRequest = onDismiss,
        initialBodyFocusRequester = if (choices.isNotEmpty()) firstFocus else null,
        content = {
            choices.forEachIndexed { index, (value, name) ->
                val isOn = value in selected
                PremiumDialogActionButton(
                    label = if (isOn) "✓  $name" else name,
                    emphasized = isOn,
                    onClick = { onToggle(value) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                )
            }
        },
        footer = {
            if (selected.isNotEmpty()) {
                PremiumDialogFooterButton(label = stringResource(R.string.shelf_filter_clear), onClick = onClear)
            }
            PremiumDialogFooterButton(label = stringResource(R.string.shelf_filter_done), emphasized = true, onClick = onDismiss)
        }
    )
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedFocus = remember { FocusRequester() }
    PremiumDialog(
        title = title,
        onDismissRequest = onDismiss,
        initialBodyFocusRequester = selectedFocus,
        content = {
            choices.forEach { (value, name) ->
                val isOn = value == selected
                PremiumDialogActionButton(
                    label = if (isOn) "✓  $name" else name,
                    emphasized = isOn,
                    onClick = { onPick(value) },
                    modifier = if (isOn) Modifier.focusRequester(selectedFocus) else Modifier
                )
            }
        }
    )
}

@Composable
private fun summary(names: List<String>, any: String): String = when (names.size) {
    0 -> any
    1 -> names.single()
    else -> stringResource(R.string.shelf_filter_more, names.first(), names.size - 1)
}

@Composable
private fun yearLabel(year: CatalogYearFilter): String = when (year) {
    CatalogYearFilter.ANY -> stringResource(R.string.shelf_filter_any)
    CatalogYearFilter.THIS_YEAR -> stringResource(R.string.shelf_filter_year_this)
    CatalogYearFilter.LAST_3_YEARS -> stringResource(R.string.shelf_filter_year_last_3)
    CatalogYearFilter.LAST_10_YEARS -> stringResource(R.string.shelf_filter_year_last_10)
    CatalogYearFilter.Y2010S -> "2010s"
    CatalogYearFilter.Y2000S -> "2000s"
    CatalogYearFilter.Y1990S -> "1990s"
    CatalogYearFilter.OLDER -> stringResource(R.string.shelf_filter_year_older)
}

private fun countryName(code: String): String =
    Locale("", code).displayCountry.takeIf { it.isNotBlank() } ?: code
