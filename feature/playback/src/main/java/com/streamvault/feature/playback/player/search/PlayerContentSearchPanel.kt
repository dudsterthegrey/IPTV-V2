package com.streamvault.feature.playback.player.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.navigation.PlayerNavigationRequest
import com.streamvault.core.ui.components.SearchInput
import com.streamvault.core.ui.design.AppColors
import com.streamvault.core.ui.device.rememberIsTelevisionDevice
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.feature.playback.R
import com.streamvault.feature.playback.player.overlay.PlayerOverlayPanel

private enum class SearchSection { LIVE, MOVIES, SERIES }

/**
 * Search the catalog without leaving playback. The video keeps running to the left; picking a
 * channel, movie or episode hands a player request to [onPlay], which switches playback in place.
 */
@Composable
internal fun PlayerContentSearchPanel(
    onPlay: (PlayerNavigationRequest) -> Unit,
    onDismiss: () -> Unit,
    onInteracted: () -> Unit = {},
    viewModel: PlayerContentSearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val seriesDetail by viewModel.seriesDetail.collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    var collapsed by rememberSaveable { mutableStateOf(emptySet<String>()) }

    BackHandler {
        if (seriesDetail != null) viewModel.closeSeries() else onDismiss()
    }
    LaunchedEffect(Unit) {
        runCatching { searchFocusRequester.requestFocus() }
    }
    LaunchedEffect(seriesDetail?.series?.id, seriesDetail?.isLoading) {
        if (seriesDetail?.isLoading == false) runCatching { episodesFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTelevisionDevice = rememberIsTelevisionDevice()
            val panelModifier = when {
                maxWidth < 700.dp -> Modifier.fillMaxWidth(0.92f)
                !isTelevisionDevice && maxWidth < 1280.dp -> Modifier.fillMaxWidth(0.55f)
                else -> Modifier.width(600.dp)
            }
            Box(
                modifier = panelModifier
                    .fillMaxHeight()
                    .padding(20.dp)
                    .align(Alignment.CenterEnd)
            ) {
                PlayerOverlayPanel(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "title") {
                            Text(
                                text = stringResource(R.string.player_search_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = AppColors.Brand,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        val detail = seriesDetail
                        if (detail == null) {
                            item(key = "input") {
                                SearchInput(
                                    value = query,
                                    onValueChange = {
                                        onInteracted()
                                        viewModel.updateQuery(it)
                                    },
                                    placeholder = stringResource(R.string.player_search_placeholder),
                                    focusRequester = searchFocusRequester,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                )
                            }
                            searchResultItems(
                                results = results,
                                collapsed = collapsed,
                                onToggleSection = { section ->
                                    onInteracted()
                                    collapsed = if (section.name in collapsed) collapsed - section.name else collapsed + section.name
                                },
                                onPlay = onPlay,
                                onOpenSeries = {
                                    onInteracted()
                                    viewModel.openSeries(it)
                                }
                            )
                        } else {
                            seriesEpisodeItems(
                                detail = detail,
                                firstItemFocusRequester = episodesFocusRequester,
                                onBack = viewModel::closeSeries,
                                onPlay = onPlay
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.searchResultItems(
    results: PlayerContentSearchResults,
    collapsed: Set<String>,
    onToggleSection: (SearchSection) -> Unit,
    onPlay: (PlayerNavigationRequest) -> Unit,
    onOpenSeries: (com.streamvault.domain.model.Series) -> Unit
) {
    when {
        results.queryTooShort -> item(key = "hint") { PanelMessage(stringResource(R.string.player_search_hint_min_chars)) }
        results.isLoading -> item(key = "loading") { PanelMessage(stringResource(R.string.player_search_loading)) }
        results.hasSearched && results.isEmpty -> item(key = "empty") { PanelMessage(stringResource(R.string.player_search_no_results)) }
    }
    if (results.channels.isNotEmpty()) {
        val open = SearchSection.LIVE.name !in collapsed
        item(key = "section_live") {
            SectionHeader(stringResource(R.string.player_search_section_live, results.channels.size), open) {
                onToggleSection(SearchSection.LIVE)
            }
        }
        if (open) {
            items(results.channels, key = { "live:${it.providerId}:${it.id}" }) { channel ->
                ResultRow(title = channel.name, subtitle = channel.categoryName) { onPlay(channel.toSearchPlayerRequest()) }
            }
        }
    }
    if (results.movies.isNotEmpty()) {
        val open = SearchSection.MOVIES.name !in collapsed
        item(key = "section_movies") {
            SectionHeader(stringResource(R.string.player_search_section_movies, results.movies.size), open) {
                onToggleSection(SearchSection.MOVIES)
            }
        }
        if (open) {
            items(results.movies, key = { "movie:${it.providerId}:${it.id}" }) { movie ->
                ResultRow(title = movie.name, subtitle = movie.year ?: movie.categoryName) { onPlay(movie.toSearchPlayerRequest()) }
            }
        }
    }
    if (results.series.isNotEmpty()) {
        val open = SearchSection.SERIES.name !in collapsed
        item(key = "section_series") {
            SectionHeader(stringResource(R.string.player_search_section_series, results.series.size), open) {
                onToggleSection(SearchSection.SERIES)
            }
        }
        if (open) {
            items(results.series, key = { "series:${it.providerId}:${it.id}" }) { series ->
                ResultRow(title = series.name, subtitle = series.categoryName, showsMore = true) { onOpenSeries(series) }
            }
        }
    }
}

private fun LazyListScope.seriesEpisodeItems(
    detail: SeriesEpisodesState,
    firstItemFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onPlay: (PlayerNavigationRequest) -> Unit
) {
    item(key = "series_back") {
        TvClickableSurface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppColors.SurfaceEmphasis,
                focusedContainerColor = AppColors.Brand
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (detail.isLoading || detail.failed) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.player_search_back_to_results),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
    item(key = "series_name") {
        Text(
            text = detail.series.name,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
    when {
        detail.isLoading -> item(key = "episodes_loading") { PanelMessage(stringResource(R.string.player_search_episodes_loading)) }
        detail.failed || detail.seasons.isEmpty() ->
            item(key = "episodes_failed") { PanelMessage(stringResource(R.string.player_search_episodes_failed)) }
    }
    var firstEpisode = true
    detail.seasons.forEach { season ->
        item(key = "season:${season.seasonNumber}") {
            Text(
                text = stringResource(R.string.player_search_season, season.seasonNumber),
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
        season.episodes.sortedBy { it.episodeNumber }.forEach { episode ->
            val focusThis = firstEpisode
            firstEpisode = false
            item(key = "episode:${episode.id}") {
                ResultRow(
                    title = stringResource(R.string.player_search_episode_row, episode.episodeNumber, episode.title),
                    subtitle = null,
                    modifier = if (focusThis) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                ) { onPlay(episode.toSearchPlayerRequest(detail.series)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, expanded: Boolean, onToggle: () -> Unit) {
    TvClickableSurface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceEmphasis,
            focusedContainerColor = AppColors.Brand
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    showsMore: Boolean = false,
    onClick: () -> Unit
) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showsMore) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextTertiary,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
    )
}
